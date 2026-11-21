package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiProxyServiceTest {
    @Test
    void auditPayloadKeepsOnlyMaskedPromptModeAndWarningCodes() throws Exception {
        var response = new ObjectMapper().readTree("""
                {"mode": "MOCK", "masked_prompt": "Oceniany: [OSOBA_1]", "draft_justification": "Katarzyna Wiśniewska ...",
                 "warnings": [{"code": "FOREIGN_NAME", "message": "Jan Kowalski"}]}
                """);

        var payload = AiProxyService.auditPayload("consistency-check", response, 4000);

        assertThat(payload).containsEntry("operation", "consistency-check").containsEntry("mode", "MOCK")
                .containsEntry("maskedPrompt", "Oceniany: [OSOBA_1]");
        assertThat(payload.get("warnings")).asList().containsExactly("FOREIGN_NAME");
        assertThat(payload.toString()).doesNotContain("Wiśniewska", "Jan Kowalski");
    }

    @Test
    void auditPromptIsTruncatedToConfiguredLength() throws Exception {
        String longPrompt = "x".repeat(10_000);
        var response = new ObjectMapper().readTree("{\"mode\": \"MOCK\", \"masked_prompt\": \"" + longPrompt + "\"}");

        String stored = (String) AiProxyService.auditPayload("socratic-draft", response, 4000).get("maskedPrompt");

        assertThat(stored).hasSize(4000 + AiProxyService.TRUNCATION_MARK.length()).endsWith(AiProxyService.TRUNCATION_MARK);
        assertThat(AiProxyService.truncate("krótki", 4000)).isEqualTo("krótki");
        assertThat(AiProxyService.truncate(null, 4000)).isNull();
    }
}
