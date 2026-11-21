package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.EvaluationCycleRepository;
import pl.gov.kprm.oceny.repository.EvaluationHistoryRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.EmploymentAlertRules;
import pl.gov.kprm.oceny.rules.EmploymentAlertRules.Alert;
import pl.gov.kprm.oceny.rules.HistoryEntry;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Alerty kadrowe z historii ocen i bieżącej (sporządzonej, jeszcze niezamkniętej) oceny: art. 71 ust. 1 pkt 1
 * (dwie negatywne u urzędnika SC), informacja dla pracownika SC, art. 89 ust. 2. Dane pobierane zbiorczo dla urzędu.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HrAlertService {
    private final UserRepository users;
    private final EvaluationHistoryRepository history;
    private final EvaluationCycleRepository cycles;

    public record HrAlert(Long employeeId, String employeeName, EmploymentStatus employmentStatus, Long cycleId,
                          EmploymentAlertRules.Type type, EmploymentAlertRules.Severity severity, String legalBasis,
                          String message, LocalDate since) {
    }

    public List<HrAlert> alerts(User user) {
        Access.requireRole(user, Role.HR, Role.DG);
        return forOffice(user.getOfficeId());
    }

    public List<HrAlert> forOffice(Long officeId) {
        return compute(users.findByOfficeIdAndActiveTrueOrderByIdAsc(officeId));
    }

    /** Alerty jednego pracownika — po zamknięciu cyklu (wysyłka e-mail o alercie krytycznym). */
    public List<HrAlert> forEmployee(User employee) {
        return compute(List.of(employee));
    }

    private List<HrAlert> compute(List<User> staff) {
        Set<Long> ids = staff.stream().map(User::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, List<HistoryEntry>> entries = new HashMap<>();
        history.findByUserIdInOrderByEvaluatedOnAsc(ids).forEach(h -> entries.computeIfAbsent(h.getUserId(),
                k -> new ArrayList<>()).add(new HistoryEntry(h.getOutcome(), h.getLevel(), h.getEvaluatedOn())));
        Map<Long, Long> latestCycle = new HashMap<>();
        for (EvaluationCycle c : cycles.findByEmployeeIdInOrderByIdAsc(ids)) {
            latestCycle.put(c.getEmployeeId(), c.getId());
            if (c.getStatus() == CycleStatus.AWAITING_ACK && c.getFinalResult() != null && c.getEvaluatedOn() != null) {
                // bieżąca ocena (sporządzona, przed zapoznaniem) jeszcze nie trafiła do historii
                entries.computeIfAbsent(c.getEmployeeId(), k -> new ArrayList<>())
                        .add(new HistoryEntry(c.getFinalResult(), c.getPerformanceLevel(), c.getEvaluatedOn()));
            }
        }
        List<HrAlert> result = new ArrayList<>();
        for (User u : staff) {
            for (Alert a : EmploymentAlertRules.evaluate(u.getEmploymentStatus(), u.getCivilServiceGrade(),
                    u.getLastGradeDate(), entries.getOrDefault(u.getId(), List.of()))) {
                result.add(new HrAlert(u.getId(), u.getFullName(), u.getEmploymentStatus(), latestCycle.get(u.getId()),
                        a.type(), a.severity(), a.legalBasis(), a.message(), a.since()));
            }
        }
        result.sort(Comparator.comparing(HrAlert::severity).thenComparing(HrAlert::since, Comparator.reverseOrder()));
        return result;
    }
}
