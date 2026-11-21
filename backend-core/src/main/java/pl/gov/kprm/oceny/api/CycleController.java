package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Dtos.CycleDto;
import pl.gov.kprm.oceny.api.Dtos.EvaluationResponse;
import pl.gov.kprm.oceny.api.Dtos.NoteDto;
import pl.gov.kprm.oceny.api.Requests.*;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.rules.Criterion;
import pl.gov.kprm.oceny.rules.EvaluationResult;
import pl.gov.kprm.oceny.service.CycleWorkflowService;
import pl.gov.kprm.oceny.service.PdfExportService;

import java.util.List;

@RestController
@RequestMapping("/api/cycles")
@RequiredArgsConstructor
public class CycleController {
    private final CycleWorkflowService workflow;
    private final PdfExportService pdfExport;

    @GetMapping
    public List<CycleDto> myCycles(@CurrentUser User user) {
        return workflow.visibleFor(user).stream().map(CycleDto::of).toList();
    }

    @GetMapping("/{id}")
    public CycleDto get(@PathVariable Long id, @CurrentUser User user) {
        return CycleDto.of(workflow.get(id, user));
    }

    @PostMapping("/{id}/criteria")
    public CycleDto setCriteria(@PathVariable Long id, @CurrentUser User user,
                                @Valid @RequestBody SetCriteriaRequest req) {
        List<Criterion> additional = req.additional().stream()
                .map(c -> new Criterion(c.code(), c.name(), false)).toList();
        return CycleDto.of(workflow.setCriteria(id, user, additional));
    }

    @PostMapping("/{id}/confirm-criteria")
    public CycleDto confirmCriteria(@PathVariable Long id, @CurrentUser User user) {
        return CycleDto.of(workflow.confirmCriteria(id, user));
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
                req.gradeRequest(), req.submit());
        return new EvaluationResponse(CycleDto.of(workflow.get(id, user)), result);
    }

    @PostMapping("/{id}/acknowledge")
    public CycleDto acknowledge(@PathVariable Long id, @CurrentUser User user) {
        return CycleDto.of(workflow.acknowledge(id, user));
    }

    @PostMapping("/{id}/refusal")
    public CycleDto refusal(@PathVariable Long id, @CurrentUser User user, @Valid @RequestBody RefusalRequest req) {
        return CycleDto.of(workflow.registerRefusal(id, user, req.reason(), req.date()));
    }

    @PostMapping("/{id}/appeal")
    public CycleDto appeal(@PathVariable Long id, @CurrentUser User user, @Valid @RequestBody AppealRequest req) {
        return CycleDto.of(workflow.appeal(id, user, req.content()));
    }

    @PostMapping("/{id}/appeal/decision")
    public CycleDto decideAppeal(@PathVariable Long id, @CurrentUser User user,
                                 @Valid @RequestBody AppealDecisionRequest req) {
        EvaluationCycle cycle = workflow.decideAppeal(id, user, req.decision(), req.comment(), req.scores(),
                req.justification(), req.iprzNotes());
        return CycleDto.of(cycle);
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
