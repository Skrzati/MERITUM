package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.User;

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
    public static final Set<String> OPERATIONS = Set.of("socratic-draft", "consistency-check", "hr-partner", "interview-prep");

    private final RestClient http;
    private final String baseUrl;
    private final AuditService audit;
    private final CycleWorkflowService workflow;

    public AiProxyService(RestClient.Builder builder, @Value("${app.ai-gateway-url}") String baseUrl,
                          AuditService audit, CycleWorkflowService workflow) {
        this.http = builder.build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.audit = audit;
        this.workflow = workflow;
    }

    @Transactional
    public JsonNode call(String operation, JsonNode body, User user, Long cycleId) {
        if (!OPERATIONS.contains(operation)) {
            throw Access.notFound("Operacja AI", operation);
        }
        EvaluationCycle cycle = cycleId == null ? null : workflow.get(cycleId, user);
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
                auditPayload(operation, response));
        return response;
    }

    static Map<String, Object> auditPayload(String operation, JsonNode response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", operation);
        payload.put("mode", response == null ? null : response.path("mode").asText(null));
        payload.put("maskedPrompt", response == null ? null : response.path("masked_prompt").asText(null));
        if (response != null && response.has("warnings")) {
            List<String> codes = new ArrayList<>();
            response.path("warnings").forEach(w -> codes.add(w.path("code").asText()));
            payload.put("warnings", codes);
        }
        return payload;
    }
}
