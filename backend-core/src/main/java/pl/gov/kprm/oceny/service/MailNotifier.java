package pl.gov.kprm.oceny.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.EvaluationCycleRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.EmploymentAlertRules;
import pl.gov.kprm.oceny.service.CycleEvents.CycleClosed;
import pl.gov.kprm.oceny.service.CycleEvents.DueDateChanged;
import pl.gov.kprm.oceny.service.HrAlertService.HrAlert;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Powiadomienia e-mail (SMTP; w demo Mailpit) wysyłane po zatwierdzeniu transakcji: przedłużenie terminu
 * (art. 81 ust. 7), nowy termin po sprzeciwie i krytyczne alerty kadrowe (art. 71 ust. 1 pkt 1). Błąd SMTP nie
 * przerywa procesu — jest logowany; audyt zawiera tylko liczbę odbiorców (adresy to dane osobowe).
 */
@Slf4j
@Component
public class MailNotifier {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final String SUBJECT_PREFIX = "[MERITUM] ";

    private final ObjectProvider<JavaMailSender> sender;
    private final EvaluationCycleRepository cycles;
    private final UserRepository users;
    private final HrAlertService alerts;
    private final AuditService audit;
    private final boolean enabled;
    private final String from;

    public MailNotifier(ObjectProvider<JavaMailSender> sender, EvaluationCycleRepository cycles, UserRepository users,
                        HrAlertService alerts, AuditService audit, @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:meritum@kprm-demo.gov.pl}") String from) {
        this.sender = sender;
        this.cycles = cycles;
        this.users = users;
        this.alerts = alerts;
        this.audit = audit;
        this.enabled = enabled;
        this.from = from;
    }

    public record Mail(List<String> to, String subject, String body) {
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDueDateChanged(DueDateChanged event) {
        if (!enabled) {
            return;
        }
        cycles.findById(event.cycleId()).ifPresent(cycle -> {
            User employee = users.findById(cycle.getEmployeeId()).orElse(null);
            User evaluator = users.findById(cycle.getEvaluatorId()).orElse(null);
            if (employee != null) {
                send(cycle.getOfficeId(), cycle.getId(), "DUE_DATE_" + event.reason().name(),
                        dueDateMail(event, cycle, employee, addresses(Stream.of(employee, evaluator))));
            }
        });
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCycleClosed(CycleClosed event) {
        if (!enabled) {
            return;
        }
        cycles.findById(event.cycleId()).flatMap(c -> users.findById(c.getEmployeeId()).map(e -> Map.entry(c, e)))
                .ifPresent(pair -> {
                    List<HrAlert> critical = alerts.forEmployee(pair.getValue()).stream()
                            .filter(a -> a.severity() == EmploymentAlertRules.Severity.CRITICAL).toList();
                    if (critical.isEmpty()) {
                        return;
                    }
                    Long officeId = pair.getKey().getOfficeId();
                    List<String> to = addresses(Stream.concat(
                            users.findByOfficeIdAndRoleAndActiveTrue(officeId, Role.HR).stream(),
                            users.findByOfficeIdAndRoleAndActiveTrue(officeId, Role.DG).stream()));
                    critical.forEach(a -> send(officeId, pair.getKey().getId(), "HR_CRITICAL_ALERT",
                            criticalAlertMail(a, to)));
                });
    }

    static Mail dueDateMail(DueDateChanged e, EvaluationCycle cycle, User employee, List<String> to) {
        String who = employee.getFullName();
        return switch (e.reason()) {
            case ABSENCE_EXTENSION -> new Mail(to, SUBJECT_PREFIX + "Przedłużenie terminu oceny okresowej — " + who,
                    "Termin sporządzenia oceny okresowej (cykl nr " + cycle.getId() + ") został przedłużony z "
                            + date(e.previousDue()) + " na " + date(e.newDue()) + " z powodu usprawiedliwionej "
                            + "nieobecności trwającej dłużej niż miesiąc (art. 81 ust. 7 ustawy o służbie cywilnej).");
            case APPEAL_REDO -> new Mail(to, SUBJECT_PREFIX + "Ponowne sporządzenie oceny po sprzeciwie — " + who,
                    "Sprzeciw został uwzględniony — ocenę sporządza się po raz drugi (art. 83 ust. 4). Nowy termin "
                            + "sporządzenia oceny (cykl nr " + cycle.getId() + "): " + date(e.newDue())
                            + ". Zaproszenie na rozmowę oceniającą (.ics) zostało zaktualizowane.");
            case APPEAL_REEVALUATION -> new Mail(to, SUBJECT_PREFIX + "Termin ponownej oceny po sprzeciwie — " + who,
                    "Ocena okresowa po rozpatrzeniu sprzeciwu jest negatywna. Ponowna ocena obejmuje okres "
                            + "6 miesięcy i powinna zostać sporządzona do " + date(e.newDue())
                            + " (art. 81 ust. 9 ustawy o służbie cywilnej).");
        };
    }

    static Mail criticalAlertMail(HrAlert a, List<String> to) {
        return new Mail(to, SUBJECT_PREFIX + "ALERT KRYTYCZNY — " + a.employeeName(),
                a.message() + ".\nPodstawa: " + a.legalBasis() + ". Data ostatniej oceny: " + date(a.since())
                        + ".\nSzczegóły w module Kadry systemu MERITUM.");
    }

    private void send(Long officeId, Long cycleId, String template, Mail mail) {
        JavaMailSender mailer = sender.getIfAvailable();
        if (mailer == null || mail.to().isEmpty()) {
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(mail.to().toArray(String[]::new));
        msg.setSubject(mail.subject());
        msg.setText(mail.body());
        try {
            mailer.send(msg);
            audit.logSystem(officeId, "EMAIL_SENT", AuditService.CYCLE, cycleId,
                    Map.of("template", template, "recipients", mail.to().size()));
        } catch (MailException e) {
            log.warn("Nie wysłano e-maila {} dla cyklu {}: {}", template, cycleId, e.getMessage());
        }
    }

    private static List<String> addresses(Stream<User> people) {
        return people.filter(Objects::nonNull).filter(User::isActive).map(User::getEmail)
                .filter(e -> e != null && e.contains("@")).distinct().toList();
    }

    private static String date(java.time.LocalDate d) {
        return d == null ? "—" : DATE.format(d);
    }
}
