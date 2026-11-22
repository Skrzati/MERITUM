package pl.gov.kprm.oceny.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import pl.gov.kprm.oceny.service.ReminderService;

/** Codzienne zbiorcze przypomnienia e-mail o terminach — włączane {@code MAIL_REMINDERS_ENABLED=true}. */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.mail.reminders-enabled", havingValue = "true")
@RequiredArgsConstructor
public class ReminderScheduler {
    private final ReminderService reminders;

    @Scheduled(cron = "${app.mail.reminders-cron:0 0 7 * * *}", zone = "Europe/Warsaw")
    public void sendReminders() {
        ReminderService.ReminderResult r = reminders.sendAll();
        log.info("Przypomnienia e-mail: odbiorcy {}, wysłane {}", r.recipients(), r.sent());
    }
}
