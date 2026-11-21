package pl.gov.kprm.oceny.domain;

/** Przesłanki zmiany terminu oceny z art. 81 ust. 8 ustawy o służbie cywilnej. */
public enum RescheduleMode {
    /** pkt 1 — zmiana na stanowisku oceniającego w trakcie okresu oceny. */
    EVALUATOR_CHANGE,
    /** pkt 2 — przewidywana dłuższa usprawiedliwiona nieobecność ocenianego. */
    EMPLOYEE_ABSENCE,
    /** pkt 3 — przewidywana dłuższa usprawiedliwiona nieobecność oceniającego. */
    EVALUATOR_ABSENCE,
    /** pkt 4 — wniosek ocenianego (wymaga decyzji oceniającego). */
    EMPLOYEE_REQUEST;

    public int statutePoint() {
        return ordinal() + 1;
    }
}
