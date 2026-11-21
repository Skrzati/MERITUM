package pl.gov.kprm.oceny.pdf;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.EvaluationHistory;
import pl.gov.kprm.oceny.domain.IprzItem;
import pl.gov.kprm.oceny.domain.SignatureMethod;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.rules.AppealAuthority;
import pl.gov.kprm.oceny.rules.Criterion;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.EvaluationRuleEngine;
import pl.gov.kprm.oceny.rules.PerformanceLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/** Odwzorowanie 1:1 wzoru arkusza A/B/C (załączniki nr 2–4 do rozporządzenia PRM z 4.04.2016, Dz. U. poz. 470). */
@Component
public class SheetHtmlRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String X = "X";
    private static final String EMPTY = "&nbsp;";
    private static final String REGULATION = "rozporządzenia Prezesa Rady Ministrów z dnia 4 kwietnia 2016 r. w sprawie "
            + "warunków i sposobu przeprowadzania ocen okresowych urzędników służby cywilnej i pracowników służby cywilnej "
            + "(Dz. U. poz. 470)";
    private static final String ACT = "ustawy z dnia 21 listopada 2008 r. o służbie cywilnej";
    static final String QES_NOTE = "Podpis kwalifikowany (mObywatel QES / eIDAS 2.0)";
    private static final List<String[]> SCALE_HEADERS = List.of(
            new String[]{"Znacznie poniżej oczekiwań", "1 punkt"}, new String[]{"Poniżej oczekiwań", "3 punkty"},
            new String[]{"Na poziomie oczekiwań", "5 punktów"}, new String[]{"Powyżej oczekiwań", "7 punktów"},
            new String[]{"Znacznie powyżej oczekiwań", "9 punktów"});
    private static final List<Integer> SCALE = EvaluationRuleEngine.ALLOWED_SCORES.stream().sorted().toList();
    private static final String[][] LEVEL_ROWS = {
            {"SIGNIFICANTLY_BELOW", "od 1 punktu do 2 punktów", "znacznie poniżej oczekiwań"},
            {"BELOW", "powyżej 2 punktów do 4 punktów", "poniżej oczekiwań"},
            {"MEETS", "powyżej 4 punktów do 6 punktów", "na poziomie oczekiwań"},
            {"ABOVE", "powyżej 6 punktów do 8 punktów", "powyżej oczekiwań"},
            {"SIGNIFICANTLY_ABOVE", "powyżej 8 punktów do 9 punktów", "znacznie powyżej oczekiwań"}};
    private static final String[] GRADES_NOMINATIVE = {"", "pierwszy", "drugi", "trzeci", "czwarty", "piąty", "szósty",
            "siódmy", "ósmy", "dziewiąty"};
    private static final String[] GRADES_GENITIVE = {"", "pierwszego", "drugiego", "trzeciego", "czwartego", "piątego",
            "szóstego", "siódmego", "ósmego", "dziewiątego"};

    private static final String CSS = """
            @page { size: A4; margin: 14mm 13mm; }
            body { font-family: 'Noto Sans', 'DejaVu Sans', Arial, sans-serif; font-size: 9.5pt; color: #000; }
            .sheet { text-align: right; font-weight: bold; }
            h1 { font-size: 12.5pt; text-align: center; margin: 2px 0 8px; }
            h2 { font-size: 11pt; text-align: center; margin: 14px 0 6px; letter-spacing: 1px; }
            h3 { font-size: 9.5pt; margin: 10px 0 3px; text-transform: uppercase; }
            h1, h2, h3, .part-header, .section-title { page-break-after: avoid; break-after: avoid; }
            .part-two { page-break-before: always; break-before: page; margin-top: 0; }
            table { width: 100%; border-collapse: collapse; margin: 3px 0; page-break-inside: avoid; }
            tr, .sign, .award, .note { page-break-inside: avoid; break-inside: avoid; }
            th, td { border: 1px solid #000; padding: 3px 5px; vertical-align: top; }
            th { background: #eee; text-align: left; }
            td.l { width: 34%; font-weight: bold; background: #f7f7f7; }
            td.c, th.c { text-align: center; width: 9%; }
            td.x { text-align: center; width: 7%; vertical-align: middle; }
            .cb { display: inline-block; width: 12px; height: 12px; border: 1px solid #000; text-align: center;
                  line-height: 11px; font-weight: bold; font-size: 9pt; vertical-align: middle; }
            .choices { display: flex; flex-wrap: wrap; gap: 16px; margin: 4px 0; }
            .choice { display: inline-block; white-space: nowrap; }
            .choice .cb { margin-left: 6px; }
            .box { height: 62px; white-space: pre-wrap; }
            .text { white-space: pre-wrap; }
            .sign { display: flex; justify-content: space-between; gap: 12px; margin: 16px 0 4px; font-size: 8.5pt; }
            .sign-field { flex: 1; text-align: center; }
            .sign-line { min-height: 14px; border-bottom: 1px dotted #333; text-align: center; font-size: 9.5pt;
                         padding-bottom: 1px; }
            .sign-caption { margin-top: 2px; }
            .part-one table { margin: 2px 0; }
            .part-one th, .part-one td { padding: 2px 5px; }
            .part-one h3 { margin: 7px 0 2px; }
            .part-one p { margin: 6px 0; }
            .part-one .sign { margin: 12px 0 2px; }
            .note { font-size: 8.5pt; border: 1px solid #000; padding: 5px; margin-top: 8px; }
            .muted { color: #555; }
            .refusal { font-style: italic; }
            .qes { font-weight: bold; border: 1px solid #000; padding: 4px 6px; display: inline-block; }
            footer { margin-top: 14px; font-size: 7.5pt; color: #444; text-align: right; }
            """;

    public String render(SheetData d) {
        return render(d, SheetParts.ALL);
    }

    public String render(SheetData d, SheetParts parts) {
        EvaluationCycle c = d.cycle();
        StringBuilder h = new StringBuilder(16384)
                .append("<!DOCTYPE html><html lang=\"pl\"><head><meta charset=\"UTF-8\"><title>Arkusz ")
                .append(c.getSheetType()).append("</title><style>").append(CSS).append("</style></head><body>")
                .append("<div class=\"sheet\">ARKUSZ ").append(c.getSheetType()).append("</div>")
                .append("<h1>OCENA OKRESOWA<br>URZĘDNIKA SŁUŻBY CYWILNEJ LUB PRACOWNIKA SŁUŻBY CYWILNEJ</h1>");
        partOne(h, d);
        if (parts == SheetParts.PART_ONE) {
            return h.append("<footer>Wygenerowano w systemie MERITUM · cykl #").append(c.getId())
                    .append(" · wydruk części I (po rozmowie rozpoczynającej)</footer></body></html>").toString();
        }
        partTwo(h, d);
        if (d.includesGradeRequest()) {
            partThree(h, d);
        }
        return h.append("<footer>Wygenerowano w systemie MERITUM · cykl #").append(c.getId())
                .append("</footer></body></html>").toString();
    }

    private static void partOne(StringBuilder h, SheetData d) {
        EvaluationCycle c = d.cycle();
        h.append("<div class=\"part-one\"><h2 class=\"part-header\">CZĘŚĆ I</h2><table>")
                .append(row("Nazwa urzędu", d.office().getName())).append("</table>")
                .append("<h3>Dane ocenianego</h3>").append(personTable(d.employee(), "Stanowisko pracy"))
                .append("<h3>Dane oceniającego</h3>").append(personTable(d.evaluator(), "Stanowisko"))
                .append("<h3>Kryteria oceny i termin sporządzenia oceny</h3>")
                .append("<table><tr><th>Kryteria obowiązkowe</th><th>Kryteria dodatkowe wybrane przez oceniającego ")
                .append("(nie więcej niż 3)</th></tr>");
        List<Criterion> mandatory = d.criteria().stream().filter(Criterion::mandatory).toList();
        List<Criterion> additional = d.criteria().stream().filter(cr -> !cr.mandatory()).toList();
        int rows = Math.max(mandatory.size(), 3);
        for (int i = 0; i < rows; i++) {
            h.append("<tr><td>").append(i < mandatory.size() ? esc(mandatory.get(i).name()) : EMPTY).append("</td><td>")
                    .append(i < additional.size() ? esc(additional.get(i).name()) : EMPTY).append("</td></tr>");
        }
        h.append("</table><table>").append(row("Termin sporządzenia oceny (dd.mm.rrrr)", date(c.getDueDate())))
                .append("</table>")
                .append("<p>Potwierdzamy<br>− przeprowadzenie rozmowy, o której mowa w § 5 ust. 1 ").append(REGULATION)
                .append(", i omówienie w trakcie tej rozmowy wszystkich wymaganych zagadnień,<br>")
                .append("− zapoznanie ocenianego z kryteriami oceny oraz terminem sporządzenia oceny.</p>")
                .append(signature(c.getPlace(), c.getCriteriaInterviewDate(), "podpis oceniającego"))
                .append(signature(c.getPlace(), c.getCriteriaConfirmedOn(), "podpis ocenianego"))
                .append("</div>");
    }

    private static void partTwo(StringBuilder h, SheetData d) {
        EvaluationCycle c = d.cycle();
        // cz. II zawsze od nowej strony — nigdy osierocony nagłówek na dole strony 1
        h.append("<h2 class=\"part-header part-two\">CZĘŚĆ II</h2><table>")
                .append(row("Data przeprowadzenia rozmowy oceniającej (dd.mm.rrrr)", date(c.getEvaluationInterviewDate())))
                .append(row("Okres, w którym oceniany podlegał ocenie",
                        "od: " + date(c.getCriteriaConfirmedOn()) + "   do: " + date(c.getEvaluatedOn())))
                .append("</table>");
        scoreMatrix(h, "Kryteria obowiązkowe", d.criteria().stream().filter(Criterion::mandatory).toList(), d);
        scoreMatrix(h, "Kryteria dodatkowe wybrane przez oceniającego",
                d.criteria().stream().filter(cr -> !cr.mandatory()).toList(), d);

        h.append("<table>").append(row("Średnia arytmetyczna z ocen cząstkowych przyznanych za poszczególne kryteria oceny",
                averageDisplay(d))).append("</table>");
        h.append("<div class=\"award\"><h3 class=\"section-title\">Przyznanie oceny</h3><table><tr><th class=\"c\">X</th><th>Liczba punktów stanowiąca średnią ")
                .append("arytmetyczną ze wszystkich ocen cząstkowych</th><th>Ogólny poziom spełnienia kryteriów oceny</th></tr>");
        PerformanceLevel level = c.getPerformanceLevel();
        for (String[] r : LEVEL_ROWS) {
            h.append("<tr><td class=\"x\">").append(mark(level != null && level.name().equals(r[0])))
                    .append("</td><td>").append(r[1]).append("</td><td>").append(r[2]).append("</td></tr>");
        }
        EvaluationOutcome outcome = c.getFinalResult();
        h.append("</table><table><tr><td class=\"l\">OCENA OKRESOWA W SŁUŻBIE CYWILNEJ</td>")
                .append("<td>POZYTYWNA</td><td class=\"x\">").append(mark(outcome == EvaluationOutcome.POSITIVE))
                .append("</td><td>NEGATYWNA</td><td class=\"x\">").append(mark(outcome == EvaluationOutcome.NEGATIVE))
                .append("</td></tr></table></div>")
                .append(box("Uzasadnienie przyznanej oceny okresowej", c.getJustification()))
                .append(box("Wnioski dotyczące indywidualnego programu rozwoju zawodowego", c.getIprzNotes()));
        if (!d.iprzItems().isEmpty()) {
            h.append("<table><tr><th>Działanie rozwojowe (IPRZ)</th><th>Kryterium</th><th>Forma</th><th>Termin</th></tr>");
            for (IprzItem i : d.iprzItems()) {
                h.append("<tr><td>").append(esc(i.getActionTitle())).append("</td><td>")
                        .append(i.getCriterionCode() == null ? EMPTY : esc(i.getCriterionCode())).append("</td><td>")
                        .append(i.getFormType().name().toLowerCase()).append("</td><td>").append(date(i.getTargetDate()))
                        .append("</td></tr>");
            }
            h.append("</table>");
        }

        boolean civilServant = d.employee().isCivilServant();
        EvaluationHistory prev = d.previousEvaluation();
        h.append("<h3>Dane dotyczące poprzedniej oceny okresowej urzędnika służby cywilnej</h3>")
                .append("<table><tr><th>Ocena</th><th>Poziom</th><th>Data sporządzenia (dd.mm.rrrr)</th></tr><tr>");
        if (!civilServant) {
            h.append("<td colspan=\"3\" class=\"muted\">nie dotyczy (oceniany jest pracownikiem służby cywilnej)</td>");
        } else if (prev == null) {
            h.append("<td>brak</td><td>").append(EMPTY).append("</td><td>").append(EMPTY).append("</td>");
        } else {
            h.append("<td>").append(prev.getOutcome() == EvaluationOutcome.POSITIVE ? "pozytywna" : "negatywna")
                    .append("</td><td>").append(prev.getLevel().label()).append("</td><td>")
                    .append(date(prev.getEvaluatedOn())).append("</td>");
        }
        boolean attached = d.includesGradeRequest();
        h.append("</tr></table><h3>Czy do oceny okresowej dołączono wniosek o przyznanie kolejnego stopnia służbowego?</h3>")
                .append("<div class=\"choices\">").append(choice("TAK", civilServant && attached))
                .append(choice("NIE", civilServant && !attached))
                .append(choice("NIE DOTYCZY (oceniany jest pracownikiem służby cywilnej)", !civilServant))
                .append("</div>");

        h.append("<h3>Dane oceniającego</h3>").append(personTable(d.evaluator(), "Stanowisko"))
                .append(signature(c.getPlace(), c.getEvaluatedOn(), "podpis oceniającego"))
                .append("<p>Przeprowadzono ze mną rozmowę oceniającą oraz zapoznałem(-łam) się z oceną okresową i wnioskami ")
                .append("dotyczącymi indywidualnego programu rozwoju zawodowego.</p>");
        if (c.getRefusalDate() != null) {
            h.append("<p class=\"refusal\">[oceniany odmówił podpisania zapoznania się z oceną — ")
                    .append(date(c.getRefusalDate())).append("; ").append(esc(c.getRefusalReason())).append("]</p>");
        } else {
            h.append("<div class=\"sign\">").append(field(esc(c.getPlace()), "miejscowość"))
                    .append(field(date(c.getAcknowledgedAt()), "data (dd.mm.rrrr)"))
                    .append(field(c.getAcknowledgedAt() != null ? esc(d.employee().getFullName()) : "",
                            "imię i nazwisko lub pieczątka ocenianego"))
                    .append(field("", "podpis ocenianego")).append("</div>");
        }
        if (c.getSignatureMethod() == SignatureMethod.MOBYWATEL_QES) {
            h.append("<p class=\"qes\">✔ ").append(QES_NOTE).append("</p>");
        }
        if (c.getDelayReason() != null && !c.getDelayReason().isBlank()) {
            h.append("<p class=\"muted\">Ocena sporządzona po terminie — przyczyna: ").append(esc(c.getDelayReason()))
                    .append("</p>");
        }
        h.append("<div class=\"note\"><b>Pouczenie</b><br>Na podstawie art. 83 ust. 1 ").append(ACT)
                .append(" od oceny okresowej służy, w terminie 7 dni od dnia zapoznania się z oceną, sprzeciw do ")
                .append(AppealAuthority.of(d.office().isHasDgRole()).genitive())
                .append(AppealAuthority.of(d.office().isHasDgRole()).legalBasis()).append('.');
        if (c.getAppealDeadline() != null) {
            h.append("<br>Termin na wniesienie sprzeciwu upływa: ").append(date(c.getAppealDeadline())).append('.');
        }
        h.append("</div>");
    }

    private static void partThree(StringBuilder h, SheetData d) {
        User e = d.employee();
        int grade = e.getCivilServiceGrade() == null ? 0 : e.getCivilServiceGrade();
        h.append("<h2 style=\"page-break-before: always\">CZĘŚĆ III</h2>")
                .append("<p>Wniosek jest wypełniany i dołączany do arkusza oceny okresowej ocenianego tylko w sytuacji ")
                .append("skorzystania przez oceniającego z możliwości przewidzianej w art. 89 ust. 1 ").append(ACT).append(".</p>")
                .append("<h3>Dane urzędnika służby cywilnej</h3><table>")
                .append(row("Imię", firstName(e))).append(row("Nazwisko", lastName(e)))
                .append(row("Rok mianowania (rrrr)", e.getAppointmentYear() == null ? "" : String.valueOf(e.getAppointmentYear())))
                .append(row("Posiadany stopień służbowy", grade > 0 ? GRADES_NOMINATIVE[grade] : ""))
                .append(row("Data przyznania stopnia służbowego (dd.mm.rrrr)", date(e.getLastGradeDate())))
                .append("</table><h3>Wniosek o przyznanie urzędnikowi służby cywilnej kolejnego stopnia służbowego</h3>");
        if (grade >= EvaluationRuleEngine.HIGHEST_GRADE) {
            h.append("<p>Ocenianemu nie można przyznać kolejnego stopnia służbowego. Oceniany posiada już najwyższy możliwy ")
                    .append("stopień służbowy.</p>");
        } else {
            h.append("<p>W związku z uzyskaniem przez Panią/Pana <b>").append(esc(e.getFullName()))
                    .append("</b> pozytywnej oceny okresowej, na podstawie art. 89 ust. 1 ").append(ACT)
                    .append(" wnioskuję o przyznanie <b>").append(grade > 0 ? GRADES_GENITIVE[grade + 1] : "kolejnego")
                    .append("</b> stopnia służbowego.</p>");
        }
        h.append(box("Umotywowanie wniosku", d.gradeRequest().motivation()));
        h.append("<div class=\"sign\">").append(field(esc(d.cycle().getPlace()), "miejscowość"))
                .append(field(date(d.cycle().getEvaluatedOn()), "data (dd.mm.rrrr)"))
                .append(field(esc(d.evaluator().getFullName()), "imię i nazwisko lub pieczątka oceniającego"))
                .append(field("", "podpis oceniającego")).append("</div>");
    }

    private static void scoreMatrix(StringBuilder h, String title, List<Criterion> criteria, SheetData d) {
        h.append("<table><tr><th rowspan=\"2\">").append(esc(title.toUpperCase())).append("</th>");
        SCALE_HEADERS.forEach(s -> h.append("<th class=\"c\">").append(s[0]).append("</th>"));
        h.append("</tr><tr>");
        SCALE_HEADERS.forEach(s -> h.append("<th class=\"c\">").append(s[1]).append("</th>"));
        h.append("</tr>");
        if (criteria.isEmpty()) {
            h.append("<tr><td>brak</td>").append(("<td class=\"x\">" + mark(false) + "</td>").repeat(SCALE.size()))
                    .append("</tr>");
        }
        for (Criterion cr : criteria) {
            Integer score = d.scores().get(cr.code());
            h.append("<tr><td>").append(esc(cr.name())).append("</td>");
            SCALE.forEach(v -> h.append("<td class=\"x\">").append(mark(v.equals(score))).append("</td>"));
            h.append("</tr>");
        }
        h.append("</table>");
    }

    /** Średnia z przecinkiem dziesiętnym i 1 miejscem po przecinku (np. „4,2”). */
    static String averageDisplay(SheetData d) {
        List<Integer> values = d.criteria().stream().map(cr -> d.scores().get(cr.code())).toList();
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            return "";
        }
        int sum = values.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }

    private static String personTable(User u, String positionLabel) {
        return "<table>" + row("Imię", firstName(u)) + row("Nazwisko", lastName(u))
                + row("Komórka organizacyjna", u.getDepartment()) + row(positionLabel, u.getPositionTitle()) + "</table>";
    }

    private static String firstName(User u) {
        if (u.getFirstName() != null) return u.getFirstName();
        String full = Objects.toString(u.getFullName(), "");
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    private static String lastName(User u) {
        if (u.getLastName() != null) return u.getLastName();
        String full = Objects.toString(u.getFullName(), "");
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(space + 1) : "";
    }

    private static String signature(String place, LocalDate date, String who) {
        return "<div class=\"sign\">" + field(esc(place), "miejscowość") + field(date(date), "data (dd.mm.rrrr)")
                + field("", who) + "</div>";
    }

    /** Pole podpisu: wartość wyśrodkowana nad kropkowaną linią, podpis pola pod nią (zamiast kropek w tekście). */
    private static String field(String escapedValue, String caption) {
        return "<div class=\"sign-field\"><div class=\"sign-line\">" + escapedValue + "</div><div class=\"sign-caption\">"
                + caption + "</div></div>";
    }

    /** Jedyny styl pola wyboru w arkuszu: kwadrat z „X” albo pusty (bez znaków Unicode i elementów formularza). */
    static String mark(boolean checked) {
        return checked ? "<span class=\"cb\">" + X + "</span>" : "<span class=\"cb\"></span>";
    }

    private static String choice(String label, boolean checked) {
        return "<span class=\"choice\">" + label + mark(checked) + "</span>";
    }

    private static String row(String label, String value) {
        return "<tr><td class=\"l\">" + esc(label) + "</td><td>" + esc(value) + "</td></tr>";
    }

    private static String box(String label, String content) {
        return "<table><tr><th>" + esc(label.toUpperCase()) + "</th></tr><tr><td class=\"box\">" + esc(content)
                + "</td></tr></table>";
    }

    private static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
