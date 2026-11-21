package pl.gov.kprm.oceny.pdf;

import org.junit.jupiter.api.Test;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.rules.Criterion;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.GradeRequest;
import pl.gov.kprm.oceny.rules.PerformanceLevel;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SheetHtmlRendererTest {
    private static final List<Criterion> CRITERIA = List.of(
            new Criterion("K08", "Rzetelność", true),
            new Criterion("K10", "Wykorzystywanie wiedzy i doskonalenie zawodowe", true),
            new Criterion("K04", "Organizacja pracy i orientacja na osiąganie celów", true),
            new Criterion("K11", "Współpraca", true),
            new Criterion("K01", "Komunikacja", true));
    private static final Map<String, Integer> SCORES = Map.of("K08", 1, "K10", 5, "K04", 5, "K11", 5, "K01", 5);
    private static final GradeRequest GRADE = new GradeRequest(true, "Wyróżniające wyniki <b>w projekcie</b>");

    private final SheetHtmlRenderer renderer = new SheetHtmlRenderer();

    @Test
    void rendersAllPartsWithExactAverageRoundedToOneDecimal() {
        String html = renderer.render(data(true, GRADE));

        assertThat(html).contains("ARKUSZ C", "CZĘŚĆ I", "CZĘŚĆ II", "Ministerstwo Cyfryzacji - Demo",
                "Katarzyna Wiśniewska", "Piotr Nowak", "obowiązkowe", "4.2", "na poziomie oczekiwań", "NEGATYWNA",
                "Wnioski dotyczące indywidualnego programu rozwoju zawodowego", "sprzeciw", "7 dni", "12.11.2026");
    }

    @Test
    void partThreeOnlyForCivilServantWithGradeRequest() {
        assertThat(renderer.render(data(true, GRADE))).contains("CZĘŚĆ III");
        assertThat(renderer.render(data(false, GRADE))).doesNotContain("CZĘŚĆ III");
        assertThat(renderer.render(data(true, null))).doesNotContain("CZĘŚĆ III");
        assertThat(renderer.render(data(true, new GradeRequest(false, "x")))).doesNotContain("CZĘŚĆ III");
    }

    @Test
    void escapesUserContent() {
        String html = renderer.render(data(true, GRADE));

        assertThat(html).doesNotContain("<b>w projekcie</b>").contains("&lt;b&gt;w projekcie&lt;/b&gt;");
    }

    @Test
    void averageIsEmptyUntilAllCriteriaScored() {
        SheetData partial = new SheetData(office(), employee(true), evaluator(), cycle(), "", CRITERIA,
                Map.of("K08", 5), null, null, null);
        assertThat(SheetHtmlRenderer.averageDisplay(partial)).isEmpty();
    }

    private static SheetData data(boolean civilServant, GradeRequest grade) {
        return new SheetData(office(), employee(civilServant), evaluator(), cycle(), "Pozostałe stanowiska (§ 3 ust. 4)",
                CRITERIA, SCORES, grade, LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 14));
    }

    private static Office office() {
        Office o = new Office();
        o.setName("Ministerstwo Cyfryzacji - Demo");
        return o;
    }

    private static User employee(boolean civilServant) {
        User u = new User();
        u.setFullName("Katarzyna Wiśniewska");
        u.setCivilServant(civilServant);
        return u;
    }

    private static User evaluator() {
        User u = new User();
        u.setFullName("Piotr Nowak");
        return u;
    }

    private static EvaluationCycle cycle() {
        EvaluationCycle c = new EvaluationCycle();
        c.setId(1L);
        c.setSheetType(SheetType.C);
        c.setStatus(CycleStatus.COMPLETED);
        c.setStartDate(LocalDate.of(2025, 1, 1));
        c.setDueDate(LocalDate.of(2026, 12, 31));
        c.setPerformanceLevel(PerformanceLevel.MEETS);
        c.setFinalResult(EvaluationOutcome.NEGATIVE);
        c.setJustification("Uzasadnienie");
        c.setIprzNotes("Szkolenie z planowania");
        c.setAcknowledgedAt(LocalDate.of(2026, 11, 4));
        c.setAppealDeadline(LocalDate.of(2026, 11, 12));
        return c;
    }
}
