package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.CycleStatus;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.OverdueRules;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Powiadomienia wyliczane w locie dla cykli widocznych dla użytkownika (zastępują scheduler w wersji demo). */
@Service
@RequiredArgsConstructor
public class NotificationService {
    static final int DUE_SOON_DAYS = 30;
    static final int URGENT_DAYS = 3;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CycleWorkflowService workflow;
    private final EvaluationHistoryService history;
    private final UserRepository users;
    private final Clock clock;

    public enum Type { OVERDUE, DUE_SOON, CRITERIA_DUE, APPEAL_DECISION_DUE, MANDATORY_GRADE_DUE }

    public enum Severity { HIGH, MEDIUM, LOW }

    public record Notification(Type type, Severity severity, Long cycleId, LocalDate dueDate, String message) {
    }

    @Transactional(readOnly = true)
    public List<Notification> forUser(User user) {
        LocalDate today = LocalDate.now(clock);
        List<Notification> result = new ArrayList<>();
        List<EvaluationCycle> visible = workflow.visibleFor(user);
        Map<Long, String> names = UserNames.byId(users, visible.stream().map(EvaluationCycle::getEmployeeId).toList());
        for (EvaluationCycle c : visible) {
            String who = names.getOrDefault(c.getEmployeeId(), "#" + c.getEmployeeId());
            boolean awaiting = OverdueService.awaitingEvaluation(c);
            long overdue = OverdueRules.overdueDays(c.getDueDate(), today, awaiting);
            if (overdue > 0) {
                result.add(new Notification(Type.OVERDUE, Severity.HIGH, c.getId(), c.getDueDate(),
                        "Ocena " + who + " po terminie (" + DATE.format(c.getDueDate()) + ") o " + overdue
                                + " dni — eskalacja: " + OverdueRules.escalationLevel(overdue)));
            } else if (awaiting && !c.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS))) {
                result.add(new Notification(Type.DUE_SOON, severity(c.getDueDate(), today), c.getId(), c.getDueDate(),
                        "Zbliża się termin oceny " + who + ": " + DATE.format(c.getDueDate())));
            }
            if (c.getStatus() == CycleStatus.DRAFT_CRITERIA) {
                LocalDate deadline = workflow.criteriaDeadline(c);
                result.add(new Notification(Type.CRITERIA_DUE, severity(deadline, today), c.getId(), deadline,
                        "Ustalenie kryteriów i rozmowa rozpoczynająca z " + who + " do " + DATE.format(deadline) + " (§ 7)"));
            }
            if (c.getStatus() == CycleStatus.APPEALED && c.getAppealDecisionDeadline() != null) {
                result.add(new Notification(Type.APPEAL_DECISION_DUE, severity(c.getAppealDecisionDeadline(), today),
                        c.getId(), c.getAppealDecisionDeadline(), "Rozpatrzenie sprzeciwu " + who + " do "
                        + DATE.format(c.getAppealDecisionDeadline()) + " (art. 83 ust. 3)"));
            }
            EvaluationHistoryService.GradeEligibility grade = history.mandatoryGrade(c);
            if (grade.eligible()) {
                result.add(new Notification(Type.MANDATORY_GRADE_DUE, Severity.HIGH, c.getId(), grade.deadline(),
                        "Obligatoryjny kolejny stopień służbowy dla " + who + (grade.deadline() == null ? ""
                                : " do " + DATE.format(grade.deadline())) + " (art. 89 ust. 2)"));
            }
        }
        result.sort(Comparator.comparing(Notification::dueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    static Severity severity(LocalDate deadline, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, deadline);
        if (daysLeft <= URGENT_DAYS) return Severity.HIGH;
        return daysLeft <= DUE_SOON_DAYS ? Severity.MEDIUM : Severity.LOW;
    }
}
