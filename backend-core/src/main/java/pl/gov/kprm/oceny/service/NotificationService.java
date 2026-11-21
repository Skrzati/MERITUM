package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.EmployeeNoteRepository;
import pl.gov.kprm.oceny.repository.FeedbackCheckinRepository;
import pl.gov.kprm.oceny.repository.OfficeRepository;
import pl.gov.kprm.oceny.repository.PreInterviewSurveyRepository;
import pl.gov.kprm.oceny.repository.RescheduleRequestRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.AppealAuthority;
import pl.gov.kprm.oceny.rules.CheckinRules;
import pl.gov.kprm.oceny.rules.EmploymentAlertRules;
import pl.gov.kprm.oceny.rules.OverdueRules;
import pl.gov.kprm.oceny.service.EvaluationHistoryService.CycleHistory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Powiadomienia wyliczane w locie dla cykli widocznych dla użytkownika (zastępują scheduler w wersji demo).
 * Wszystkie dane pomocnicze (osoby, historia, notatki, wnioski, urzędy) pobierane zbiorczo — bez zapytań per cykl.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    static final int DUE_SOON_DAYS = 30;
    static final int URGENT_DAYS = 3;
    /** Jak długo po zdarzeniu pokazywać powiadomienia informacyjne (rozstrzygnięcie sprzeciwu, wniosek o stopień). */
    static final int INFO_VISIBLE_DAYS = 30;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Set<CycleStatus> EVALUATED = EnumSet.of(CycleStatus.AWAITING_ACK, CycleStatus.COMPLETED,
            CycleStatus.APPEALED, CycleStatus.APPEALED_TO_COURT);

    private final CycleWorkflowService workflow;
    private final EvaluationHistoryService history;
    private final SubstitutionService substitutions;
    private final UserRepository users;
    private final EmployeeNoteRepository notes;
    private final RescheduleRequestRepository reschedules;
    private final OfficeRepository offices;
    private final FeedbackCheckinRepository checkins;
    private final HrAlertService hrAlerts;
    private final PreInterviewSurveyRepository surveys;
    private final Clock clock;

    public enum Type {
        OVERDUE, DUE_SOON, CRITERIA_DUE, APPEAL_DECISION_DUE, MANDATORY_GRADE_DUE,
        QUARTERLY_CHECKIN, GRADE_REQUEST_SUBMITTED, RESCHEDULE_PENDING, APPEAL_DECIDED,
        /** Kadry / kierownictwo: art. 71 ust. 1 pkt 1 (krytyczny) albo informacja o dwóch ocenach negatywnych. */
        HR_CRITICAL_ALERT, HR_INFO_ALERT,
        /** Oceniający: oceniany wysłał ankietę przygotowawczą przed rozmową. */
        PRE_INTERVIEW_SURVEY_SENT
    }

    public enum Severity { HIGH, MEDIUM, LOW }

    public record Notification(Type type, Severity severity, Long cycleId, LocalDate dueDate, String message) {
    }

    @Transactional(readOnly = true)
    public List<Notification> forUser(User user) {
        LocalDate today = LocalDate.now(clock);
        List<EvaluationCycle> visible = workflow.visibleFor(user);
        List<Notification> result = new ArrayList<>();
        if (user.getRole() == Role.HR || user.getRole() == Role.DG) {
            employmentAlerts(user, result);
        }
        if (visible.isEmpty()) {
            return result;
        }
        Context ctx = context(user, visible);
        for (EvaluationCycle c : visible) {
            String who = ctx.names.getOrDefault(c.getEmployeeId(), "#" + c.getEmployeeId());
            boolean asEvaluator = c.getEvaluatorId().equals(user.getId())
                    || (c.getStatus().isActive() && ctx.substituted.contains(c.getEvaluatorId()));
            deadlines(c, who, today, ctx, result);
            if (asEvaluator) {
                evaluatorReminders(c, who, today, ctx, result);
            }
            if (ctx.officeStaff) {
                gradeRequest(c, who, today, result);
            }
            if (asEvaluator || c.getEmployeeId().equals(user.getId())) {
                appealDecided(c, who, today, ctx, result);
            }
        }
        result.sort(Comparator.comparing(Notification::dueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /** Art. 89 ust. 2 ma osobne powiadomienie per cykl (MANDATORY_GRADE_DUE) — tu tylko skutki ocen negatywnych. */
    private void employmentAlerts(User user, List<Notification> out) {
        for (HrAlertService.HrAlert a : hrAlerts.forOffice(user.getOfficeId())) {
            if (a.type() == EmploymentAlertRules.Type.MANDATORY_GRADE) {
                continue;
            }
            boolean critical = a.severity() == EmploymentAlertRules.Severity.CRITICAL;
            out.add(new Notification(critical ? Type.HR_CRITICAL_ALERT : Type.HR_INFO_ALERT,
                    critical ? Severity.HIGH : Severity.LOW, a.cycleId(), a.since(),
                    a.employeeName() + ": " + a.message() + " (" + a.legalBasis() + ")"));
        }
    }

    private record Context(Map<Long, String> names, Map<Long, CycleHistory> histories, Map<Long, LocalDate> lastNote,
                           Map<Long, LocalDate> lastCheckin,
                           Set<Long> pendingReschedule, Map<Long, Boolean> officeHasDg, Set<Long> substituted,
                           boolean officeStaff, Map<Long, LocalDate> surveySent) {
    }

    private Context context(User user, List<EvaluationCycle> visible) {
        List<Long> ids = visible.stream().map(EvaluationCycle::getId).toList();
        Map<Long, LocalDate> lastNote = new HashMap<>();
        for (Object[] row : notes.lastNoteAt(ids)) {
            lastNote.put((Long) row[0], ((OffsetDateTime) row[1]).atZoneSameInstant(clock.getZone()).toLocalDate());
        }
        Map<Long, LocalDate> lastCheckin = new HashMap<>();
        checkins.lastCheckinOn(ids).forEach(r -> lastCheckin.put((Long) r[0], (LocalDate) r[1]));
        Set<Long> pending = reschedules.findByCycleIdInAndStatus(ids, RescheduleStatus.PENDING).stream()
                .map(RescheduleRequest::getCycleId).collect(Collectors.toSet());
        Map<Long, Boolean> hasDg = offices.findAllById(visible.stream().map(EvaluationCycle::getOfficeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Office::getId, Office::isHasDgRole));
        Map<Long, LocalDate> surveySent = new HashMap<>();
        surveys.findByCycleIdInAndStatus(ids, PreInterviewSurvey.Status.SENT).forEach(s -> surveySent.put(s.getCycleId(),
                s.getSentAt().atZoneSameInstant(clock.getZone()).toLocalDate()));
        return new Context(UserNames.byId(users, visible.stream().map(EvaluationCycle::getEmployeeId).toList()),
                history.forCycles(visible), lastNote, lastCheckin, pending, hasDg, substitutions.substitutedBy(user.getId()),
                user.getRole() == Role.HR || user.getRole() == Role.DG, surveySent);
    }

    private void deadlines(EvaluationCycle c, String who, LocalDate today, Context ctx, List<Notification> out) {
        boolean awaiting = OverdueService.awaitingEvaluation(c);
        long overdue = OverdueRules.overdueDays(c.getDueDate(), today, awaiting);
        if (overdue > 0) {
            out.add(new Notification(Type.OVERDUE, Severity.HIGH, c.getId(), c.getDueDate(),
                    "Ocena " + who + " po terminie (" + DATE.format(c.getDueDate()) + ") o " + overdue
                            + " dni — eskalacja: " + escalation(overdue, authority(c, ctx))));
        } else if (awaiting && !c.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS))) {
            out.add(new Notification(Type.DUE_SOON, severity(c.getDueDate(), today), c.getId(), c.getDueDate(),
                    "Zbliża się termin oceny " + who + ": " + DATE.format(c.getDueDate())));
        }
        if (c.getStatus() == CycleStatus.DRAFT_CRITERIA) {
            LocalDate deadline = workflow.criteriaDeadline(c);
            out.add(new Notification(Type.CRITERIA_DUE, severity(deadline, today), c.getId(), deadline,
                    "Ustalenie kryteriów i rozmowa rozpoczynająca z " + who + " do " + DATE.format(deadline) + " (§ 7)"));
        }
        if (c.getStatus() == CycleStatus.APPEALED && c.getAppealDecisionDeadline() != null) {
            out.add(new Notification(Type.APPEAL_DECISION_DUE, severity(c.getAppealDecisionDeadline(), today),
                    c.getId(), c.getAppealDecisionDeadline(), "Rozpatrzenie sprzeciwu " + who + " przez: "
                    + authority(c, ctx).label().toLowerCase(Locale.ROOT) + " do "
                    + DATE.format(c.getAppealDecisionDeadline()) + " (art. 83 ust. 3)"));
        }
        EvaluationHistoryService.GradeEligibility grade = ctx.histories.get(c.getId()).grade();
        if (grade.eligible()) {
            out.add(new Notification(Type.MANDATORY_GRADE_DUE, Severity.HIGH, c.getId(), grade.deadline(),
                    "Obligatoryjny kolejny stopień służbowy dla " + who + (grade.deadline() == null ? ""
                            : " do " + DATE.format(grade.deadline())) + " (art. 89 ust. 2)"));
        }
    }

    /** Przypomnienia dla oceniającego (i jego aktywnego zastępcy). */
    private void evaluatorReminders(EvaluationCycle c, String who, LocalDate today, Context ctx, List<Notification> out) {
        if (c.getStatus() == CycleStatus.OBSERVATION) {
            // odbyty check-in (bieżąca informacja zwrotna) resetuje licznik 90 dni
            LocalDate last = CheckinRules.lastActivity(c.getStartDate(), c.getCriteriaConfirmedOn(),
                    ctx.lastNote.get(c.getId()), ctx.lastCheckin.get(c.getId()));
            long days = CheckinRules.daysSince(last, today);
            CheckinRules.Urgency urgency = CheckinRules.urgency(days);
            if (urgency != CheckinRules.Urgency.NONE) {
                out.add(new Notification(Type.QUARTERLY_CHECKIN,
                        urgency == CheckinRules.Urgency.URGENT ? Severity.HIGH : Severity.MEDIUM, c.getId(),
                        last.plusDays(CheckinRules.REMINDER_DAYS),
                        "Kwartalne podsumowanie: od " + days + " dni brak notatki z obserwacji pracy " + who
                                + " — zapisz krótką notatkę lub przeprowadź rozmowę informacyjną"));
            }
        }
        LocalDate sent = ctx.surveySent.get(c.getId());
        if (sent != null && c.getStatus().isActive() && !sent.isBefore(today.minusDays(INFO_VISIBLE_DAYS))) {
            out.add(new Notification(Type.PRE_INTERVIEW_SURVEY_SENT, Severity.MEDIUM, c.getId(), sent,
                    who + " wysłał(a) ankietę przygotowawczą przed rozmową — odpowiedzi w zakładce „Rozmowy i IPRZ”"));
        }
        if (ctx.pendingReschedule.contains(c.getId())) {
            out.add(new Notification(Type.RESCHEDULE_PENDING, Severity.HIGH, c.getId(), null,
                    "Wniosek " + who + " o zmianę terminu oceny czeka na rozpatrzenie (art. 81 ust. 8 pkt 4)"));
        }
    }

    private void gradeRequest(EvaluationCycle c, String who, LocalDate today, List<Notification> out) {
        boolean requested = c.getGradeRequestJson() != null && c.getGradeRequestJson().path("requested").asBoolean(false);
        if (requested && EVALUATED.contains(c.getStatus()) && c.getEvaluatedOn() != null
                && !c.getEvaluatedOn().isBefore(today.minusDays(INFO_VISIBLE_DAYS))) {
            out.add(new Notification(Type.GRADE_REQUEST_SUBMITTED, Severity.MEDIUM, c.getId(), c.getEvaluatedOn(),
                    "Złożono wniosek o przyznanie kolejnego stopnia służbowego dla " + who + " (art. 89 ust. 1)"));
        }
    }

    private void appealDecided(EvaluationCycle c, String who, LocalDate today, Context ctx, List<Notification> out) {
        if (c.getDgDecidedAt() == null) {
            return;
        }
        LocalDate decided = c.getDgDecidedAt().atZoneSameInstant(clock.getZone()).toLocalDate();
        if (decided.isBefore(today.minusDays(INFO_VISIBLE_DAYS))) {
            return;
        }
        boolean redo = c.getDgDecision() == DgDecision.REDO;
        out.add(new Notification(Type.APPEAL_DECIDED, redo ? Severity.HIGH : Severity.MEDIUM, c.getId(), decided,
                authority(c, ctx).label() + " rozpatrzył(a) sprzeciw " + who + ": "
                        + (redo ? "ponowne sporządzenie oceny" : "zmiana oceny") + " (" + DATE.format(decided) + ")"));
    }

    private static AppealAuthority authority(EvaluationCycle c, Context ctx) {
        return AppealAuthority.of(ctx.officeHasDg.getOrDefault(c.getOfficeId(), true));
    }

    private static String escalation(long overdueDays, AppealAuthority authority) {
        EscalationLevel level = OverdueRules.escalationLevel(overdueDays);
        return level == EscalationLevel.DYREKTOR_GENERALNY ? authority.label().toUpperCase(Locale.ROOT) : level.name();
    }

    static Severity severity(LocalDate deadline, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, deadline);
        if (daysLeft <= URGENT_DAYS) return Severity.HIGH;
        return daysLeft <= DUE_SOON_DAYS ? Severity.MEDIUM : Severity.LOW;
    }
}
