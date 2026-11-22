package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.service.NotificationService.Notification;
import pl.gov.kprm.oceny.service.NotificationService.Type;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Zbiorcze przypomnienia e-mail o terminach (kryteria § 7, zbliżający się termin oceny, art. 89 ust. 2) — z tych samych
 * powiadomień wyliczanych w locie co dzwonek w UI, więc bez osobnej tabeli. Jedna wiadomość na osobę.
 */
@Service
@RequiredArgsConstructor
public class ReminderService {
    static final Set<Type> REMINDER_TYPES = EnumSet.of(Type.CRITERIA_DUE, Type.DUE_SOON, Type.MANDATORY_GRADE_DUE);

    private final NotificationService notifications;
    private final UserRepository users;
    private final MailNotifier mail;
    private final AuditService audit;

    /** {@code recipients} — osoby z co najmniej jednym przypomnieniem, {@code sent} — wysłane wiadomości. */
    public record ReminderResult(int recipients, int sent) {
    }

    /** Wysyłka na żądanie (HR, DG) — wyłącznie dla osób z własnego urzędu. */
    @Transactional
    public ReminderResult sendForOffice(User actor) {
        Access.requireRole(actor, Role.HR, Role.DG);
        ReminderResult result = send(users.findByOfficeIdAndActiveTrueOrderByIdAsc(actor.getOfficeId()));
        audit.log(actor, actor.getOfficeId(), "REMINDERS_SENT", AuditService.USER, actor.getId(),
                Map.of("recipients", result.recipients(), "sent", result.sent()));
        return result;
    }

    /** Harmonogram (ReminderScheduler) — wszystkie urzędy. */
    @Transactional
    public ReminderResult sendAll() {
        return send(users.findAllByActiveTrueOrderByIdAsc());
    }

    private ReminderResult send(List<User> people) {
        int recipients = 0;
        int sent = 0;
        for (User user : people) {
            // administrator KPRM widzi cykle wszystkich urzędów — terminy pilnują kadry i oceniający
            if (user.getRole() == Role.KPRM_ADMIN) {
                continue;
            }
            List<Notification> reminders = notifications.forUser(user).stream()
                    .filter(n -> REMINDER_TYPES.contains(n.type())).toList();
            if (reminders.isEmpty()) {
                continue;
            }
            recipients++;
            if (mail.sendReminderDigest(user, reminders)) {
                sent++;
            }
        }
        return new ReminderResult(recipients, sent);
    }
}
