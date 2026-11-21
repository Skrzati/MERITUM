package pl.gov.kprm.oceny.export;

import pl.gov.kprm.oceny.rules.AnonymizedCount;

import java.util.List;

/** CSV dla Excela: UTF-8 z BOM, separator „;” (polskie ustawienia regionalne), CRLF; liczności ukryte jako „<k”. */
public final class CsvReportRenderer {
    public static final String BOM = "\uFEFF";
    private static final String SEPARATOR = ";";
    private static final String CRLF = "\r\n";

    public record Row(String section, String key, AnonymizedCount count) {
    }

    private CsvReportRenderer() {
    }

    public static String render(List<Row> rows, int k) {
        StringBuilder out = new StringBuilder(BOM).append("sekcja;kategoria;liczba").append(CRLF);
        for (Row row : rows) {
            String value = row.count().suppressed() ? "<" + k : String.valueOf(row.count().value());
            out.append(cell(row.section())).append(SEPARATOR).append(cell(row.key())).append(SEPARATOR)
                    .append(cell(value)).append(CRLF);
        }
        return out.toString();
    }

    static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v; // ochrona przed wstrzyknięciem formuły w Excelu
        }
        return v.contains(SEPARATOR) || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
