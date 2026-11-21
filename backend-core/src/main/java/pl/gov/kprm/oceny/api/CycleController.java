package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Dtos.*;
import pl.gov.kprm.oceny.api.Requests.*;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.rules.Criterion;
import pl.gov.kprm.oceny.rules.EvaluationResult;
import pl.gov.kprm.oceny.service.CycleWorkflowService;
import pl.gov.kprm.oceny.service.PdfExportService;
import pl.gov.kprm.oceny.service.RescheduleService;
import pl.gov.kprm.oceny.service.ScopeChangeService;

import java.util.List;

@RestController
@RequestMapping("/api/cycles")
@RequiredArgsConstructor
public class CycleController {
    private final CycleWorkflowService workflow;
    private final RescheduleService reschedules;
    private final ScopeChangeService scopeChanges;
    private final PdfExportService pdfExport;
    private final CycleMapper mapper;

    @GetMapping
    public List<CycleDto> myCycles(@CurrentUser User user) {
        return workflow.visibleFor(user).stream().map(mapper::toDto).toList();
    }

    /** Nowy cykl — zawsze z najnowszą obowiązującą wersją schematu. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CycleDto create(@CurrentUser User user, @Valid @RequestBody CreateCycleRequest req) {
        return mapper.toDto(workflow.create(user, req.employeeId(), req.sheetType(), req.startDate(), req.reevaluation()));
    }

    @GetMapping("/{id}")
    public CycleDto get(@PathVariable Long id, @CurrentUser User user) {
        return mapper.toDto(workflow.get(id, user));
    }

    @PostMapping("/{id}/criteria")
    public CycleDto setCriteria(@PathVariable Long id, @CurrentUser User user,
                                @Valid @RequestBody SetCriteriaRequest req) {
        List<Criterion> additional = req.additional().stream()
                .map(c -> new Criterion(c.code(), c.name(), false)).toList();
        return mapper.toDto(workflow.setCriteria(id, user, additional, req.interviewDate()));
    }

    @PostMapping("/{id}/confirm-criteria")
    public CycleDto confirmCriteria(@PathVariable Long id, @CurrentUser User user) {
        return mapper.toDto(workflow.confirmCriteria(id, user));
    }

    @GetMapping("/{id}/notes")
    public List<NoteDto> notes(@PathVariable Long id, @CurrentUser User user) {
        return workflow.notes(id, user).stream().map(NoteDto::of).toList();
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteDto addNote(@PathVariable Long id, @CurrentUser User user, @Valid @RequestBody NoteRequest req) {
        return NoteDto.of(workflow.addNote(id, user, req.content(), req.isSharedAfterEval()));
    }

    @PutMapping("/{id}/evaluate")
    public EvaluationResponse evaluate(@PathVariable Long id, @CurrentUser User user,
                                       @Valid @RequestBody EvaluateRequest req) {
        EvaluationResult result = workflow.evaluate(id, user, req.scores(), req.justification(), req.iprzNotes(),
                req.gradeRequest(), req.evaluationInterviewDate(), req.submit(), req.delayReason(),
                req.signatureMethod());
        return new EvaluationResponse(mapper.toDto(workflow.get(id, user)), result);
    }

    @PostMapping("/{id}/acknowledge")
    public CycleDto acknowledge(@PathVariable Long id, @CurrentUser User user,
                                @RequestBody(required = false) AcknowledgeRequest req) {
        return mapper.toDto(workflow.acknowledge(id, user, req == null ? null : req.signatureMethod()));
    }

    /** Kolejny cykl po zakończonej ocenie: po negatywnej ponowna ocena (6 mies.), po pozytywnej zwykły cykl. */
    @PostMapping("/{id}/next")
    @ResponseStatus(HttpStatus.CREATED)
    public CycleDto next(@PathVariable Long id, @CurrentUser User user) {
        return mapper.toDto(workflow.next(id, user));
    }

    @PostMapping("/{id}/refusal")
    public CycleDto refusal(@PathVariable Long id, @CurrentUser User user, @Valid @RequestBody RefusalRequest req) {
        return mapper.toDto(workflow.registerRefusal(id, user, req.reason(), req.date()));
    }

    @PostMapping("/{id}/appeal")
    public CycleDto appeal(@PathVariable Long id, @CurrentUser User user, @Valid @RequestBody AppealRequest req) {
        return mapper.toDto(workflow.appeal(id, user, req.content()));
    }

    @PostMapping("/{id}/appeal/decision")
    public CycleDto decideAppeal(@PathVariable Long id, @CurrentUser User user,
                                 @Valid @RequestBody AppealDecisionRequest req) {
        return mapper.toDto(workflow.decideAppeal(id, user, req.decision(), req.comment(), req.scores(),
                req.justification(), req.iprzNotes()));
    }

    @GetMapping("/{id}/reschedule")
    public List<RescheduleDto> reschedules(@PathVariable Long id, @CurrentUser User user) {
        return reschedules.list(id, user).stream().map(RescheduleDto::of).toList();
    }

    /** Art. 81 ust. 8: pkt 1–3 — oceniający (od razu + zawiadomienie § 8); pkt 4 — wniosek ocenianego. */
    @PostMapping("/{id}/reschedule")
    @ResponseStatus(HttpStatus.CREATED)
    public RescheduleDto reschedule(@PathVariable Long id, @CurrentUser User user,
                                    @Valid @RequestBody RescheduleBody req) {
        return RescheduleDto.of(reschedules.reschedule(id, user, req.mode(), req.newDueDate(), req.reason()));
    }

    @PostMapping("/{id}/reschedule/{requestId}/decision")
    public RescheduleDto decideReschedule(@PathVariable Long id, @PathVariable Long requestId, @CurrentUser User user,
                                          @Valid @RequestBody RescheduleDecision req) {
        return RescheduleDto.of(reschedules.decide(id, requestId, user, req.accept(), req.comment()));
    }

    @GetMapping("/{id}/scope-changes")
    public List<ScopeChangeDto> scopeChanges(@PathVariable Long id, @CurrentUser User user) {
        return scopeChanges.list(id, user).stream().map(ScopeChangeDto::of).toList();
    }

    @PostMapping("/{id}/scope-changes")
    @ResponseStatus(HttpStatus.CREATED)
    public ScopeChangeDto recordScopeChange(@PathVariable Long id, @CurrentUser User user,
                                            @Valid @RequestBody ScopeChangeRequest req) {
        return ScopeChangeDto.of(scopeChanges.record(id, user, req.changeDate(), req.newPositionTitle(),
                req.description(), req.significant()));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @CurrentUser User user) {
        byte[] pdf = pdfExport.export(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("arkusz-oceny-" + id + ".pdf").build().toString())
                .body(pdf);
    }
}
