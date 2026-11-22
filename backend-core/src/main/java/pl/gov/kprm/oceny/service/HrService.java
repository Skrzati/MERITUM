package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.*;
import pl.gov.kprm.oceny.rules.AbsencePeriod;
import pl.gov.kprm.oceny.rules.DeadlineService;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.PerformanceLevel;
import pl.gov.kprm.oceny.rules.RuleViolationException;
import pl.gov.kprm.oceny.rules.SchemaRules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

import static pl.gov.kprm.oceny.service.Access.*;

@Service
@Transactional
@RequiredArgsConstructor
public class HrService {
    private static final Set<String> SORTABLE = Set.of("id", "fullName", "email", "role");
    private static final Set<Role> EVALUATOR_ROLES = EnumSet.of(Role.EVALUATOR, Role.DG);
    private static final int MIN_GRADE = 1;
    private static final int MAX_GRADE = 9;
    private static final int MIN_APPOINTMENT_YEAR = 1990;
    private static final String ANONYMIZED_NAME = "Pracownik zanonimizowany";
    private static final String ANONYMIZED_EMAIL = "anonimizacja-%d@invalid.local";
    private static final Set<Role> IMPORTABLE_ROLES = EnumSet.of(Role.EVALUATOR, Role.EVALUATED, Role.HR);

    private final UserRepository users;
    private final EvaluationCycleRepository cycles;
    private final AbsenceRepository absences;
    private final EmployeeNoteRepository notes;
    private final SchemaVersionRepository schemas;
    private final AuditLogRepository auditLogs;
    private final EvaluationHistoryRepository history;
    private final DeadlineService deadlines;
    private final AuditService audit;
    private final RescheduleRequestRepository reschedules;
    private final EvaluationHistoryService historyService;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final EmployeePrivateNoteRepository privateNotes;

    public record ImportResult(int imported, List<String> skipped, List<String> errors) {
    }

    public record HandoverResult(Long employeeId, Long previousEvaluatorId, Long newEvaluatorId,
                                 List<Long> cycleIds, long notesTransferred) {
    }

    public record GdprExport(User user, List<EvaluationCycle> cycles, List<EmployeeNote> notes,
                             List<Absence> absences, List<EvaluationHistory> evaluationHistory,
                             List<AuditLog> auditLogs) {
    }

    @Transactional(readOnly = true)
    public List<User> employees(User hr, String query, String sortBy, Sort.Direction direction,
                                Integer page, Integer size) {
        requireRole(hr, Role.HR);
        String property = SORTABLE.contains(sortBy) ? sortBy : "fullName";
        Sort sort = Sort.by(direction, property);
        String q = query == null ? "" : query.trim();
        return Paging.of(page, size, sort)
                .map(p -> users.search(hr.getOfficeId(), q, p))
                .orElseGet(() -> users.search(hr.getOfficeId(), q, sort));
    }

    public List<EvaluationCycle> registerAbsence(User hr, Long userId, LocalDate start, LocalDate end, String reason) {
        requireRole(hr, Role.HR);
        User employee = employeeInOffice(hr, userId);
        if (end.isBefore(start)) {
            throw conflict("Data końca nieobecności jest wcześniejsza niż początek");
        }
        Absence absence = new Absence();
        absence.setUserId(employee.getId());
        absence.setStartDate(start);
        absence.setEndDate(end);
        absence.setReason(reason);
        absence.setCreatedBy(hr.getId());
        absences.save(absence);

        List<AbsencePeriod> periods = absences.findByUserIdOrderByStartDateAsc(employee.getId()).stream()
                .map(a -> new AbsencePeriod(a.getStartDate(), a.getEndDate())).toList();
        List<EvaluationCycle> active = activeCycles(employee.getId());
        for (EvaluationCycle cycle : active) {
            LocalDate previous = cycle.getDueDate();
            LocalDate recalculated = recalculateDue(cycle, periods);
            Optional<LocalDate> rescheduled = appliedReschedule(cycle.getId());
            // ręczna zmiana terminu (art. 81 ust. 8) nie może zostać cofnięta przeliczeniem nieobecności
            boolean keepRescheduled = rescheduled.isPresent() && recalculated.isBefore(rescheduled.get());
            cycle.setDueDate(keepRescheduled ? rescheduled.get() : recalculated);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("absence", start + ".." + end);
            p.put("from", previous);
            p.put("to", cycle.getDueDate());
            p.put("recalculated", recalculated);
            rescheduled.ifPresent(r -> p.put("rescheduledDueDate", r));
            p.put("keptRescheduledDueDate", keepRescheduled);
            audit.log(hr, cycle.getOfficeId(), "DUE_DATE_RECALCULATED", AuditService.CYCLE, cycle.getId(), p);
            if (cycle.getDueDate().isAfter(previous)) {
                events.publishEvent(new CycleEvents.DueDateChanged(cycle.getId(), previous, cycle.getDueDate(),
                        CycleEvents.DueDateChanged.Reason.ABSENCE_EXTENSION));
            }
        }
        return active;
    }

    private Optional<LocalDate> appliedReschedule(Long cycleId) {
        return reschedules.findByCycleIdAndStatus(cycleId, RescheduleStatus.APPLIED).stream()
                .map(RescheduleRequest::getNewDueDate).filter(Objects::nonNull).max(LocalDate::compareTo);
    }

    /** Pola edytowalne przez kadry (null = bez zmian). */
    public record EmployeeUpdate(String firstName, String lastName, String department, String positionTitle, Role role,
                                 Boolean civilServant, EmploymentStatus employmentStatus, Integer civilServiceGrade,
                                 Integer appointmentYear,
                                 LocalDate lastGradeDate, Long supervisorId, String jobDescription,
                                 LocalDate employmentStartDate, Set<Role> secondaryRoles) {
        public EmployeeUpdate(String firstName, String lastName, String department, String positionTitle, Role role,
                              Boolean civilServant, EmploymentStatus employmentStatus, Integer civilServiceGrade,
                              Integer appointmentYear, LocalDate lastGradeDate, Long supervisorId) {
            this(firstName, lastName, department, positionTitle, role, civilServant, employmentStatus,
                    civilServiceGrade, appointmentYear, lastGradeDate, supervisorId, null, null, null);
        }
    }

    public User updateEmployee(User hr, Long employeeId, EmployeeUpdate u) {
        requireRole(hr, Role.HR);
        User e = activeEmployee(hr, employeeId);
        List<String> changed = new ArrayList<>();
        if (u.role() != null && u.role() != e.getRole()) {
            if (u.role() == Role.KPRM_ADMIN) {
                throw forbidden("Kadry nie nadają roli administratora KPRM");
            }
            if (e.getId().equals(hr.getId())) {
                throw conflict("Nie można zmienić własnej roli");
            }
            e.setRole(u.role());
            changed.add("role");
        }
        if (u.civilServiceGrade() != null && (u.civilServiceGrade() < MIN_GRADE || u.civilServiceGrade() > MAX_GRADE)) {
            throw new RuleViolationException(List.of("Stopień służbowy: od " + MIN_GRADE + " do " + MAX_GRADE));
        }
        int year = LocalDate.now(clock).getYear();
        if (u.appointmentYear() != null && (u.appointmentYear() < MIN_APPOINTMENT_YEAR || u.appointmentYear() > year)) {
            throw new RuleViolationException(List.of("Rok mianowania: od " + MIN_APPOINTMENT_YEAR + " do " + year));
        }
        set(changed, "firstName", e.getFirstName(), trim(u.firstName()), e::setFirstName);
        set(changed, "lastName", e.getLastName(), trim(u.lastName()), e::setLastName);
        set(changed, "department", e.getDepartment(), trim(u.department()), e::setDepartment);
        set(changed, "positionTitle", e.getPositionTitle(), trim(u.positionTitle()), e::setPositionTitle);
        if (u.employmentStatus() != null) {
            set(changed, "employmentStatus", e.getEmploymentStatus(), u.employmentStatus(), e::setEmploymentStatus);
        } else {
            set(changed, "civilServant", e.isCivilServant(), u.civilServant(), e::setCivilServant);
        }
        set(changed, "civilServiceGrade", e.getCivilServiceGrade(), u.civilServiceGrade(), e::setCivilServiceGrade);
        set(changed, "appointmentYear", e.getAppointmentYear(), u.appointmentYear(), e::setAppointmentYear);
        set(changed, "lastGradeDate", e.getLastGradeDate(), u.lastGradeDate(), e::setLastGradeDate);
        set(changed, "jobDescription", e.getJobDescription(), trim(u.jobDescription()), e::setJobDescription);
        if (u.employmentStartDate() != null && u.employmentStartDate().isAfter(LocalDate.now(clock))) {
            throw new RuleViolationException(List.of("Data nawiązania stosunku pracy nie może być z przyszłości"));
        }
        set(changed, "employmentStartDate", e.getEmploymentStartDate(), u.employmentStartDate(),
                e::setEmploymentStartDate);
        if (u.secondaryRoles() != null) {
            set(changed, "secondaryRoles", e.getSecondaryRoles(), secondaryRoles(u.secondaryRoles(), e.getRole()),
                    e::setSecondaryRoles);
        }
        if (changed.contains("firstName") || changed.contains("lastName")) {
            String full = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                    + (e.getLastName() == null ? "" : e.getLastName())).trim();
            if (!full.isEmpty()) {
                e.setFullName(full);
            }
        }
        if (u.supervisorId() != null && !u.supervisorId().equals(e.getSupervisorId())) {
            handover(hr, e.getId(), u.supervisorId()); // zmiana podległości = przekazanie aktywnych cykli (§ 9 ust. 3)
            changed.add("supervisorId");
        }
        if (!changed.isEmpty()) {
            // w audycie tylko nazwy pól — wartości to dane osobowe
            audit.log(hr, hr.getOfficeId(), "EMPLOYEE_UPDATED", AuditService.USER, e.getId(), Map.of("fields", changed));
        }
        return e;
    }

    public enum RemovalOutcome { DELETED, ANONYMIZED }

    /**
     * Usunięcie danych pracownika: bez śladów w procesie — usunięcie rekordu; z cyklami, historią lub audytem —
     * anonimizacja (klucze obce i dokumentacja oceny pozostają, dane osobowe i notatki robocze są usuwane).
     */
    public RemovalOutcome removeEmployee(User hr, Long employeeId) {
        requireRole(hr, Role.HR);
        User e = activeEmployee(hr, employeeId);
        if (e.getId().equals(hr.getId())) {
            throw conflict("Nie można usunąć własnego konta");
        }
        if (cycles.findByEmployeeIdOrEvaluatorIdOrderByIdAsc(e.getId(), e.getId()).stream()
                .anyMatch(c -> c.getStatus().isActive())) {
            throw conflict("Pracownik ma aktywny cykl oceny (jako oceniany lub oceniający) — najpierw go zamknij lub przekaż");
        }
        if (users.existsBySupervisorId(e.getId())) {
            throw conflict("Pracownik jest przełożonym — najpierw zmień podległość jego podwładnych");
        }
        absences.deleteAll(absences.findByUserIdOrderByStartDateAsc(e.getId()));
        privateNotes.deleteByOwnerId(e.getId()); // prywatny dziennik — dane osobowe, nie dokumentacja
        if (!users.isReferenced(e.getId())) {
            users.delete(e);
            audit.log(hr, hr.getOfficeId(), "EMPLOYEE_DELETED", AuditService.USER, employeeId, Map.of());
            return RemovalOutcome.DELETED;
        }
        List<Long> ownCycleIds = cycles.findByEmployeeIdOrderByIdAsc(e.getId()).stream().map(EvaluationCycle::getId).toList();
        if (!ownCycleIds.isEmpty()) {
            notes.deleteByCycleIdIn(ownCycleIds);
        }
        e.setFullName(ANONYMIZED_NAME + " #" + e.getId());
        e.setEmail(String.format(ANONYMIZED_EMAIL, e.getId()));
        e.setFirstName(null);
        e.setLastName(null);
        e.setDepartment(null);
        e.setPositionTitle(null);
        e.setAppointmentYear(null);
        e.setCivilServiceGrade(null);
        e.setLastGradeDate(null);
        e.setJobDescription(null);
        e.setSupervisorId(null);
        e.setActive(false);
        audit.log(hr, hr.getOfficeId(), "EMPLOYEE_ANONYMIZED", AuditService.USER, employeeId,
                Map.of("cyclesKept", ownCycleIds.size()));
        return RemovalOutcome.ANONYMIZED;
    }

    @Transactional(readOnly = true)
    public List<EvaluationHistory> history(User hr, Long employeeId) {
        requireRole(hr, Role.HR);
        return historyService.of(employeeInOffice(hr, employeeId).getId());
    }

    public EvaluationHistory addHistory(User hr, Long employeeId, EvaluationOutcome outcome, PerformanceLevel level,
                                        LocalDate evaluatedOn) {
        requireRole(hr, Role.HR);
        return historyService.addHistorical(hr, activeEmployee(hr, employeeId), outcome, level, evaluatedOn);
    }

    private User activeEmployee(User hr, Long id) {
        User e = employeeInOffice(hr, id);
        if (!e.isActive()) {
            throw conflict("Konto jest zanonimizowane");
        }
        return e;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static <T> void set(List<String> changed, String field, T current, T requested, Consumer<T> setter) {
        if (requested != null && !requested.equals(current)) {
            setter.accept(requested);
            changed.add(field);
        }
    }

    /** § 9 ust. 3: nowy przełożony przejmuje aktywny cykl wraz z notatkami; kryteria pozostają bez zmian. */
    public HandoverResult handover(User hr, Long employeeId, Long newEvaluatorId) {
        requireRole(hr, Role.HR);
        User employee = employeeInOffice(hr, employeeId);
        User newEvaluator = employeeInOffice(hr, newEvaluatorId);
        if (employee.getId().equals(newEvaluator.getId())) {
            throw conflict("Pracownik nie może być własnym oceniającym");
        }
        if (!EVALUATOR_ROLES.contains(newEvaluator.getRole())) {
            throw conflict("Nowy przełożony musi mieć rolę EVALUATOR lub DG");
        }
        Long previous = employee.getSupervisorId();
        employee.setSupervisorId(newEvaluator.getId());
        List<EvaluationCycle> active = activeCycles(employee.getId());
        active.forEach(c -> c.setEvaluatorId(newEvaluator.getId()));
        List<Long> cycleIds = active.stream().map(EvaluationCycle::getId).toList();
        long notesCount = cycleIds.isEmpty() ? 0 : notes.countByCycleIdIn(cycleIds);

        audit.log(hr, hr.getOfficeId(), "SUPERVISOR_HANDOVER", AuditService.USER, employee.getId(),
                Map.of("previousEvaluatorId", String.valueOf(previous), "newEvaluatorId", newEvaluator.getId(),
                        "cycleIds", cycleIds, "notesTransferred", notesCount));
        return new HandoverResult(employee.getId(), previous, newEvaluator.getId(), cycleIds, notesCount);
    }

    /** CSV: full_name,email,role,is_civil_servant|employment_status[,supervisor_email] — separator , lub ; nagłówek opcjonalny. */
    public ImportResult importCsv(User hr, InputStream csv) throws IOException {
        requireRole(hr, Role.HR);
        return importInto(hr, hr.getOfficeId(), csv);
    }

    /** Import do wskazanego urzędu — uprawnienia sprawdza wywołujący (kadry: własny urząd, admin KPRM: dowolny). */
    ImportResult importInto(User actor, Long officeId, InputStream csv) throws IOException {
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int imported = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank() || (lineNo == 1 && line.toLowerCase(Locale.ROOT).contains("email"))) {
                    continue;
                }
                String[] cols = line.split(line.contains(";") ? ";" : ",", -1);
                try {
                    User user = parseRow(officeId, cols);
                    if (users.existsByEmailIgnoreCase(user.getEmail())) {
                        skipped.add("Wiersz " + lineNo + ": " + user.getEmail() + " już istnieje");
                        continue;
                    }
                    users.saveAndFlush(user);
                    imported++;
                } catch (IllegalArgumentException e) {
                    errors.add("Wiersz " + lineNo + ": " + e.getMessage());
                }
            }
        }
        audit.log(actor, officeId, "EMPLOYEES_IMPORTED", AuditService.USER, null,
                Map.of("imported", imported, "skipped", skipped.size(), "errors", errors.size()));
        return new ImportResult(imported, skipped, errors);
    }

    /** Art. 15 RODO — wszystkie dane osobowe pracownika przetwarzane w systemie. */
    @Transactional
    public GdprExport gdprExport(User hr, Long employeeId) {
        requireRole(hr, Role.HR);
        User employee = employeeInOffice(hr, employeeId);
        List<EvaluationCycle> own = cycles.findByEmployeeIdOrderByIdAsc(employee.getId());
        List<Long> cycleIds = own.stream().map(EvaluationCycle::getId).toList();
        GdprExport export = new GdprExport(employee, own,
                notes.findByCycleIdInOrAuthorIdOrderByCreatedAtAsc(cycleIds, employee.getId()),
                absences.findByUserIdOrderByStartDateAsc(employee.getId()),
                history.findByUserIdOrderByEvaluatedOnAsc(employee.getId()),
                auditLogs.findByActorIdOrEntityTypeAndEntityIdInOrderByCreatedAtAsc(employee.getId(),
                        AuditService.CYCLE, cycleIds));
        audit.log(hr, hr.getOfficeId(), "GDPR_EXPORT", AuditService.USER, employee.getId(), Map.of());
        return export;
    }

    private User parseRow(Long officeId, String[] cols) {
        if (cols.length < 4) {
            throw new IllegalArgumentException("oczekiwano min. 4 kolumn");
        }
        String name = cols[0].trim();
        String email = cols[1].trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || !email.contains("@")) {
            throw new IllegalArgumentException("niepoprawne imię i nazwisko lub e-mail");
        }
        Role role;
        try {
            role = Role.valueOf(cols[2].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("nieznana rola " + cols[2].trim());
        }
        if (!IMPORTABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("rola " + role + " nie może być nadana importem");
        }
        User user = new User();
        user.setOfficeId(officeId);
        user.setFullName(name);
        int space = name.indexOf(' ');
        user.setFirstName(space > 0 ? name.substring(0, space) : name);
        user.setLastName(space > 0 ? name.substring(space + 1) : null);
        user.setEmail(email);
        user.setRole(role);
        user.setEmploymentStatus(employmentStatus(cols[3].trim()));
        if (cols.length > 4 && !cols[4].isBlank()) {
            User supervisor = users.findByEmailIgnoreCase(cols[4].trim())
                    .filter(s -> s.getOfficeId().equals(officeId))
                    .orElseThrow(() -> new IllegalArgumentException("nieznany przełożony " + cols[4].trim()));
            user.setSupervisorId(supervisor.getId());
        }
        return user;
    }

    /** Łączenie ról (mały urząd): bez KPRM_ADMIN, bez dublowania roli głównej. */
    static Set<Role> secondaryRoles(Set<Role> requested, Role primary) {
        if (requested.contains(Role.KPRM_ADMIN)) {
            throw forbidden("Roli administratora KPRM nie można nadać jako roli dodatkowej");
        }
        Set<Role> roles = requested.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(requested);
        roles.remove(primary);
        return roles;
    }

    /** Kolumna 4: nazwa statusu (np. PRACOWNIK_SC_OKRESLONY) albo dawna flaga true/tak = urzędnik SC. */
    static EmploymentStatus employmentStatus(String value) {
        if (Boolean.parseBoolean(value) || "tak".equalsIgnoreCase(value)) {
            return EmploymentStatus.URZEDNIK_SC;
        }
        if (value.isEmpty() || "false".equalsIgnoreCase(value) || "nie".equalsIgnoreCase(value)) {
            return EmploymentStatus.PRACOWNIK_SC_NIEOKRESLONY;
        }
        try {
            return EmploymentStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("nieznany status zatrudnienia " + value);
        }
    }

    private LocalDate recalculateDue(EvaluationCycle cycle, List<AbsencePeriod> periods) {
        SchemaVersion schema = schemas.findById(cycle.getSchemaVersionId())
                .orElseThrow(() -> notFound("Schemat", cycle.getSchemaVersionId()));
        if (cycle.isReevaluation()) {
            return deadlines.reevaluationDue(cycle.getStartDate(), SchemaRules.from(schema.getConfigJson()));
        }
        return deadlines.periodicEvaluationDue(cycle.getStartDate(), schema.getPeriodMonths(), periods);
    }

    private List<EvaluationCycle> activeCycles(Long employeeId) {
        return cycles.findByEmployeeIdOrderByIdAsc(employeeId).stream()
                .filter(c -> c.getStatus().isActive()).toList();
    }

    private User employeeInOffice(User hr, Long userId) {
        User user = users.findById(userId).orElseThrow(() -> notFound("Użytkownik", userId));
        requireSameOffice(hr, user.getOfficeId());
        return user;
    }
}
