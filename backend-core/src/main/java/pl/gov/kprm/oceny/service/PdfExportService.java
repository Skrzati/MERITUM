package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.pdf.GotenbergClient;
import pl.gov.kprm.oceny.pdf.SheetData;
import pl.gov.kprm.oceny.pdf.SheetHtmlRenderer;
import pl.gov.kprm.oceny.pdf.SheetParts;
import pl.gov.kprm.oceny.repository.IprzItemRepository;
import pl.gov.kprm.oceny.repository.OfficeRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.Criterion;
import pl.gov.kprm.oceny.rules.GradeRequest;

import java.util.List;
import java.util.Map;

import static pl.gov.kprm.oceny.service.Access.notFound;

@Service
@RequiredArgsConstructor
public class PdfExportService {
    private final CycleWorkflowService workflow;
    private final EvaluationHistoryService history;
    private final OfficeRepository offices;
    private final UserRepository users;
    private final SheetHtmlRenderer renderer;
    private final GotenbergClient gotenberg;
    private final AuditService audit;
    private final ObjectMapper json;
    private final IprzItemRepository iprzItems;

    @Transactional
    public byte[] export(Long cycleId, User user) {
        return export(cycleId, user, SheetParts.ALL);
    }

    @Transactional
    public byte[] export(Long cycleId, User user, SheetParts parts) {
        SheetData data = sheetData(workflow.get(cycleId, user));
        byte[] pdf = gotenberg.htmlToPdf(renderer.render(data, parts));
        audit.log(user, data.cycle().getOfficeId(), "PDF_EXPORTED", AuditService.CYCLE, cycleId,
                Map.of("sheetType", data.cycle().getSheetType(), "bytes", pdf.length, "parts", parts,
                        "includesGradeRequest", parts == SheetParts.ALL && data.includesGradeRequest()));
        return pdf;
    }

    private SheetData sheetData(EvaluationCycle c) {
        List<Criterion> criteria = c.getCriteriaJson() == null ? List.of()
                : json.convertValue(c.getCriteriaJson(), new TypeReference<>() {
                });
        Map<String, Integer> scores = c.getScoresJson() == null ? Map.of()
                : json.convertValue(c.getScoresJson(), new TypeReference<>() {
                });
        GradeRequest grade = c.getGradeRequestJson() == null ? null
                : json.convertValue(c.getGradeRequestJson(), GradeRequest.class);
        return new SheetData(
                offices.findById(c.getOfficeId()).orElseThrow(() -> notFound("Urząd", c.getOfficeId())),
                user(c.getEmployeeId()), user(c.getEvaluatorId()), c, criteria, scores, grade,
                history.previousEvaluation(c).orElse(null), iprzItems.findByCycleIdOrderByIdAsc(c.getId()));
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> notFound("Użytkownik", id));
    }
}
