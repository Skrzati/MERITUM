package pl.gov.kprm.oceny.rules;

import java.time.LocalDate;
import java.util.List;

/**
 * Arkusz oceny okresowej należy do części B akt osobowych — nie podlega automatycznemu usunięciu w ramach RODO.
 * Okres przechowywania dokumentacji pracowniczej: 10 lat (stosunek pracy nawiązany po 31.12.2018) albo 50 lat
 * (wcześniej lub data nieznana — kategoria ostrożnościowa); spór sądowy (art. 83 ust. 5) wstrzymuje brakowanie.
 * Z paczki archiwalnej jawnie wyłączone są dane pomocnicze (zakaz „czarnych teczek”).
 */
public final class RetentionRules {
    public static final LocalDate TEN_YEAR_REGIME_FROM = LocalDate.of(2019, 1, 1);
    public static final int SHORT_YEARS = 10;
    public static final int LONG_YEARS = 50;
    public static final String PERSONNEL_FILE_PART = "B";
    /** Dane pomocnicze — nie są dokumentacją pracowniczą i nie trafiają do archiwum. */
    public static final List<String> EXCLUDED_FROM_ARCHIVE = List.of("EmployeeNote", "PreInterviewSurvey",
            "EmployeeSelfPrep", "EmployeePrivateNote");
    public static final String EXCLUSION_REASON = "Dane pomocnicze (notatki robocze przełożonego, ankiety "
            + "przygotowawcze) nie stanowią dokumentacji pracowniczej — wyłączone z akt (zakaz „czarnych teczek”, "
            + "orzecznictwo SN) i czyszczone po zamknięciu cyklu";

    private RetentionRules() {
    }

    public record Retention(String personnelFilePart, int years, String category, String basis, boolean legalHold,
                            String retainUntil) {
    }

    public static Retention of(LocalDate employmentStart, boolean legalHold) {
        boolean shortRegime = employmentStart != null && !employmentStart.isBefore(TEN_YEAR_REGIME_FROM);
        int years = shortRegime ? SHORT_YEARS : LONG_YEARS;
        String basis = shortRegime
                ? "art. 94 pkt 9b Kodeksu pracy — stosunek pracy nawiązany po 31.12.2018"
                : "art. 94⁴ Kodeksu pracy w zw. z ustawą z 10.01.2018 — stosunek pracy nawiązany przed 1.01.2019"
                + (employmentStart == null ? " lub data nieznana (kategoria ostrożnościowa)" : "");
        String until = legalHold
                ? "wstrzymane — legal hold do prawomocnego zakończenia sporu (art. 83 ust. 5 ustawy o służbie cywilnej)"
                : years + " lat od końca roku kalendarzowego, w którym stosunek pracy ustał";
        return new Retention(PERSONNEL_FILE_PART, years, "B" + years, basis, legalHold, until);
    }

    /** Dane pomocnicze cyklu można wyczyścić po upływie okresu karencji od zamknięcia (bez sporu i sprzeciwu). */
    public static boolean workingNotesPurgeable(LocalDate closedOn, int graceDays, boolean legalHold, LocalDate today) {
        return !legalHold && closedOn != null && closedOn.plusDays(graceDays).isBefore(today);
    }
}
