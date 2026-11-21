package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.domain.EvaluationCycle.IprzStatus;
import pl.gov.kprm.oceny.repository.FeedbackCheckinRepository;
import pl.gov.kprm.oceny.repository.IprzItemRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static pl.gov.kprm.oceny.service.Access.*;

/**
 * Część rozwojowa procesu: bieżąca informacja zwrotna (check-iny, Standardy ZZL) oraz strukturalny IPRZ z akceptacją
 * kierownictwa i zapoznaniem ocenianego (art. 108 ustawy o SC). Widoczność check-inów jak notatek (D4).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DevelopmentService {
    private final CycleWorkflowService workflow;
    private final FeedbackCheckinRepository checkins;
    private final IprzItemRepository iprzItems;
    private final AuditService audit;
    private final Clock clock;
    private final pl.gov.kprm.oceny.repository.EmployeeSelfPrepRepository selfPreps;

    public record SelfPrepInput(String achievements, String challenges, String developmentNeeds, boolean shared) {
    }

    /**
     * Oceniany widzi swoje przygotowanie; oceniający/zastępca — tylko udostępnione (inaczej pusty, nieudostępniony stan);
     * kadry i DG — brak dostępu.
     */
    @Transactional(readOnly = true)
    public EmployeeSelfPrep selfPrep(Long cycleId, User user) {
        EvaluationCycle cycle = workflow.get(cycleId, user);
        EmployeeSelfPrep prep = selfPreps.findById(cycle.getId()).orElse(null);
        if (user.getId().equals(cycle.getEmployeeId())) {
            return prep != null ? prep : empty(cycle.getId());
        }
        if (workflow.canActAsEvaluator(cycle, user) || user.getId().equals(cycle.getEvaluatorId())) {
            return prep != null && prep.isSharedWithEvaluator() ? prep : empty(cycle.getId());
        }
        throw forbidden("Przygotowanie do rozmowy widzi oceniany i — po udostępnieniu — przełożony");
    }

    public EmployeeSelfPrep saveSelfPrep(Long cycleId, User user, SelfPrepInput in) {
        EvaluationCycle cycle = workflow.asEmployee(cycleId, user);
        EmployeeSelfPrep prep = selfPreps.findById(cycle.getId()).orElseGet(() -> empty(cycle.getId()));
        prep.setAchievements(in.achievements());
        prep.setChallenges(in.challenges());
        prep.setDevelopmentNeeds(in.developmentNeeds());
        prep.setSharedWithEvaluator(in.shared());
        prep.setUpdatedAt(OffsetDateTime.now(clock));
        EmployeeSelfPrep saved = selfPreps.save(prep);
        // treść jest prywatna — w audycie wyłącznie fakt zapisu i udostępnienia
        audit.log(user, cycle.getOfficeId(), "SELF_PREP_SAVED", AuditService.CYCLE, cycle.getId(), Map.of("shared", in.shared()));
        return saved;
    }

    private static EmployeeSelfPrep empty(Long cycleId) {
        EmployeeSelfPrep p = new EmployeeSelfPrep();
        p.setCycleId(cycleId);
        return p;
    }

    public record CheckinInput(LocalDate conductedOn, String topic, String summary, String nextSteps,
                               boolean sharedWithEmployee) {
    }

    @Transactional(readOnly = true)
    public List<FeedbackCheckin> checkins(Long cycleId, User user) {
        EvaluationCycle cycle = workflow.get(cycleId, user);
        List<FeedbackCheckin> all = checkins.findByCycleIdOrderByConductedOnDescIdDesc(cycle.getId());
        if (workflow.canActAsEvaluator(cycle, user)) {
            return all;
        }
        if (user.getId().equals(cycle.getEmployeeId())) {
            return all.stream().filter(FeedbackCheckin::isSharedWithEmployee).toList();
        }
        throw forbidden("Check-iny widzi oceniający (zastępca) i — po udostępnieniu — oceniany");
    }

    public FeedbackCheckin addCheckin(Long cycleId, User user, CheckinInput in) {
        EvaluationCycle cycle = workflow.asEvaluator(cycleId, user);
        if (!cycle.getStatus().isActive()) {
            throw conflict("Cykl jest zakończony");
        }
        LocalDate date = in.conductedOn() != null ? in.conductedOn() : LocalDate.now(clock);
        if (date.isAfter(LocalDate.now(clock))) {
            throw conflict("Data rozmowy nie może być z przyszłości");
        }
        FeedbackCheckin c = new FeedbackCheckin();
        c.setCycleId(cycle.getId());
        c.setAuthorId(user.getId());
        c.setConductedOn(date);
        c.setTopic(in.topic());
        c.setSummary(in.summary());
        c.setNextSteps(in.nextSteps());
        c.setSharedWithEmployee(in.sharedWithEmployee());
        FeedbackCheckin saved = checkins.saveAndFlush(c);
        audit.log(user, cycle.getOfficeId(), "CHECKIN_ADDED", AuditService.CYCLE, cycle.getId(),
                workflow.auditPayload(user, cycle, Map.of("checkinId", saved.getId(), "shared", in.sharedWithEmployee())));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<IprzItem> iprzItems(Long cycleId, User user) {
        return iprzItems.findByCycleIdOrderByIdAsc(workflow.get(cycleId, user).getId());
    }

    public IprzItem addIprzItem(Long cycleId, User user, IprzItem item) {
        EvaluationCycle cycle = asIprzEditor(cycleId, user);
        if (cycle.getIprzStatus() == IprzStatus.APPROVED) {
            throw conflict("IPRZ jest zatwierdzony — zmiana zakresu wymaga korekty");
        }
        item.setId(null);
        item.setCycleId(cycle.getId());
        IprzItem saved = iprzItems.saveAndFlush(item);
        cycle.setIprzStatus(IprzStatus.PROPOSED);
        audit.log(user, cycle.getOfficeId(), "IPRZ_ITEM_ADDED", AuditService.CYCLE, cycle.getId(),
                workflow.auditPayload(user, cycle, Map.of("itemId", saved.getId(), "formType", saved.getFormType())));
        return saved;
    }

    /** Aktualizacja postępu (status) zawsze; treści — tylko przed zatwierdzeniem. */
    public IprzItem updateIprzItem(Long cycleId, Long itemId, User user, IprzItem changes) {
        EvaluationCycle cycle = asIprzEditor(cycleId, user);
        IprzItem item = item(cycle, itemId);
        boolean contentChange = changes.getActionTitle() != null || changes.getFormType() != null
                || changes.getTargetDate() != null || changes.getCriterionCode() != null;
        if (contentChange && cycle.getIprzStatus() == IprzStatus.APPROVED) {
            throw conflict("IPRZ jest zatwierdzony — można zmieniać tylko status realizacji");
        }
        if (changes.getActionTitle() != null) item.setActionTitle(changes.getActionTitle());
        if (changes.getFormType() != null) item.setFormType(changes.getFormType());
        if (changes.getTargetDate() != null) item.setTargetDate(changes.getTargetDate());
        if (changes.getCriterionCode() != null) item.setCriterionCode(changes.getCriterionCode());
        if (changes.getStatus() != null) item.setStatus(changes.getStatus());
        return item;
    }

    public void deleteIprzItem(Long cycleId, Long itemId, User user) {
        EvaluationCycle cycle = asIprzEditor(cycleId, user);
        if (cycle.getIprzStatus() == IprzStatus.APPROVED) {
            throw conflict("IPRZ jest zatwierdzony");
        }
        iprzItems.delete(item(cycle, itemId));
    }

    /** Art. 108: IPRZ zatwierdza dyrektor generalny (w urzędzie bez DG — kierownik urzędu, rola DG). */
    public EvaluationCycle approve(Long cycleId, User user) {
        EvaluationCycle cycle = workflow.load(cycleId);
        requireRole(user, Role.DG);
        requireSameOffice(user, cycle.getOfficeId());
        if (iprzItems.findByCycleIdOrderByIdAsc(cycle.getId()).isEmpty()) {
            throw conflict("IPRZ nie zawiera żadnych działań");
        }
        cycle.setIprzStatus(IprzStatus.APPROVED);
        cycle.setIprzApprovedBy(user.getId());
        cycle.setIprzApprovedAt(OffsetDateTime.now(clock));
        audit.log(user, cycle.getOfficeId(), "IPRZ_APPROVED", AuditService.CYCLE, cycle.getId(), Map.of());
        return cycle;
    }

    public EvaluationCycle acknowledge(Long cycleId, User user) {
        EvaluationCycle cycle = workflow.asEmployee(cycleId, user);
        if (cycle.getIprzStatus() != IprzStatus.APPROVED) {
            throw conflict("IPRZ nie został jeszcze zatwierdzony");
        }
        cycle.setIprzEmployeeAckAt(OffsetDateTime.now(clock));
        audit.log(user, cycle.getOfficeId(), "IPRZ_ACKNOWLEDGED", AuditService.CYCLE, cycle.getId(), Map.of());
        return cycle;
    }

    private EvaluationCycle asIprzEditor(Long cycleId, User user) {
        EvaluationCycle cycle = workflow.load(cycleId);
        boolean hr = user.getRole() == Role.HR && user.getOfficeId().equals(cycle.getOfficeId());
        if (!hr && !user.getId().equals(cycle.getEvaluatorId()) && !workflow.canActAsEvaluator(cycle, user)) {
            throw forbidden("IPRZ ustala oceniający (lub zastępca) albo kadry");
        }
        return cycle;
    }

    private IprzItem item(EvaluationCycle cycle, Long itemId) {
        return iprzItems.findById(itemId).filter(i -> i.getCycleId().equals(cycle.getId()))
                .orElseThrow(() -> notFound("Pozycja IPRZ", itemId));
    }
}
