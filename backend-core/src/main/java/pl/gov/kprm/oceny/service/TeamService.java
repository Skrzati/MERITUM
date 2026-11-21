package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.*;
import pl.gov.kprm.oceny.rules.CheckinRules;
import pl.gov.kprm.oceny.rules.HrFactsRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static pl.gov.kprm.oceny.service.Access.requireRole;

/**
 * „Mój zespół” — Wirtualny HR Partner na poziomie zespołu: mapa kompetencji z ostatnich ocen cząstkowych, sugestie
 * mentoringu wewnętrznego (deterministyczne), radar przeciążenia i momenty docenienia z twardych faktów w bazie.
 * Zespół = bezpośredni podwładni + pracownicy z aktywnych cykli zastępowanych oceniających. Zapytania zbiorcze.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamService {
    static final int MENTOR_MIN_SCORE = 7;
    static final int MENTEE_MAX_SCORE = 5;
    static final int MAX_MENTORING_PAIRS = 10;
    static final int ABSENCE_WINDOW_MONTHS = 6;
    private static final int SNIPPET_CHARS = 160;

    private final UserRepository users;
    private final EvaluationCycleRepository cycles;
    private final EmployeeNoteRepository notes;
    private final FeedbackCheckinRepository checkins;
    private final AbsenceRepository absences;
    private final ScopeChangeRepository scopeChanges;
    private final SubstitutionService substitutions;
    private final Clock clock;

    public record MemberScores(Long employeeId, String fullName, String positionTitle, Long cycleId,
                               Map<String, Integer> scores) {
    }

    public record CriterionAverage(String code, String name, BigDecimal average, int rated) {
    }

    public record MentoringPair(String criterionCode, String criterionName, Long mentorId, String mentorName,
                                int mentorScore, Long menteeId, String menteeName, int menteeScore) {
    }

    /** Twarde fakty dla HR Partnera (te same pola co {@code workload_signals}). */
    public record MemberSignals(Long employeeId, String fullName, Long activeCycleId, long absenceDays6m,
                                long scopeChangesCount, long overloadNotesCount, Long daysSinceLastActivity,
                                String appreciationSnippet) {
    }

    public record Overview(List<MemberScores> competencies, List<CriterionAverage> teamAverages,
                           List<MentoringPair> mentoring, List<MemberSignals> signals) {
    }

    public Overview overview(User user) {
        requireRole(user, Role.EVALUATOR, Role.DG);
        Map<Long, User> members = new LinkedHashMap<>();
        users.findBySupervisorIdAndActiveTrueOrderByFullNameAsc(user.getId()).forEach(u -> members.put(u.getId(), u));
        Set<Long> substituted = substitutions.substitutedBy(user.getId());
        if (!substituted.isEmpty()) {
            List<Long> extra = cycles.findByEvaluatorIdInOrderByIdAsc(substituted).stream()
                    .filter(c -> c.getStatus().isActive()).map(EvaluationCycle::getEmployeeId)
                    .filter(id -> !members.containsKey(id)).toList();
            users.findAllById(extra).stream().filter(User::isActive).forEach(u -> members.put(u.getId(), u));
        }
        if (members.isEmpty()) {
            return new Overview(List.of(), List.of(), List.of(), List.of());
        }
        Map<Long, List<EvaluationCycle>> cyclesByEmployee = cycles.findByEmployeeIdInOrderByIdAsc(members.keySet()).stream()
                .collect(Collectors.groupingBy(EvaluationCycle::getEmployeeId));
        Map<String, String> names = new TreeMap<>();
        List<MemberScores> competencies = new ArrayList<>();
        for (User m : members.values()) {
            Optional<EvaluationCycle> scored = cyclesByEmployee.getOrDefault(m.getId(), List.of()).stream()
                    .filter(c -> c.getScoresJson() != null && c.getScoresJson().size() > 0)
                    .max(Comparator.comparing(EvaluationCycle::getId));
            Map<String, Integer> scores = new TreeMap<>();
            scored.ifPresent(c -> {
                c.getScoresJson().fields().forEachRemaining(e -> scores.put(e.getKey(), e.getValue().asInt()));
                criteriaNames(c.getCriteriaJson()).forEach(names::putIfAbsent);
            });
            competencies.add(new MemberScores(m.getId(), m.getFullName(), m.getPositionTitle(),
                    scored.map(EvaluationCycle::getId).orElse(null), scores));
        }
        return new Overview(competencies, averages(competencies, names), mentoring(competencies, names),
                signals(members, cyclesByEmployee));
    }

    private static Map<String, String> criteriaNames(JsonNode criteria) {
        Map<String, String> out = new HashMap<>();
        if (criteria != null) criteria.forEach(c -> out.put(c.path("code").asText(), c.path("name").asText()));
        return out;
    }

    private static List<CriterionAverage> averages(List<MemberScores> members, Map<String, String> names) {
        Map<String, List<Integer>> byCode = new TreeMap<>();
        members.forEach(m -> m.scores().forEach((code, s) -> byCode.computeIfAbsent(code, k -> new ArrayList<>()).add(s)));
        return byCode.entrySet().stream().map(e -> new CriterionAverage(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()),
                BigDecimal.valueOf(e.getValue().stream().mapToInt(Integer::intValue).sum())
                        .divide(BigDecimal.valueOf(e.getValue().size()), 1, RoundingMode.HALF_UP), e.getValue().size())).toList();
    }

    /** Pary mentor (7–9 pkt) — mentee (≤ 5 pkt) w tym samym kryterium; mentorzy rozdzielani po kolei (round-robin). */
    static List<MentoringPair> mentoring(List<MemberScores> members, Map<String, String> names) {
        List<MentoringPair> pairs = new ArrayList<>();
        Set<String> codes = new TreeSet<>();
        members.forEach(m -> codes.addAll(m.scores().keySet()));
        for (String code : codes) {
            List<MemberScores> mentors = members.stream().filter(m -> m.scores().getOrDefault(code, 0) >= MENTOR_MIN_SCORE)
                    .sorted(Comparator.comparing((MemberScores m) -> -m.scores().get(code)).thenComparing(MemberScores::fullName))
                    .toList();
            List<MemberScores> mentees = members.stream()
                    .filter(m -> m.scores().containsKey(code) && m.scores().get(code) <= MENTEE_MAX_SCORE)
                    .sorted(Comparator.comparing((MemberScores m) -> m.scores().get(code)).thenComparing(MemberScores::fullName))
                    .toList();
            for (int i = 0; i < mentees.size() && !mentors.isEmpty(); i++) {
                MemberScores mentor = mentors.get(i % mentors.size());
                MemberScores mentee = mentees.get(i);
                pairs.add(new MentoringPair(code, names.getOrDefault(code, code), mentor.employeeId(), mentor.fullName(),
                        mentor.scores().get(code), mentee.employeeId(), mentee.fullName(), mentee.scores().get(code)));
            }
        }
        pairs.sort(Comparator.comparingInt(MentoringPair::menteeScore).thenComparing(MentoringPair::criterionCode));
        return pairs.size() > MAX_MENTORING_PAIRS ? pairs.subList(0, MAX_MENTORING_PAIRS) : pairs;
    }

    private List<MemberSignals> signals(Map<Long, User> members, Map<Long, List<EvaluationCycle>> cyclesByEmployee) {
        LocalDate today = LocalDate.now(clock);
        LocalDate windowStart = today.minusMonths(ABSENCE_WINDOW_MONTHS);
        Map<Long, EvaluationCycle> active = new HashMap<>();
        cyclesByEmployee.forEach((id, list) -> list.stream().filter(c -> c.getStatus().isActive())
                .max(Comparator.comparing(EvaluationCycle::getId)).ifPresent(c -> active.put(id, c)));
        List<Long> activeIds = active.values().stream().map(EvaluationCycle::getId).toList();
        Map<Long, List<EmployeeNote>> notesByCycle = activeIds.isEmpty() ? Map.of()
                : notes.findByCycleIdInOrderByCreatedAtAsc(activeIds).stream().collect(Collectors.groupingBy(EmployeeNote::getCycleId));
        Map<Long, LocalDate> lastCheckin = new HashMap<>();
        if (!activeIds.isEmpty()) {
            checkins.lastCheckinOn(activeIds).forEach(r -> lastCheckin.put((Long) r[0], (LocalDate) r[1]));
        }
        Map<Long, List<Absence>> absencesByUser = absences.findByUserIdIn(members.keySet()).stream()
                .collect(Collectors.groupingBy(Absence::getUserId));
        Map<Long, Long> scopeByUser = scopeChanges.findByUserIdIn(members.keySet()).stream()
                .collect(Collectors.groupingBy(ScopeChange::getUserId, Collectors.counting()));
        List<MemberSignals> out = new ArrayList<>();
        for (User m : members.values()) {
            EvaluationCycle c = active.get(m.getId());
            List<EmployeeNote> cycleNotes = c == null ? List.of() : notesByCycle.getOrDefault(c.getId(), List.of());
            long absenceDays = absencesByUser.getOrDefault(m.getId(), List.of()).stream()
                    .mapToLong(a -> HrFactsRules.absenceDaysWithin(a.getStartDate(), a.getEndDate(), windowStart, today)).sum();
            Long daysSince = null;
            if (c != null) {
                LocalDate lastNote = cycleNotes.isEmpty() ? null
                        : toDate(cycleNotes.get(cycleNotes.size() - 1).getCreatedAt());
                daysSince = CheckinRules.daysSince(CheckinRules.lastActivity(c.getStartDate(), c.getCriteriaConfirmedOn(),
                        lastNote, lastCheckin.get(c.getId())), today);
            }
            String appreciation = cycleNotes.stream().map(EmployeeNote::getContent).filter(HrFactsRules::signalsAppreciation)
                    .reduce((a, b) -> b).map(TeamService::snippet).orElse(null);
            out.add(new MemberSignals(m.getId(), m.getFullName(), c == null ? null : c.getId(), absenceDays,
                    scopeByUser.getOrDefault(m.getId(), 0L),
                    cycleNotes.stream().filter(n -> HrFactsRules.signalsOverload(n.getContent())).count(),
                    daysSince, appreciation));
        }
        return out;
    }

    private LocalDate toDate(OffsetDateTime t) {
        return t.atZoneSameInstant(clock.getZone()).toLocalDate();
    }

    private static String snippet(String text) {
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= SNIPPET_CHARS ? t : t.substring(0, SNIPPET_CHARS).trim() + "…";
    }
}
