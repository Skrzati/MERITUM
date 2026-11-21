package pl.gov.kprm.oceny.rules;

/**
 * Organ rozpatrujący sprzeciw (art. 83 ust. 1): dyrektor generalny urzędu albo — w urzędach bez tego stanowiska —
 * kierownik urzędu (uwaga KPRM do wzoru arkusza). W systemie rolę pełni użytkownik z rolą DG.
 */
public enum AppealAuthority {
    DIRECTOR_GENERAL("Dyrektor Generalny", "dyrektora generalnego urzędu", ""),
    HEAD_OF_OFFICE("Kierownik urzędu", "kierownika urzędu",
            " (art. 25 ust. 9 ustawy o służbie cywilnej — zadania dyrektora generalnego wykonuje kierownik urzędu)");

    private final String label;
    private final String genitive;
    private final String legalBasis;

    AppealAuthority(String label, String genitive, String legalBasis) {
        this.label = label;
        this.genitive = genitive;
        this.legalBasis = legalBasis;
    }

    /** Podstawa prawna dopisywana w pouczeniu, gdy organem jest kierownik urzędu. */
    public String legalBasis() {
        return legalBasis;
    }

    public static AppealAuthority of(boolean officeHasDgRole) {
        return officeHasDgRole ? DIRECTOR_GENERAL : HEAD_OF_OFFICE;
    }

    public String label() {
        return label;
    }

    /** Forma do pouczenia: „sprzeciw do …”. */
    public String genitive() {
        return genitive;
    }
}
