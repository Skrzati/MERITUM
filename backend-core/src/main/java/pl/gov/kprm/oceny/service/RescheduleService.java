package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.RescheduleRequestRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.DeadlineService;
import pl.gov.kprm.oceny.rules.RescheduleRules;
import pl.gov.kprm.oceny.rules.RuleViolationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static pl.gov.kprm.oceny.service.Access.*;

/** Zmiana terminu oceny: art. 81 ust. 8 (4 przesłanki) + § 8 (pisemne zawiadomienie ocenianego). */
@Service
@Transactional
@RequiredArgsConstructor
public class RescheduleService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CycleWorkflowService workflow;
    private final RescheduleRequestRepository requests;
    private final EvaluationHistoryService history;
    private final UserRepository users;
    private final DeadlineService deadlines;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RescheduleRequest> list(Long cycleId, User user) {
        return requests.findByCycleIdOrderByCreatedAtAsc(workflow.get(cycleId, user).getId());
    }

    /** Pkt 1–3: decyzja oceniającego stosowana od razu; pkt 4: wniosek ocenianego oczekujący na decyzję. */
    public RescheduleRequest reschedule(Long cycleId, User user, RescheduleMode mode, LocalDate newDueDate, String reason) {
        EvaluationCycle cycle = mode == RescheduleMode.EMPLOYEE_REQUEST
                ? workflow.asEmployee(cycleId, user) : workflow.asEvaluator(cycleId, user);
        requireActive(cycle);
        checkStatute(cycle, newDueDate);

        RescheduleRequest request = new RescheduleRequest();
        request.setCycleId(cycle.getId());
        request.setMode(mode);
        request.setReason(reason);
        request.setPreviousDueDate(cycle.getDueDate());
        request.setProposedDueDate(newDueDate);
        request.setRequestedBy(user.getId());
        if (mode == RescheduleMode.EMPLOYEE_REQUEST) {
            request.setStatus(RescheduleStatus.PENDING);
            RescheduleRequest saved = requests.saveAndFlush(request);
            audit.log(user, cycle.getOfficeId(), "RESCHEDULE_REQUESTED", AuditService.CYCLE, cycle.getId(),
                    Map.of("requestId", saved.getId(), "proposedDueDate", newDueDate));
            return saved;
        }
        apply(cycle, request, user);
        return requests.saveAndFlush(request);
    }

    public RescheduleRequest decide(Long cycleId, Long requestId, User user, boolean accept, String comment) {
        EvaluationCycle cycle = workflow.asEvaluator(cycleId, user);
        RescheduleRequest request = requests.findById(requestId)
                .filter(r -> r.getCycleId().equals(cycle.getId()))
                .orElseThrow(() -> notFound("Wniosek o zmianę terminu", requestId));
        if (request.getStatus() != RescheduleStatus.PENDING) {
            throw conflict("Wniosek został już rozpatrzony");
        }
        request.setDecisionComment(comment);
        if (!accept) {
            request.setStatus(RescheduleStatus.REJECTED);
            request.setDecidedBy(user.getId());
            request.setDecidedAt(OffsetDateTime.now(clock));
            audit.log(user, cycle.getOfficeId(), "RESCHEDULE_REJECTED", AuditService.CYCLE, cycle.getId(),
                    workflow.auditPayload(user, cycle, Map.of("requestId", requestId)));
            return request;
        }
        requireActive(cycle);
        checkStatute(cycle, request.getProposedDueDate());
        apply(cycle, request, user);
        return request;
    }

    private void apply(EvaluationCycle cycle, RescheduleRequest request, User decidedBy) {
        LocalDate newDue = deadlines.shiftToWorkingDay(request.getProposedDueDate());
        request.setPreviousDueDate(cycle.getDueDate());
        request.setNewDueDate(newDue);
        request.setStatus(RescheduleStatus.APPLIED);
        request.setDecidedBy(decidedBy.getId());
        request.setDecidedAt(OffsetDateTime.now(clock));
        request.setNoticeText(notice(cycle, request, newDue));
        cycle.setDueDate(newDue);
        audit.log(decidedBy, cycle.getOfficeId(), "DUE_DATE_RESCHEDULED", AuditService.CYCLE, cycle.getId(),
                workflow.auditPayload(decidedBy, cycle, Map.of("mode", request.getMode(),
                        "statutePoint", request.getMode().statutePoint(), "from", request.getPreviousDueDate(), "to", newDue)));
    }

    /** § 8: pisemne zawiadomienie ocenianego o nowym terminie; kopię dołącza się do arkusza. */
    private String notice(EvaluationCycle cycle, RescheduleRequest request, LocalDate newDue) {
        String employee = users.findById(cycle.getEmployeeId()).map(User::getFullName).orElse("");
        return "ZAWIADOMIENIE O ZMIANIE TERMINU SPORZĄDZENIA OCENY OKRESOWEJ\n\n"
                + "Na podstawie art. 81 ust. 8 pkt " + request.getMode().statutePoint()
                + " ustawy z dnia 21 listopada 2008 r. o służbie cywilnej oraz § 8 rozporządzenia Prezesa Rady Ministrów "
                + "z dnia 4 kwietnia 2016 r. (Dz. U. poz. 470) zawiadamiam Panią/Pana " + employee
                + ", że termin sporządzenia oceny okresowej na piśmie został zmieniony z dnia "
                + DATE.format(request.getPreviousDueDate()) + " na dzień " + DATE.format(newDue) + ".\n"
                + "Przyczyna: " + request.getReason() + "\n\nKopię zawiadomienia dołącza się do arkusza oceny.";
    }

    private void checkStatute(EvaluationCycle cycle, LocalDate newDueDate) {
        boolean firstEvaluation = !history.hasEvaluationBefore(cycle.getEmployeeId(), cycle.getStartDate());
        RescheduleRules.violation(firstEvaluation, cycle.getStartDate(), LocalDate.now(clock), newDueDate)
                .ifPresent(v -> {
                    throw new RuleViolationException(List.of(v));
                });
    }

    private static void requireActive(EvaluationCycle cycle) {
        if (!cycle.getStatus().isActive() || cycle.getStatus() == CycleStatus.AWAITING_ACK) {
            throw conflict("Termin można zmienić wyłącznie przed sporządzeniem oceny");
        }
    }
}
