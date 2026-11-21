package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Dtos.*;
import pl.gov.kprm.oceny.api.Requests.*;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.rules.Criterion;
import pl.gov.kprm.oceny.rules.EvaluationResult;
import pl.gov.kprm.oceny.service.CycleWorkflowService;
import pl.gov.kprm.oceny.service.CycleWorkflowService.BulkResult;
import pl.gov.kprm.oceny.service.CycleWorkflowService.CorrectionFields;
import pl.gov.kprm.oceny.service.CycleWorkflowService.DuePreview;
import pl.gov.kprm.oceny.service.CycleWorkflowService.HrFacts;
import pl.gov.kprm.oceny.domain.IprzItem;
import pl.gov.kprm.oceny.rules.RuleViolationException;
import pl.gov.kprm.oceny.service.DevelopmentService;
import pl.gov.kprm.oceny.service.EvaluationHistoryService;
import pl.gov.kprm.oceny.service.IntegrationExportService;
import pl.gov.kprm.oceny.service.Paging;
import pl.gov.kprm.oceny.service.IntegrationExportService.EzdPackage;
import pl.gov.kprm.oceny.service.PdfExportService;
import pl.gov.kprm.oceny.service.RescheduleService;
import pl.gov.kprm.oceny.service.ScopeChangeService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cycles")
@RequiredArgsConstructor
public class CycleController {
    private final CycleWorkflowService workflow;
    private final RescheduleService reschedules;
    private final ScopeChangeService scopeChanges;
    private final PdfExportService pdfExport;
    private final IntegrationExportService integrations;
    private final EvaluationHistoryService evaluationHistory;
    private final DevelopmentService development;
    private final CycleMapper mapper;

    @GetMapping
    public List<CycleDto> myCycles(@CurrentUser User user, @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer size) {
        return mapper.toDtos(workflow.visibleFor(user, Paging.of(page, size, Sort.unsorted())));
    }

    /** Nowy cykl — zawsze z najnowszą obowiązującą wersją schematu. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CycleDto create(@CurrentUser User user, @Valid @RequestBody CreateCycleRequest req) {
        return mapper.toDto(workflow.create(user, req.employeeId(), req.sheetType(), req.startDate(), req.reevaluation(),
                req.evaluatorId(), req.place()));
    }

    /** Masowe otwarcie cykli (HR / DG) — lista pracowników albo cała komórka; osoby z aktywnym cyklem są pomijane. */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public BulkCreateDto bulk(@CurrentUser User user, @Valid @RequestBody BulkCreateRequest req) {
        BulkResult result = workflow.createBulk(user, req.employeeIds(), req.department(), req.startDate());
        return new BulkCreateDto(mapper.toDtos(result.created()), result.skipped());
    }

    /** Podgląd terminu oceny, terminu kryteriów i arkusza przed utworzeniem cyklu. */
    @GetMapping("/due-preview")
    public DuePreview duePreview(@CurrentUser User user, @RequestParam Long employeeId,
                                 @RequestParam(required = false) LocalDate startDate,
                                 @RequestParam(defaultValue = "false") boolean reevaluation) {
        return workflow.previewDue(user, employeeId, startDate, reevaluation);
    }

    @PatchMapping("/{id}/details")
    public CycleDto updateDetails(@PathVariable Long id, @CurrentUser User user,
                                  @Valid @RequestBody CycleDetailsRequest req) {
        return mapper.toDto(workflow.updateDetails(id, user, req.place(), req.startDate(), req.dueDate(), req.reason()));
    }

    /** Tryb korekty: cofnięcie etapu lub korekta pól formalnych z uzasadnieniem (HR, DG — urząd; admin KPRM). */
    @PostMapping("/{id}/admin-correction")
    public CycleDto adminCorrection(@PathVariable Long id, @CurrentUser User user,
                                    @Valid @RequestBody AdminCorrectionRequest req) {
        return mapper.toDto(workflow.adminCorrection(id, user, req.targetStatus(), req.reason(),
                new CorrectionFields(req.criteriaInterviewDate(), req.criteriaConfirmedOn(), req.evaluationInterviewDate(),
                        req.evaluatedOn(), req.acknowledgedAt(), req.place(), req.startDate(), req.dueDate())));
    }

    @GetMapping("/{id}/checkins")
    public List<CheckinDto> checkins(@PathVariable Long id, @CurrentUser User user) {
        return development.checkins(id, user).stream().map(CheckinDto::of).toList();
    }

    /** Bieżąca informacja zwrotna (Standardy ZZL) — zapis: oceniający / zastępca; resetuje przypomnienie kwartalne. */
    @PostMapping("/{id}/checkins")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckinDto addCheckin(@PathVariable Long id, @CurrentUser User user, @Valid @RequestBody CheckinRequest req) {
        return CheckinDto.of(development.addCheckin(id, user, new DevelopmentService.CheckinInput(req.conductedOn(),
                req.topic(), req.summary(), req.nextSteps(), req.sharedWithEmployee())));
    }

    @GetMapping("/{id}/iprz-items")
    public List<IprzItemDto> iprzItems(@PathVariable Long id, @CurrentUser User user) {
        return development.iprzItems(id, user).stream().map(IprzItemDto::of).toList();
    }

    @PostMapping("/{id}/iprz-items")
    @ResponseStatus(HttpStatus.CREATED)
    public IprzItemDto addIprzItem(@PathVariable Long id, @CurrentUser User user, @Valid @RequestBody IprzItemRequest req) {
        if (req.actionTitle() == null || req.actionTitle().isBlank() || req.formType() == null) {
            throw new RuleViolationException(List.of("Działanie IPRZ wymaga nazwy i formy rozwoju"));
        }
        IprzItem item = req.toItem();
        if (item.getStatus() == null) item.setStatus(IprzItem.Status.PLANNED);
        return IprzItemDto.of(development.addIprzItem(id, user, item));
    }

    @PatchMapping("/{id}/iprz-items/{itemId}")
    public IprzItemDto updateIprzItem(@PathVariable Long id, @PathVariable Long itemId, @CurrentUser User user,
                                      @Valid @RequestBody IprzItemRequest req) {
        return IprzItemDto.of(development.updateIprzItem(id, itemId, user, req.toItem()));
    }

    @DeleteMapping("/{id}/iprz-items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIprzItem(@PathVariable Long id, @PathVariable Long itemId, @CurrentUser User user) {
        development.deleteIprzItem(id, itemId, user);
    }

    /** Art. 108: zatwierdzenie IPRZ przez DG / kierownika urzędu (audyt IPRZ_APPROVED). */
    @PostMapping("/{id}/iprz/approve")
    public CycleDto approveIprz(@PathVariable Long id, @CurrentUser User user) {
        return mapper.toDto(development.approve(id, user));
    }

    @PostMapping("/{id}/iprz/ack")
    public CycleDto acknowledgeIprz(@PathVariable Long id, @CurrentUser User user) {
        return mapper.toDto(development.acknowledge(id, user));
    }

    /** Historia ocen ocenianego (wgląd w historię ocen) — dla stron cyklu i kadr. */
    @GetMapping("/{id}/history")
    public List<HistoryDto> history(@PathVariable Long id, @CurrentUser User user) {
        return evaluationHistory.of(workflow.get(id, user).getEmployeeId()).stream().map(HistoryDto::of).toList();
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
                req.signatureMethod(), req.previousEvaluation());
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

    /** Zaproszenie iCalendar: rozmowa rozpoczynająca (DRAFT_CRITERIA, § 5 ust. 1) albo oceniająca (§ 10). */
    @GetMapping(value = "/{id}/interview.ics", produces = "text/calendar")
    public ResponseEntity<byte[]> interviewIcs(@PathVariable Long id, @CurrentUser User user) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("rozmowa-cykl-" + id + ".ics").build().toString())
                .body(integrations.interviewIcs(id, user).getBytes(StandardCharsets.UTF_8));
    }

    /** Metadane dokumentu do rejestracji w EZD RP (KRI). */
    @GetMapping("/{id}/ezd-package")
    public EzdPackage ezdPackage(@PathVariable Long id, @CurrentUser User user) {
        return integrations.ezdPackage(id, user);
    }

    /** Fakty policzone z bazy — kontekst dla HR Partnera bez zgadywania przez AI. */
    @GetMapping("/{id}/hr-facts")
    public HrFacts hrFacts(@PathVariable Long id, @CurrentUser User user) {
        return workflow.hrFacts(id, user);
    }
}
