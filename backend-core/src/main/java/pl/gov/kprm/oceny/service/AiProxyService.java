package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pośrednik do ai-gateway. Każda interakcja z AI jest rejestrowana w audit_logs (AI Act art. 26 — rejestrowanie
 * zdarzeń przez podmiot stosujący): kto, jaka operacja, tryb LLM i prompt po maskowaniu PII (nigdy surowe dane).
 */
@Service
public class AiProxyService {
    public static final Set<String> OPERATIONS = Set.of("socratic-draft", "consistency-check", "hr-partner", "interview-prep",
            "suggest-criteria", "appreciation-nudge", "team-mentoring");
    /** Operacje wspierające przełożonego — niedostępne dla ocenianego i administratora KPRM. */
    public static final Set<String> SUPERVISOR_OPERATIONS = Set.of("socratic-draft", "hr-partner", "interview-prep",
            "suggest-criteria", "appreciation-nudge", "team-mentoring");
    private static final Role[] SUPERVISOR_ROLES = {Role.EVALUATOR, Role.DG, Role.HR};
    static final String TRUNCATION_MARK = "…[przycięto]";
    static final String SUGGEST_CRITERIA = "suggest-criteria";

    private final RestClient http;
    private final String baseUrl;
    private final AuditService audit;
    private final CycleWorkflowService workflow;
    private final UserRepository users;
    private final int maxRequestBytes;
    private final int auditPromptMaxChars;

    public AiProxyService(RestClient.Builder builder, @Value("${app.ai-gateway-url}") String baseUrl,
                          AuditService audit, CycleWorkflowService workflow, UserRepository users,
                          @Value("${app.ai.max-request-bytes:65536}") int maxRequestBytes,
                          @Value("${app.ai.audit-prompt-max-chars:4000}") int auditPromptMaxChars) {
        this.http = builder.build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.audit = audit;
        this.workflow = workflow;
        this.users = users;
        this.maxRequestBytes = maxRequestBytes;
        this.auditPromptMaxChars = auditPromptMaxChars;
    }

    @Transactional
    public JsonNode call(String operation, JsonNode body, User user, Long cycleId) {
        if (!OPERATIONS.contains(operation)) {
            throw Access.notFound("Operacja AI", operation);
        }
        if (SUPERVISOR_OPERATIONS.contains(operation)) {
            Access.requireRole(user, SUPERVISOR_ROLES);
        }
        if (body != null && body.toString().getBytes(StandardCharsets.UTF_8).length > maxRequestBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Żądanie do AI przekracza limit " + maxRequestBytes / 1024 + " KB");
        }
        EvaluationCycle cycle = cycleId == null ? null : workflow.get(cycleId, user);
        if (SUGGEST_CRITERIA.equals(operation) && cycle != null && body instanceof ObjectNode request) {
            // art. 84: opis stanowiska z profilu pracownika (kadry), a nie z formularza przeglądarki
            users.findById(cycle.getEmployeeId()).map(User::getJobDescription)
                    .filter(d -> !d.isBlank()).ifPresent(d -> request.put("job_description", d));
        }
        JsonNode response;
        try {
            response = http.post().uri(baseUrl + "/api/ai/" + operation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Usługa AI (ai-gateway) niedostępna", e);
        }
        audit.log(user, cycle != null ? cycle.getOfficeId() : user.getOfficeId(), "AI_" + operation.toUpperCase().replace('-', '_'),
                cycle != null ? AuditService.CYCLE : AuditService.AI, cycle != null ? cycle.getId() : null,
                auditPayload(operation, response, auditPromptMaxChars));
        return response;
    }

    static Map<String, Object> auditPayload(String operation, JsonNode response, int promptMaxChars) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", operation);
        payload.put("mode", response == null ? null : response.path("mode").asText(null));
        payload.put("maskedPrompt", truncate(response == null ? null : response.path("masked_prompt").asText(null),
                promptMaxChars));
        if (response != null && response.has("warnings")) {
            List<String> codes = new ArrayList<>();
            response.path("warnings").forEach(w -> codes.add(w.path("code").asText()));
            payload.put("warnings", codes);
        }
        return payload;
    }

    /** Audyt przechowuje początek promptu — pełny prompt o rozmiarze żądania nie powiela się w JSONB audit_logs. */
    static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + TRUNCATION_MARK;
    }
}
