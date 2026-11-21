package pl.gov.kprm.oceny.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import pl.gov.kprm.oceny.service.WorkingNotesRetentionService;

/** Nocne czyszczenie danych pomocniczych zamkniętych cykli — włączane {@code RETENTION_PURGE_ENABLED=true}. */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.retention.purge-enabled", havingValue = "true")
@RequiredArgsConstructor
public class RetentionScheduler {
    private final WorkingNotesRetentionService retention;

    @Scheduled(cron = "${app.retention.purge-cron:0 30 2 * * *}", zone = "Europe/Warsaw")
    public void purgeWorkingNotes() {
        WorkingNotesRetentionService.PurgeResult r = retention.purgeAll();
        log.info("Czyszczenie danych pomocniczych: cykle {}, notatki {}, ankiety {}, pominięte (legal hold) {}",
                r.cyclesPurged(), r.notesDeleted(), r.surveysDeleted(), r.skippedLegalHold());
    }
}
