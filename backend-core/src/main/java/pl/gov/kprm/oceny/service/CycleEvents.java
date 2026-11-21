package pl.gov.kprm.oceny.service;

import java.time.LocalDate;

/** Zdarzenia domenowe cyklu — obsługiwane po zatwierdzeniu transakcji (wysyłka e-mail). */
public final class CycleEvents {
    private CycleEvents() {
    }

    /** Cykl zamknięty (zapoznanie, fikcja zapoznania, zmiana oceny) — kontrola alertów kadrowych. */
    public record CycleClosed(Long cycleId) {
    }

    /** {@code previousDue = null} — termin kolejnego (ponownego) cyklu, a nie zmiana terminu bieżącego. */
    public record DueDateChanged(Long cycleId, LocalDate previousDue, LocalDate newDue, Reason reason) {
        public enum Reason {
            /** Art. 81 ust. 7 — nieobecność ciągła > 1 mies. przedłuża termin oceny. */
            ABSENCE_EXTENSION,
            /** Art. 83 ust. 4 — ponowne sporządzenie oceny po uwzględnieniu sprzeciwu. */
            APPEAL_REDO,
            /** Zmieniona po sprzeciwie ocena negatywna — ponowna ocena za 6 miesięcy (art. 81 ust. 9). */
            APPEAL_REEVALUATION
        }
    }
}
