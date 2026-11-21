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

    @Test
    void suggestCriteriaGetsJobDescriptionFromEmployeeProfile() throws Exception {
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var workflow = org.mockito.Mockito.mock(CycleWorkflowService.class);
        var users = org.mockito.Mockito.mock(pl.gov.kprm.oceny.repository.UserRepository.class);
        var cycle = new pl.gov.kprm.oceny.domain.EvaluationCycle();
        cycle.setId(9L);
        cycle.setOfficeId(1L);
        cycle.setEmployeeId(13L);
        var employee = new pl.gov.kprm.oceny.domain.User();
        employee.setJobDescription("Opis stanowiska z kadr (art. 84)");
        var evaluator = new pl.gov.kprm.oceny.domain.User();
        evaluator.setId(2L);
        evaluator.setOfficeId(1L);
        evaluator.setRole(pl.gov.kprm.oceny.domain.Role.EVALUATOR);
        org.mockito.Mockito.when(workflow.get(9L, evaluator)).thenReturn(cycle);
        org.mockito.Mockito.when(users.findById(13L)).thenReturn(java.util.Optional.of(employee));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://ai/api/ai/suggest-criteria"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .jsonPath("$.job_description").value("Opis stanowiska z kadr (art. 84)"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess("{\"mode\": \"MOCK\", \"masked_prompt\": \"x\"}",
                                org.springframework.http.MediaType.APPLICATION_JSON));
        var service = new AiProxyService(builder, "http://ai", org.mockito.Mockito.mock(AuditService.class), workflow,
                users, 65536, 4000);

        var body = new ObjectMapper().readTree("{\"position_title\": \"Referent\", \"job_description\": \"z przeglądarki\"}");
        service.call("suggest-criteria", body, evaluator, 9L);

        server.verify();
    }
}
