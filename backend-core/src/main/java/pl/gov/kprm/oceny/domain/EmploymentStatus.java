package pl.gov.kprm.oceny.domain;

/** Status zatrudnienia w służbie cywilnej — decyduje o podleganiu ocenie okresowej i o skutkach ocen. */
public enum EmploymentStatus {
    /** Urzędnik SC (mianowanie): stopnie służbowe (art. 89), art. 71 ust. 1 pkt 1 przy dwóch ocenach negatywnych. */
    URZEDNIK_SC,
    PRACOWNIK_SC_NIEOKRESLONY,
    /** Umowa na czas określony (12 mies.) — pierwsza ocena z art. 37–38 w zw. z art. 35 ust. 3, nie ocena okresowa. */
    PRACOWNIK_SC_OKRESLONY,
    /** Wyższe stanowisko w służbie cywilnej — wyłączone z ocen okresowych (art. 81 ust. 1). */
    WYZSZE_STANOWISKO_SC;

    public boolean isCivilServant() {
        return this == URZEDNIK_SC;
    }

    public boolean subjectToPeriodicEvaluation() {
        return this == URZEDNIK_SC || this == PRACOWNIK_SC_NIEOKRESLONY;
    }
}
