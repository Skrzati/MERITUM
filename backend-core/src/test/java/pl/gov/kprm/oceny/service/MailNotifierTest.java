package pl.gov.kprm.oceny.service;

import org.junit.jupiter.api.Test;
import pl.gov.kprm.oceny.domain.EmploymentStatus;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.rules.EmploymentAlertRules;
import pl.gov.kprm.oceny.service.CycleEvents.DueDateChanged;
import pl.gov.kprm.oceny.service.CycleEvents.DueDateChanged.Reason;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailNotifierTest {
    @Test
    void mailsCiteLegalBasisAndDates() {
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(16L);
        User employee = new User();
        employee.setFullName("Michał Lewicki");
        List<String> to = List.of("a@x.pl");

        MailNotifier.Mail extension = MailNotifier.dueDateMail(new DueDateChanged(16L, LocalDate.of(2026, 11, 10),
                LocalDate.of(2027, 1, 11), Reason.ABSENCE_EXTENSION), cycle, employee, to);
        assertThat(extension.subject()).startsWith(MailNotifier.SUBJECT_PREFIX).contains("Michał Lewicki");
        assertThat(extension.body()).contains("10.11.2026", "11.01.2027", "art. 81 ust. 7");
        assertThat(MailNotifier.dueDateMail(new DueDateChanged(16L, null, LocalDate.of(2027, 5, 4),
                Reason.APPEAL_REEVALUATION), cycle, employee, to).body()).contains("art. 81 ust. 9", "04.05.2027");

        HrAlertService.HrAlert alert = new HrAlertService.HrAlert(21L, "Grzegorz Sikora", EmploymentStatus.URZEDNIK_SC,
                null, EmploymentAlertRules.Type.TWO_NEGATIVE_TERMINATION, EmploymentAlertRules.Severity.CRITICAL,
                "art. 71 ust. 1 pkt 1 ustawy o służbie cywilnej", "Dwie kolejne negatywne", LocalDate.of(2026, 3, 16));
        assertThat(MailNotifier.criticalAlertMail(alert, to).body()).contains("art. 71 ust. 1 pkt 1", "16.03.2026");
    }

    @Test
    void reminderDigestListsEveryNotificationWithCycleNumber() {
        MailNotifier.Mail digest = MailNotifier.reminderDigestMail(List.of(
                new NotificationService.Notification(NotificationService.Type.CRITERIA_DUE,
                        NotificationService.Severity.MEDIUM, 7L, LocalDate.of(2026, 12, 1), "Ustalenie kryteriów (§ 7)"),
                new NotificationService.Notification(NotificationService.Type.MANDATORY_GRADE_DUE,
                        NotificationService.Severity.HIGH, null, null, "Obligatoryjny stopień (art. 89 ust. 2)")),
                List.of("a@x.pl"));
        assertThat(digest.subject()).startsWith(MailNotifier.SUBJECT_PREFIX).endsWith("(2)");
        assertThat(digest.body()).contains("Ustalenie kryteriów (§ 7) (cykl nr 7)", "art. 89 ust. 2)\n");
    }

    @Test
    void localPasswordPolicyRequiresLengthLetterAndDigit() {
        LocalAccountService.requireStrong("Weterynaria2026");
        assertThatThrownBy(() -> LocalAccountService.requireStrong("krotkie1")).hasMessageContaining("min. 12");
        assertThatThrownBy(() -> LocalAccountService.requireStrong("bezcyfrbezcyfr")).hasMessageContaining("cyfra");
    }
}
