package pl.gov.kprm.oceny.rules;

/**
 * Organ rozpatrujący sprzeciw (art. 83 ust. 1): dyrektor generalny urzędu albo — w urzędach bez tego stanowiska —
 * kierownik urzędu (uwaga KPRM do wzoru arkusza). W systemie rolę pełni użytkownik z rolą DG.
 */
public enum AppealAuthority {
    DIRECTOR_GENERAL("Dyrektor Generalny", "dyrektora generalnego urzędu"),
    HEAD_OF_OFFICE("Kierownik urzędu", "kierownika urzędu");

    private final String label;
    private final String genitive;

    AppealAuthority(String label, String genitive) {
        this.label = label;
        this.genitive = genitive;
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
