package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.EmployeePrivateNote;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.EmployeePrivateNoteRepository;
import pl.gov.kprm.oceny.repository.EvaluationCycleRepository;
import pl.gov.kprm.oceny.rules.RuleViolationException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import static pl.gov.kprm.oceny.service.Access.*;

/**
 * Prywatny dziennik pracownika: wyłącznie właściciel (ownerId == bieżący użytkownik) — przełożony, kadry, DG i admin
 * dostają 403. Treść nie trafia do audytu, akt, eksportu EZD ani do eksportu RODO prowadzonego przez kadry.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PrivateNoteService {
    static final int MAX_LENGTH = 10_000;

    private final EmployeePrivateNoteRepository notes;
    private final EvaluationCycleRepository cycles;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<EmployeePrivateNote> list(User user, Long cycleId) {
        return cycleId == null ? notes.findByOwnerIdOrderByCreatedAtDescIdDesc(user.getId())
                : notes.findByOwnerIdAndCycleIdOrderByCreatedAtDescIdDesc(user.getId(), cycleId);
    }

    public EmployeePrivateNote create(User user, Long cycleId, String content) {
        EmployeePrivateNote note = new EmployeePrivateNote();
        note.setOwnerId(user.getId());
        note.setCycleId(ownCycle(user, cycleId));
        note.setContent(valid(content));
        note.setCreatedAt(OffsetDateTime.now(clock));
        return notes.save(note);
    }

    public EmployeePrivateNote update(User user, Long id, String content) {
        EmployeePrivateNote note = owned(user, id);
        note.setContent(valid(content));
        note.setUpdatedAt(OffsetDateTime.now(clock));
        return note;
    }

    public void delete(User user, Long id) {
        notes.delete(owned(user, id));
    }

    private EmployeePrivateNote owned(User user, Long id) {
        EmployeePrivateNote note = notes.findById(id).orElseThrow(() -> notFound("Notatka prywatna", id));
        if (!note.getOwnerId().equals(user.getId())) {
            throw forbidden("Prywatny dziennik jest dostępny wyłącznie dla jego właściciela");
        }
        return note;
    }

    /** Powiązanie z cyklem tylko dla własnego cyklu (jako oceniany). */
    private Long ownCycle(User user, Long cycleId) {
        if (cycleId == null) {
            return null;
        }
        EvaluationCycle cycle = cycles.findById(cycleId).orElseThrow(() -> notFound("Cykl", cycleId));
        if (!user.getId().equals(cycle.getEmployeeId())) {
            throw forbidden("Notatkę prywatną można powiązać wyłącznie z własnym cyklem oceny");
        }
        return cycleId;
    }

    private static String valid(String content) {
        String c = content == null ? "" : content.trim();
        if (c.isEmpty() || c.length() > MAX_LENGTH) {
            throw new RuleViolationException(List.of("Treść notatki: od 1 do " + MAX_LENGTH + " znaków"));
        }
        return c;
    }
}
