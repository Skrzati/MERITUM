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

        assertThat(html).contains("ARKUSZ C", "URZĘDNIKA SŁUŻBY CYWILNEJ LUB PRACOWNIKA SŁUŻBY CYWILNEJ", "CZĘŚĆ I",
                "CZĘŚĆ II", "Ministerstwo Cyfryzacji - Demo", "Katarzyna", "Wiśniewska", "Komórka organizacyjna",
                "Wydział Rozwoju Systemów", "Główny specjalista", "Naczelnik wydziału", "Piotr",
                "Termin sporządzenia oceny", "31.12.2026", "Data przeprowadzenia rozmowy oceniającej", "Potwierdzamy",
                "§ 5 ust. 1", "4,2", "Przyznanie oceny",
                "Na podstawie art. 83 ust. 1 ustawy z dnia 21 listopada 2008 r. o służbie cywilnej",
                "WNIOSKI DOTYCZĄCE INDYWIDUALNEGO PROGRAMU ROZWOJU ZAWODOWEGO", "12.11.2026", "Warszawa")
                .doesNotContain("4.2");
    }

    @Test
    void appealInstructionNamesHeadOfOfficeWhenThereIsNoDirectorGeneral() {
        SheetData withDg = data(true, null);
        withDg.office().setHasDgRole(true);
        assertThat(renderer.render(withDg)).contains("sprzeciw do dyrektora generalnego urzędu.");

        SheetData smallOffice = data(true, null);
        smallOffice.office().setHasDgRole(false);
        assertThat(renderer.render(smallOffice)).contains("sprzeciw do kierownika urzędu", "art. 25 ust. 9")
                .doesNotContain("sprzeciw do dyrektora generalnego");
    }

    @Test
    void structuredIprzItemsArePrintedInDevelopmentSection() {
        pl.gov.kprm.oceny.domain.IprzItem item = new pl.gov.kprm.oceny.domain.IprzItem();
        item.setActionTitle("Szkolenie z kontroli jakości <dokumentów>");
        item.setCriterionCode("K08");
        item.setFormType(pl.gov.kprm.oceny.domain.IprzItem.FormType.SZKOLENIE);
        item.setTargetDate(LocalDate.of(2027, 3, 31));
        SheetData d = data(true, null);
        SheetData withItems = new SheetData(d.office(), d.employee(), d.evaluator(), d.cycle(), d.criteria(), d.scores(),
                d.gradeRequest(), d.previousEvaluation(), List.of(item));
        assertThat(renderer.render(withItems)).contains("Działanie rozwojowe (IPRZ)", "&lt;dokumentów&gt;", "szkolenie",
                "31.03.2027");
    }

    @Test
    void marksLevelRowAndNegativeOutcomeWithX() {
        String html = renderer.render(data(true, null));

        assertThat(html).contains("<td class=\"x\"><span class=\"cb\">X</span></td><td>powyżej 4 punktów do 6 punktów</td>")
                .contains("NEGATYWNA</td><td class=\"x\"><span class=\"cb\">X</span></td>")
                .contains("POZYTYWNA</td><td class=\"x\"><span class=\"cb\"></span></td>")
                .doesNotContain("☑", "type=\"checkbox\"");
    }

    @Test
    void partTwoStartsOnNewPageAndSignaturesUseDottedLines() {
        String html = renderer.render(data(true, null));
        assertThat(html).contains("<h2 class=\"part-header part-two\">CZĘŚĆ II</h2>",
                        ".part-two { page-break-before: always; break-before: page;",
                        "page-break-after: avoid; break-after: avoid;", "page-break-inside: avoid; break-inside: avoid;",
                        "<div class=\"award\">", "<div class=\"sign-line\">Warszawa</div>",
                        "border-bottom: 1px dotted #333")
                .doesNotContain("....");
        assertThat(renderer.render(data(true, null), SheetParts.PART_ONE)).doesNotContain("part-two\">");
    }

    @Test
    void previousEvaluationAndGradeAttachmentSection() {
        assertThat(renderer.render(data(true, null))).contains("pozytywna", "powyżej oczekiwań", "15.12.2024");
        assertThat(renderer.render(data(false, null)))
                .contains("<span class=\"choice\">NIE DOTYCZY (oceniany jest pracownikiem służby cywilnej)"
                        + "<span class=\"cb\">X</span></span>")
                .contains("<span class=\"choice\">TAK<span class=\"cb\"></span></span>")
                .contains(".choices { display: flex; flex-wrap: wrap; gap: 16px;");

    }

    @Test
    void partThreeOnlyForPositiveCivilServantWithGradeRequest() {
        assertThat(renderer.render(positive(data(true, GRADE)))).contains("CZĘŚĆ III", "Rok mianowania", "2019",
                "drugi", "wnioskuję o przyznanie <b>trzeciego</b> stopnia", "Panią/Pana");
        assertThat(renderer.render(data(true, GRADE))).as("ocena negatywna").doesNotContain("CZĘŚĆ III");
        assertThat(renderer.render(data(false, GRADE))).doesNotContain("CZĘŚĆ III");
        assertThat(renderer.render(data(true, null))).doesNotContain("CZĘŚĆ III");
        assertThat(renderer.render(data(true, new GradeRequest(false, "x")))).doesNotContain("CZĘŚĆ III");
    }

    @Test
    void qualifiedSignatureAndDelayReasonAreAnnotated() {
        SheetData d = data(true, null);
        assertThat(renderer.render(d)).doesNotContain("mObywatel QES");
        d.cycle().setSignatureMethod(SignatureMethod.MOBYWATEL_QES);
        d.cycle().setDelayReason("Długotrwałe zwolnienie oceniającego");
        assertThat(renderer.render(d)).contains("Podpis kwalifikowany (mObywatel QES / eIDAS 2.0)",
                "po terminie — przyczyna: Długotrwałe zwolnienie oceniającego");
    }

    @Test
    void partOneOnlyPrintsCriteriaAndStartingSignatures() {
        String html = renderer.render(positive(data(true, GRADE)), SheetParts.PART_ONE);
        assertThat(html).contains("CZĘŚĆ I", "Kryteria obowiązkowe", "podpis ocenianego", "wydruk części I")
                .doesNotContain("CZĘŚĆ II", "CZĘŚĆ III", "Pouczenie", "Średnia arytmetyczna");
        assertThat(SheetParts.parse(null)).isEqualTo(SheetParts.ALL);
        assertThat(SheetParts.parse("1")).isEqualTo(SheetParts.PART_ONE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> SheetParts.parse("2"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void evaluatorNameIsPrintedAtSignaturesOnceSigned() {
        String html = renderer.render(data(true, null));
        assertThat(html).contains("<div class=\"sign-line\">Piotr Nowak</div><div class=\"sign-caption\">"
                + "imię i nazwisko lub pieczątka oceniającego</div>");
        SheetData d = data(true, null);
        d.cycle().setEvaluatedOn(null);
        d.cycle().setCriteriaInterviewDate(null);
        assertThat(renderer.render(d)).contains("<div class=\"sign-line\"></div><div class=\"sign-caption\">"
                + "imię i nazwisko lub pieczątka oceniającego</div>").doesNotContain(">Piotr Nowak</div>");
    }

    @Test
    void substituteWhoEvaluatedSignsPartTwo() {
        SheetData d = data(true, null);
        d.evaluator().setId(5L);
        User deputy = new User();
        deputy.setId(9L);
        deputy.setFullName("Anna Zielińska");
        deputy.setPositionTitle("Zastępca naczelnika");
        SheetData substituted = new SheetData(d.office(), d.employee(), d.evaluator(), d.cycle(), d.criteria(), d.scores(),
                d.gradeRequest(), d.previousEvaluation(), d.iprzItems(), deputy);
        String html = renderer.render(substituted);
        assertThat(html).contains("<div class=\"sign-line\">Anna Zielińska</div>", "Zastępca naczelnika",
                "Ocenę sporządzono w zastępstwie: Piotr Nowak", "<div class=\"sign-line\">Piotr Nowak</div>");
        assertThat(renderer.render(new SheetData(d.office(), d.employee(), d.evaluator(), d.cycle(), d.criteria(),
                d.scores(), d.gradeRequest(), d.previousEvaluation(), d.iprzItems(), d.evaluator())))
                .doesNotContain("w zastępstwie");
    }

    @Test
    void escapesUserContent() {
        String html = renderer.render(positive(data(true, GRADE)));

        assertThat(html).doesNotContain("<b>w projekcie</b>").contains("&lt;b&gt;w projekcie&lt;/b&gt;");
    }

    @Test
    void averageIsEmptyUntilAllCriteriaScored() {
        SheetData partial = new SheetData(office(), employee(true), evaluator(), cycle(), CRITERIA,
                Map.of("K08", 5), null, null, List.of());
        assertThat(SheetHtmlRenderer.averageDisplay(partial)).isEmpty();
    }

    private static SheetData data(boolean civilServant, GradeRequest grade) {
        EvaluationHistory previous = new EvaluationHistory();
        previous.setOutcome(EvaluationOutcome.POSITIVE);
        previous.setLevel(PerformanceLevel.ABOVE);
        previous.setEvaluatedOn(LocalDate.of(2024, 12, 15));
        return new SheetData(office(), employee(civilServant), evaluator(), cycle(), CRITERIA, SCORES, grade, previous, List.of());
    }

    private static SheetData positive(SheetData d) {
        d.cycle().setFinalResult(EvaluationOutcome.POSITIVE);
        return d;
    }

    private static Office office() {
        Office o = new Office();
        o.setName("Ministerstwo Cyfryzacji - Demo");
        o.setCity("Warszawa");
        return o;
    }

    private static User employee(boolean civilServant) {
        User u = new User();
        u.setFullName("Katarzyna Wiśniewska");
        u.setFirstName("Katarzyna");
        u.setLastName("Wiśniewska");
        u.setDepartment("Wydział Rozwoju Systemów");
        u.setPositionTitle("Główny specjalista");
        u.setCivilServant(civilServant);
        u.setAppointmentYear(2019);
        u.setCivilServiceGrade(2);
        u.setLastGradeDate(LocalDate.of(2023, 9, 1));
        return u;
    }

    private static User evaluator() {
        User u = new User();
        u.setFullName("Piotr Nowak");
        u.setPositionTitle("Naczelnik wydziału");
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
        c.setPlace("Warszawa");
        c.setCriteriaInterviewDate(LocalDate.of(2025, 1, 10));
        c.setCriteriaConfirmedOn(LocalDate.of(2025, 1, 13));
        c.setEvaluationInterviewDate(LocalDate.of(2026, 11, 2));
        c.setEvaluatedOn(LocalDate.of(2026, 11, 3));
        return c;
    }
}
