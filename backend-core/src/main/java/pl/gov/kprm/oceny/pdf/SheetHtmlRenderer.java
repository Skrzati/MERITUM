package pl.gov.kprm.oceny.pdf;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.rules.Criterion;
import pl.gov.kprm.oceny.rules.DeadlineService;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.EvaluationRuleEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/** Odwzorowanie wzoru arkusza A/B/C (załączniki nr 2–4 rozporządzenia) w czystym HTML/CSS pod A4. */
@Component
public class SheetHtmlRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<Integer> SCALE = EvaluationRuleEngine.ALLOWED_SCORES.stream().sorted().toList();
    private static final String EMPTY = "&nbsp;";

    private static final String CSS = """
            @page { size: A4; margin: 16mm 14mm; }
            body { font-family: 'Noto Sans', 'DejaVu Sans', Arial, sans-serif; font-size: 10.5pt; color: #000; }
            h1 { font-size: 14pt; text-align: center; margin: 4px 0 2px; }
            h2 { font-size: 11.5pt; margin: 18px 0 6px; padding: 4px 6px; background: #e6e6e6; border: 1px solid #000; }
            .sub { text-align: center; font-size: 9.5pt; margin-bottom: 12px; }
            .office { text-align: center; font-weight: bold; text-transform: uppercase; }
            table { width: 100%; border-collapse: collapse; margin: 4px 0; page-break-inside: avoid; }
            th, td { border: 1px solid #000; padding: 4px 6px; vertical-align: top; }
            th { background: #f2f2f2; font-weight: bold; text-align: left; }
            td.c, th.c { text-align: center; width: 9%; }
            td.label { width: 38%; font-weight: bold; background: #fafafa; }
            .box { min-height: 48px; white-space: pre-wrap; }
            .sign { margin-top: 26px; display: flex; justify-content: space-between; font-size: 9pt; }
            .sign div { width: 44%; border-top: 1px dotted #000; text-align: center; padding-top: 3px; }
            .note { font-size: 9pt; border: 1px solid #000; padding: 6px; margin-top: 8px; }
            .result { font-weight: bold; font-size: 11.5pt; }
            footer { margin-top: 18px; font-size: 8pt; color: #444; text-align: right; }
            """;

    public String render(SheetData d) {
        EvaluationCycle c = d.cycle();
        StringBuilder html = new StringBuilder(8192)
                .append("<!DOCTYPE html><html lang=\"pl\"><head><meta charset=\"UTF-8\"><title>Arkusz oceny okresowej ")
                .append(c.getSheetType()).append("</title><style>").append(CSS).append("</style></head><body>");

        html.append("<div class=\"office\">").append(esc(d.office().getName())).append("</div>")
                .append("<h1>ARKUSZ OCENY OKRESOWEJ — ARKUSZ ").append(c.getSheetType()).append("</h1>")
                .append("<div class=\"sub\">").append(esc(d.sheetDescription())).append("</div>");

        html.append("<table>")
                .append(row("Imię i nazwisko ocenianego", d.employee().getFullName()))
                .append(row("Status", d.employee().isCivilServant() ? "urzędnik służby cywilnej" : "pracownik służby cywilnej"))
                .append(row("Imię i nazwisko oceniającego", d.evaluator().getFullName()))
                .append(row("Okres objęty oceną", date(c.getStartDate()) + " – " + date(c.getDueDate())))
                .append(row("Rodzaj oceny", c.isReevaluation() ? "ponowna ocena (po ocenie negatywnej)" : "ocena okresowa"))
                .append("</table>");

        renderPartOne(html, d);
        renderPartTwo(html, d);
        if (d.includesGradeRequest()) {
            renderPartThree(html, d);
        }
        html.append("<footer>Wygenerowano w systemie MERITUM · cykl #").append(c.getId()).append("</footer></body></html>");
        return html.toString();
    }

    private static void renderPartOne(StringBuilder html, SheetData d) {
        html.append("<h2>CZĘŚĆ I — Kryteria oceny</h2><table><tr><th style=\"width:8%\">Lp.</th><th>Kryterium</th>")
                .append("<th style=\"width:22%\">Rodzaj</th></tr>");
        int i = 1;
        for (Criterion cr : d.criteria()) {
            html.append("<tr><td class=\"c\">").append(i++).append("</td><td>").append(esc(cr.name()))
                    .append("</td><td>").append(cr.mandatory() ? "obowiązkowe" : "dodatkowe").append("</td></tr>");
        }
        if (d.criteria().isEmpty()) {
            html.append("<tr><td colspan=\"3\">Kryteria nie zostały jeszcze ustalone.</td></tr>");
        }
        html.append("</table><table>")
                .append(row("Data rozmowy i ustalenia kryteriów", date(d.criteriaSetOn())))
                .append(row("Potwierdzenie zapoznania się ocenianego z kryteriami", date(d.criteriaConfirmedOn())))
                .append("</table>")
                .append(signatures("data i podpis oceniającego", "data i podpis ocenianego"));
    }

    private static void renderPartTwo(StringBuilder html, SheetData d) {
        EvaluationCycle c = d.cycle();
        html.append("<h2>CZĘŚĆ II — Ocena</h2><table><tr><th>Kryterium</th>");
        SCALE.forEach(v -> html.append("<th class=\"c\">").append(v).append("</th>"));
        html.append("</tr>");
        for (Criterion cr : d.criteria()) {
            Integer score = d.scores().get(cr.code());
            html.append("<tr><td>").append(esc(cr.name())).append("</td>");
            SCALE.forEach(v -> html.append("<td class=\"c\">").append(v.equals(score) ? "X" : EMPTY).append("</td>"));
            html.append("</tr>");
        }
        html.append("</table><table>")
                .append(row("Średnia ocen cząstkowych", averageDisplay(d)))
                .append(row("Poziom wykonywania obowiązków", c.getPerformanceLevel() == null ? "" : c.getPerformanceLevel().label()))
                .append("<tr><td class=\"label\">Ocena</td><td class=\"result\">").append(outcome(c.getFinalResult()))
                .append("</td></tr>")
                .append("</table>")
                .append(box("Uzasadnienie oceny", c.getJustification()))
                .append(box("Wnioski dotyczące indywidualnego programu rozwoju zawodowego (IPRZ)", c.getIprzNotes()))
                .append(signatures("data i podpis oceniającego", "data i podpis ocenianego (zapoznanie się z oceną)"));

        html.append("<div class=\"note\"><b>Pouczenie:</b> od oceny okresowej ocenianemu przysługuje sprzeciw do ")
                .append("dyrektora generalnego urzędu, wnoszony w terminie ").append(DeadlineService.APPEAL_DAYS)
                .append(" dni od dnia zapoznania się z oceną.");
        if (c.getAcknowledgedAt() != null) {
            html.append("<br>Data zapoznania się z oceną: ").append(date(c.getAcknowledgedAt()))
                    .append(". Termin na wniesienie sprzeciwu upływa: ").append(date(c.getAppealDeadline())).append('.');
        }
        if (c.getRefusalDate() != null) {
            html.append("<br>Oceniany odmówił zapoznania się z oceną w dniu ").append(date(c.getRefusalDate()))
                    .append(". Powód: ").append(esc(c.getRefusalReason())).append('.');
        }
        html.append("</div>");
    }

    private static void renderPartThree(StringBuilder html, SheetData d) {
        html.append("<h2>CZĘŚĆ III — Wniosek o przyznanie kolejnego stopnia służbowego</h2>")
                .append("<table>").append(row("Urzędnik służby cywilnej", d.employee().getFullName())).append("</table>")
                .append(box("Umotywowanie wniosku", d.gradeRequest().motivation()))
                .append(signatures("data i podpis oceniającego", "data i podpis bezpośredniego przełożonego"));
    }

    static String averageDisplay(SheetData d) {
        List<Integer> values = d.criteria().stream().map(cr -> d.scores().get(cr.code())).toList();
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            return "";
        }
        int sum = values.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String outcome(EvaluationOutcome o) {
        if (o == null) return EMPTY;
        return o == EvaluationOutcome.POSITIVE ? "POZYTYWNA" : "NEGATYWNA";
    }

    private static String row(String label, String value) {
        return "<tr><td class=\"label\">" + esc(label) + "</td><td>" + esc(value) + "</td></tr>";
    }

    private static String box(String label, String content) {
        return "<table><tr><th>" + esc(label) + "</th></tr><tr><td class=\"box\">" + esc(content) + "</td></tr></table>";
    }

    private static String signatures(String left, String right) {
        return "<div class=\"sign\"><div>" + esc(left) + "</div><div>" + esc(right) + "</div></div>";
    }

    private static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
