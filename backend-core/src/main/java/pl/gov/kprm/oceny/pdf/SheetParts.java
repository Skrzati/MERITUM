package pl.gov.kprm.oceny.pdf;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Zakres wydruku arkusza: cały wzór albo sama część I (po rozmowie rozpoczynającej, § 5 ust. 1). */
public enum SheetParts {
    ALL, PART_ONE;

    /** Parametr {@code parts}: brak / „all” → cały arkusz, „1” → część I; inne wartości są błędem (400). */
    public static SheetParts parse(String parts) {
        if (parts == null || parts.isBlank() || parts.equalsIgnoreCase("all")) {
            return ALL;
        }
        if (parts.trim().equals("1")) {
            return PART_ONE;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dozwolone wartości parametru parts: 1, all");
    }
}
