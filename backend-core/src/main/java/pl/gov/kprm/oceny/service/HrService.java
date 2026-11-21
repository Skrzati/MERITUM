package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.*;
import pl.gov.kprm.oceny.rules.AbsencePeriod;
import pl.gov.kprm.oceny.rules.DeadlineService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static pl.gov.kprm.oceny.service.Access.*;

@Service
@Transactional
@RequiredArgsConstructor
public class HrService {
    private static final Set<String> SORTABLE = Set.of("id", "fullName", "email", "role");
    private static final Set<Role> EVALUATOR_ROLES = EnumSet.of(Role.EVALUATOR, Role.DG);
    private static final Set<Role> IMPORTABLE_ROLES = EnumSet.of(Role.EVALUATOR, Role.EVALUATED, Role.HR);

    private final UserRepository users;
    private final EvaluationCycleRepository cycles;
    private final AbsenceRepository absences;
    private final EmployeeNoteRepository notes;
    private final SchemaVersionRepository schemas;
    private final AuditLogRepository auditLogs;
    private final EvaluationHistoryRepository history;
    private final WellbeingSurveyRepository wellbeing;
    private final DeadlineService deadlines;
    private final AuditService audit;

    public record ImportResult(int imported, List<String> skipped, List<String> errors) {
    }

    public record HandoverResult(Long employeeId, Long previousEvaluatorId, Long newEvaluatorId,
                                 List<Long> cycleIds, long notesTransferred) {
    }

    public record GdprExport(User user, List<EvaluationCycle> cycles, List<EmployeeNote> notes,
                             List<Absence> absences, List<EvaluationHistory> evaluationHistory,
                             List<WellbeingSurvey> wellbeingSurveys, List<AuditLog> auditLogs) {
    }

    @Transactional(readOnly = true)
    public List<User> employees(User hr, String query, String sortBy, Sort.Direction direction) {
        requireRole(hr, Role.HR);
        String property = SORTABLE.contains(sortBy) ? sortBy : "fullName";
        return users.search(hr.getOfficeId(), query == null ? "" : query.trim(), Sort.by(direction, property));
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
            cycle.setDueDate(recalculateDue(cycle, periods));
            audit.log(hr, cycle.getOfficeId(), "DUE_DATE_RECALCULATED", AuditService.CYCLE, cycle.getId(),
                    Map.of("absence", start + ".." + end, "from", previous, "to", cycle.getDueDate()));
        }
        return active;
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

    /** CSV: full_name,email,role,is_civil_servant[,supervisor_email] — separator , lub ; nagłówek opcjonalny. */
    public ImportResult importCsv(User hr, InputStream csv) throws IOException {
        requireRole(hr, Role.HR);
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
                    User user = parseRow(hr, cols);
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
        audit.log(hr, hr.getOfficeId(), "EMPLOYEES_IMPORTED", AuditService.USER, null,
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
                wellbeing.findByUserIdOrderBySubmittedAtAsc(employee.getId()),
                auditLogs.findByActorIdOrEntityTypeAndEntityIdInOrderByCreatedAtAsc(employee.getId(),
                        AuditService.CYCLE, cycleIds));
        audit.log(hr, hr.getOfficeId(), "GDPR_EXPORT", AuditService.USER, employee.getId(), Map.of());
        return export;
    }

    private User parseRow(User hr, String[] cols) {
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
        user.setOfficeId(hr.getOfficeId());
        user.setFullName(name);
        int space = name.indexOf(' ');
        user.setFirstName(space > 0 ? name.substring(0, space) : name);
        user.setLastName(space > 0 ? name.substring(space + 1) : null);
        user.setEmail(email);
        user.setRole(role);
        user.setCivilServant(Boolean.parseBoolean(cols[3].trim()) || "tak".equalsIgnoreCase(cols[3].trim()));
        if (cols.length > 4 && !cols[4].isBlank()) {
            User supervisor = users.findByEmailIgnoreCase(cols[4].trim())
                    .filter(s -> s.getOfficeId().equals(hr.getOfficeId()))
                    .orElseThrow(() -> new IllegalArgumentException("nieznany przełożony " + cols[4].trim()));
            user.setSupervisorId(supervisor.getId());
        }
        return user;
    }

    private LocalDate recalculateDue(EvaluationCycle cycle, List<AbsencePeriod> periods) {
        if (cycle.isReevaluation()) {
            return deadlines.reevaluationDue(cycle.getStartDate());
        }
        int months = schemas.findById(cycle.getSchemaVersionId())
                .orElseThrow(() -> notFound("Schemat", cycle.getSchemaVersionId())).getPeriodMonths();
        return deadlines.periodicEvaluationDue(cycle.getStartDate(), months, periods);
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
