package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.gov.kprm.oceny.domain.EscalationLevel;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.export.CsvReportRenderer;
import pl.gov.kprm.oceny.export.CsvReportRenderer.Row;
import pl.gov.kprm.oceny.rules.AnonymizedCount;
import pl.gov.kprm.oceny.service.OverdueService.OverdueItem;
import pl.gov.kprm.oceny.service.StatsService.Summary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Zanonimizowany raport CSV — te same agregaty i ten sam próg k co {@link StatsService}, bez danych osobowych. */
@Service
@RequiredArgsConstructor
public class ReportExportService {
    private final StatsService stats;
    private final OverdueService overdue;
    private final AuditService audit;

    public String cyclesReportCsv(User user) {
        Access.requireRole(user, Role.HR, Role.DG);
        Summary s = stats.summary(user);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("cykle", "RAZEM", s.totalCycles()));
        section(rows, "status", s.byStatus());
        section(rows, "wynik", s.byOutcome());
        section(rows, "poziom", s.byLevel());
        rows.add(new Row("terminy", "PO_TERMINIE", s.overdue()));
        rows.add(new Row("terminy", "TERMIN_7_DNI", s.dueIn7Days()));
        rows.add(new Row("terminy", "TERMIN_30_DNI", s.dueIn30Days()));
        Map<EscalationLevel, Long> escalation = overdue.overdue(user).stream()
                .collect(Collectors.groupingBy(OverdueItem::escalationLevel, Collectors.counting()));
        for (EscalationLevel level : EscalationLevel.values()) {
            rows.add(new Row("eskalacja", level.name(),
                    AnonymizedCount.of(escalation.getOrDefault(level, 0L), s.kAnonymity())));
        }
        rows.add(new Row("sprzeciwy", "WNIESIONE", s.appeals()));
        audit.log(user, user.getOfficeId(), "REPORT_EXPORTED", AuditService.USER, user.getId(),
                Map.of("format", "CSV", "rows", rows.size(), "kAnonymity", s.kAnonymity()));
        return CsvReportRenderer.render(rows, s.kAnonymity());
    }

    private static void section(List<Row> rows, String name, Map<String, AnonymizedCount> counts) {
        counts.forEach((key, count) -> rows.add(new Row(name, key, count)));
    }
}
