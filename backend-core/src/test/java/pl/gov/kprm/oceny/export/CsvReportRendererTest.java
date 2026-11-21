package pl.gov.kprm.oceny.export;

import org.junit.jupiter.api.Test;
import pl.gov.kprm.oceny.export.CsvReportRenderer.Row;
import pl.gov.kprm.oceny.rules.AnonymizedCount;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvReportRendererTest {
    @Test
    void startsWithBomAndHidesSuppressedCounts() {
        String csv = CsvReportRenderer.render(List.of(
                new Row("status", "COMPLETED", AnonymizedCount.of(8, 5)),
                new Row("wynik", "NEGATIVE", AnonymizedCount.of(2, 5))), 5);
        assertThat(csv).startsWith(CsvReportRenderer.BOM + "sekcja;kategoria;liczba\r\n")
                .contains("status;COMPLETED;8\r\n", "wynik;NEGATIVE;<5\r\n");
    }

    @Test
    void quotesSeparatorsAndNeutralizesFormulas() {
        assertThat(CsvReportRenderer.cell("a;b")).isEqualTo("\"a;b\"");
        assertThat(CsvReportRenderer.cell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
    }
}
