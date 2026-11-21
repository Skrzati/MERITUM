package pl.gov.kprm.oceny.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.*;
import pl.gov.kprm.oceny.rules.RetentionRules;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Czyszczenie danych pomocniczych po zamknięciu cyklu (RODO — minimalizacja, zakaz „czarnych teczek”): notatki
 * robocze przełożonego, ankiety przygotowawcze i przygotowanie ocenianego. Arkusz oceny, historia ocen i audyt nigdy
 * nie są usuwane (część B akt osobowych); cykle z legal hold (spór sądowy) i niezamknięte są pomijane.
 */
@Service
public class WorkingNotesRetentionService {
    private final EvaluationCycleRepository cycles;
    private final EmployeeNoteRepository notes;
    private final PreInterviewSurveyRepository surveys;
    private final EmployeeSelfPrepRepository selfPreps;
    private final AuditService audit;
    private final Clock clock;
    private final int graceDays;

    public WorkingNotesRetentionService(EvaluationCycleRepository cycles, EmployeeNoteRepository notes,
                                        PreInterviewSurveyRepository surveys, EmployeeSelfPrepRepository selfPreps,
                                        AuditService audit, Clock clock,
                                        @Value("${app.retention.working-notes-grace-days:30}") int graceDays) {
        this.cycles = cycles;
        this.notes = notes;
        this.surveys = surveys;
        this.selfPreps = selfPreps;
        this.audit = audit;
        this.clock = clock;
        this.graceDays = graceDays;
    }

    public record PurgeResult(int cyclesPurged, long notesDeleted, long surveysDeleted, int skippedLegalHold) {
    }

    /** Kadry uruchamiają czyszczenie dla własnego urzędu (poza harmonogramem). */
    @Transactional
    public PurgeResult purgeForOffice(User hr) {
        Access.requireRole(hr, Role.HR);
        return purge(cycles.findByOfficeIdOrderByIdAsc(hr.getOfficeId()), hr);
    }

    /** Harmonogram (RetentionScheduler) — wszystkie urzędy. */
    @Transactional
    public PurgeResult purgeAll() {
        return purge(cycles.findAllByOrderByIdAsc(), null);
    }

    private PurgeResult purge(List<EvaluationCycle> candidates, User actor) {
        LocalDate today = LocalDate.now(clock);
        int purged = 0;
        int held = 0;
        long notesDeleted = 0;
        long surveysDeleted = 0;
        for (EvaluationCycle c : candidates) {
            if (c.getStatus() != CycleStatus.COMPLETED || c.getWorkingNotesPurgedAt() != null) {
                continue;
            }
            if (c.isLegalHold()) {
                held++;
                continue;
            }
            if (!RetentionRules.workingNotesPurgeable(closedOn(c), graceDays, false, today)) {
                continue;
            }
            List<Long> id = List.of(c.getId());
            long n = notes.countByCycleIdIn(id);
            notes.deleteByCycleIdIn(id);
            long s = surveys.deleteByCycleIdIn(id);
            List<EmployeeSelfPrep> preps = selfPreps.findAllById(id);
            selfPreps.deleteAll(preps);
            c.setWorkingNotesPurgedAt(OffsetDateTime.now(clock));
            Map<String, Object> payload = Map.of("notesDeleted", n, "surveysDeleted", s + preps.size(),
                    "graceDays", graceDays, "archiveKept", "arkusz, historia ocen, audyt");
            if (actor != null) {
                audit.log(actor, c.getOfficeId(), "WORKING_NOTES_PURGED", AuditService.CYCLE, c.getId(), payload);
            } else {
                audit.logSystem(c.getOfficeId(), "WORKING_NOTES_PURGED", AuditService.CYCLE, c.getId(), payload);
            }
            purged++;
            notesDeleted += n;
            surveysDeleted += s + preps.size();
        }
        return new PurgeResult(purged, notesDeleted, surveysDeleted, held);
    }

    /** Zamknięcie = najpóźniejsze z: zapoznanie, termin sprzeciwu, rozstrzygnięcie sprzeciwu. */
    static LocalDate closedOn(EvaluationCycle c) {
        return Stream.of(c.getAcknowledgedAt(), c.getAppealDeadline(),
                        c.getDgDecidedAt() == null ? null : c.getDgDecidedAt().toLocalDate())
                .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
    }
}
