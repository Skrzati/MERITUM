package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.export.CsvReportRenderer;
import pl.gov.kprm.oceny.export.CsvReportRenderer.Row;
import pl.gov.kprm.oceny.repository.AuditLogRepository;
import pl.gov.kprm.oceny.repository.EvaluationCycleRepository;
import pl.gov.kprm.oceny.repository.IprzItemRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.AnonymizedCount;
import pl.gov.kprm.oceny.rules.EmploymentAlertRules;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.PerformanceLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Raporty zarządcze urzędu (HR, DG): plan szkoleń z zatwierdzonych IPRZ, kalibracja ocen per komórka (k-anonimizacja)
 * oraz zestawienie do rocznego Sprawozdania Szefa Służby Cywilnej.
 */
@Service
@Transactional(readOnly = true)
public class ManagementReportService {
    static final int TOP_ACTIONS = 10;
    private static final String UNKNOWN_DEPARTMENT = "(bez komórki)";

    private final EvaluationCycleRepository cycles;
    private final IprzItemRepository iprzItems;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final HrAlertService alerts;
    private final AuditService audit;
    private final Clock clock;
    private final int k;

    public ManagementReportService(EvaluationCycleRepository cycles, IprzItemRepository iprzItems, UserRepository users,
                                   AuditLogRepository auditLogs, HrAlertService alerts, AuditService audit, Clock clock,
                                   @Value("${app.stats.k-anonymity:5}") int k) {
        this.cycles = cycles;
        this.iprzItems = iprzItems;
        this.users = users;
        this.auditLogs = auditLogs;
        this.alerts = alerts;
        this.audit = audit;
        this.clock = clock;
        this.k = Math.max(2, k);
    }

    public record AreaCount(String criterionCode, String criterionName, long actions, long employees) {
    }

    public record ActionCount(String actionTitle, long count) {
    }

    public record IprzSummary(int approvedPlans, List<AreaCount> byCriterion, Map<IprzItem.FormType, Long> byFormType,
                              List<ActionCount> topActions) {
    }

    /** Gotowy plan szkoleń: obszary rozwojowe z zatwierdzonych IPRZ (art. 108), od najczęstszych. */
    public IprzSummary iprzSummary(User user) {
        Access.requireRole(user, Role.HR, Role.DG);
        Map<Long, EvaluationCycle> approved = cycles.findByOfficeIdOrderByIdAsc(user.getOfficeId()).stream()
                .filter(c -> c.getIprzStatus() == EvaluationCycle.IprzStatus.APPROVED)
                .collect(Collectors.toMap(EvaluationCycle::getId, Function.identity()));
        List<IprzItem> items = approved.isEmpty() ? List.of() : iprzItems.findByCycleIdIn(approved.keySet());
        Map<String, String> names = new HashMap<>();
        approved.values().forEach(c -> criterionNames(c.getCriteriaJson(), names));

        Map<String, List<IprzItem>> byCode = items.stream()
                .collect(Collectors.groupingBy(i -> i.getCriterionCode() == null ? "" : i.getCriterionCode()));
        List<AreaCount> areas = byCode.entrySet().stream()
                .map(e -> new AreaCount(e.getKey().isEmpty() ? null : e.getKey(),
                        e.getKey().isEmpty() ? "(bez kryterium)" : names.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue().size(),
                        e.getValue().stream().map(i -> approved.get(i.getCycleId()).getEmployeeId()).distinct().count()))
                .sorted(Comparator.comparingLong(AreaCount::actions).reversed().thenComparing(AreaCount::criterionName))
                .toList();
        Map<IprzItem.FormType, Long> forms = new EnumMap<>(IprzItem.FormType.class);
        items.forEach(i -> forms.merge(i.getFormType(), 1L, Long::sum));
        List<ActionCount> top = items.stream()
                .collect(Collectors.groupingBy(i -> i.getActionTitle().trim(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new ActionCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(ActionCount::count).reversed().thenComparing(ActionCount::actionTitle))
                .limit(TOP_ACTIONS).toList();
        return new IprzSummary(approved.size(), areas, forms, top);
    }

    public record DepartmentCalibration(String department, long evaluations, BigDecimal averageScore,
                                        Map<PerformanceLevel, AnonymizedCount> levels,
                                        AnonymizedCount positive, AnonymizedCount negative) {
    }

    public record Calibration(int kAnonymity, Integer year, List<DepartmentCalibration> departments,
                              int suppressedDepartments, BigDecimal officeAverage) {
    }

    /**
     * Średnia i rozkład ocen per komórka organizacyjna. Komórki z mniej niż k ocenami są pomijane w całości (podaje się
     * tylko ich liczbę), a liczności 1..k-1 w rozkładach — ukrywane.
     */
    public Calibration calibration(User user, Integer year) {
        Access.requireRole(user, Role.HR, Role.DG);
        List<EvaluationCycle> evaluated = evaluated(user.getOfficeId(), year);
        Map<Long, User> people = users.findAllById(evaluated.stream().map(EvaluationCycle::getEmployeeId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String, List<EvaluationCycle>> byDepartment = evaluated.stream().collect(Collectors.groupingBy(c ->
                Optional.ofNullable(people.get(c.getEmployeeId())).map(User::getDepartment)
                        .filter(d -> !d.isBlank()).orElse(UNKNOWN_DEPARTMENT), TreeMap::new, Collectors.toList()));
        List<DepartmentCalibration> visible = new ArrayList<>();
        int suppressed = 0;
        for (Map.Entry<String, List<EvaluationCycle>> e : byDepartment.entrySet()) {
            List<EvaluationCycle> list = e.getValue();
            if (list.size() < k) {
                suppressed++;
                continue;
            }
            Map<PerformanceLevel, AnonymizedCount> levels = new EnumMap<>(PerformanceLevel.class);
            for (PerformanceLevel level : PerformanceLevel.values()) {
                levels.put(level, AnonymizedCount.of(list.stream().filter(c -> c.getPerformanceLevel() == level).count(), k));
            }
            visible.add(new DepartmentCalibration(e.getKey(), list.size(), average(list), levels,
                    AnonymizedCount.of(count(list, EvaluationOutcome.POSITIVE), k),
                    AnonymizedCount.of(count(list, EvaluationOutcome.NEGATIVE), k)));
        }
        return new Calibration(k, year, visible, suppressed, evaluated.size() >= k ? average(evaluated) : null);
    }

    /**
     * Zestawienie do rocznego Sprawozdania Szefa Służby Cywilnej (dane urzędu, liczby dokładne — sprawozdawczość
     * ustawowa na poziomie urzędu, bez danych osobowych). Sprzeciw „uwzględniony” = decyzja zmiany oceny lub ponownego
     * sporządzenia (art. 83 ust. 4).
     */
    @Transactional
    public String sscReportCsv(User user, Integer year) {
        Access.requireRole(user, Role.HR, Role.DG);
        int y = year != null ? year : LocalDate.now(clock).getYear();
        Long office = user.getOfficeId();
        List<EvaluationCycle> evaluated = evaluated(office, y);
        OffsetDateTime from = LocalDate.of(y, 1, 1).atStartOfDay(clock.getZone()).toOffsetDateTime();
        OffsetDateTime to = LocalDate.of(y + 1, 1, 1).atStartOfDay(clock.getZone()).toOffsetDateTime();
        long art71 = alerts.forOffice(office).stream()
                .filter(a -> a.type() == EmploymentAlertRules.Type.TWO_NEGATIVE_TERMINATION && a.since().getYear() == y)
                .count();
        List<Row> rows = List.of(
                exact("Rok sprawozdawczy", String.valueOf(y), y),
                exact("Oceny okresowe", "sporządzone", evaluated.size()),
                exact("Oceny okresowe", "pozytywne", count(evaluated, EvaluationOutcome.POSITIVE)),
                exact("Oceny okresowe", "negatywne", count(evaluated, EvaluationOutcome.NEGATIVE)),
                exact("Sprzeciwy (art. 83 ust. 1)", "wniesione", audited(office, "APPEAL_FILED", from, to)),
                exact("Sprzeciwy (art. 83 ust. 1)", "uwzględnione (zmiana / ponowne sporządzenie)",
                        audited(office, "APPEAL_DECIDED", from, to)),
                exact("Odwołania do sądu pracy (art. 83 ust. 5)", "zarejestrowane",
                        audited(office, "COURT_APPEAL_REGISTERED", from, to)),
                exact("Wnioski o kolejny stopień (art. 89)", "dołączone do ocen",
                        evaluated.stream().filter(ManagementReportService::gradeRequested).count()),
                exact("Art. 71 ust. 1 pkt 1", "dwie kolejne oceny negatywne urzędnika SC", art71));
        audit.log(user, office, "SSC_REPORT_EXPORTED", AuditService.USER, null, Map.of("year", y));
        return CsvReportRenderer.render(rows, k);
    }

    private List<EvaluationCycle> evaluated(Long officeId, Integer year) {
        return cycles.findByOfficeIdOrderByIdAsc(officeId).stream()
                .filter(c -> c.getFinalResult() != null && c.getEvaluatedOn() != null)
                .filter(c -> c.getStatus() != CycleStatus.EVALUATION_DRAFT)
                .filter(c -> year == null || c.getEvaluatedOn().getYear() == year)
                .toList();
    }

    private long audited(Long officeId, String action, OffsetDateTime from, OffsetDateTime to) {
        return auditLogs.countByOfficeIdAndActionAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(officeId, action, from, to);
    }

    private static Row exact(String section, String key, long value) {
        return new Row(section, key, new AnonymizedCount(value, false));
    }

    private static long count(List<EvaluationCycle> list, EvaluationOutcome outcome) {
        return list.stream().filter(c -> c.getFinalResult() == outcome).count();
    }

    private static BigDecimal average(List<EvaluationCycle> list) {
        List<BigDecimal> scores = list.stream().map(EvaluationCycle::getAverageScore).filter(Objects::nonNull).toList();
        if (scores.isEmpty()) {
            return null;
        }
        return scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP);
    }

    private static boolean gradeRequested(EvaluationCycle c) {
        return c.getGradeRequestJson() != null && c.getGradeRequestJson().path("requested").asBoolean(false);
    }

    private static void criterionNames(JsonNode criteria, Map<String, String> names) {
        if (criteria != null && criteria.isArray()) {
            criteria.forEach(c -> names.putIfAbsent(c.path("code").asText(), c.path("name").asText()));
        }
    }
}
