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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"appreciation-nudge", "team-mentoring"})
    void newHrPartnerOperationsAreProxiedAndAudited(String operation) throws Exception {
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var audit = org.mockito.Mockito.mock(AuditService.class);
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://ai/api/ai/" + operation))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess("{\"mode\": \"MOCK\", \"masked_prompt\": \"[OSOBA_1]\", "
                                        + "\"complianceMeta\": {\"advisoryOnly\": true}}",
                                org.springframework.http.MediaType.APPLICATION_JSON));
        var service = new AiProxyService(builder, "http://ai", audit, org.mockito.Mockito.mock(CycleWorkflowService.class),
                org.mockito.Mockito.mock(pl.gov.kprm.oceny.repository.UserRepository.class), 65536, 4000);
        var evaluator = new pl.gov.kprm.oceny.domain.User();
        evaluator.setId(2L);
        evaluator.setOfficeId(1L);
        evaluator.setRole(pl.gov.kprm.oceny.domain.Role.EVALUATOR);

        var response = service.call(operation, new ObjectMapper().readTree("{}"), evaluator, null);

        server.verify();
        assertThat(response.path("complianceMeta").path("advisoryOnly").asBoolean()).isTrue();
        org.mockito.Mockito.verify(audit).log(org.mockito.ArgumentMatchers.eq(evaluator), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("AI_" + operation.toUpperCase().replace('-', '_')),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"UNPROCESSABLE_ENTITY, 422", "BAD_REQUEST, 422", "PAYLOAD_TOO_LARGE, 413",
            "INTERNAL_SERVER_ERROR, 503"})
    void gatewayErrorsAreMappedToMeaningfulStatus(org.springframework.http.HttpStatus gatewayStatus, int expected) {
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://ai/api/ai/consistency-check"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(gatewayStatus));
        var service = new AiProxyService(builder, "http://ai", org.mockito.Mockito.mock(AuditService.class),
                org.mockito.Mockito.mock(CycleWorkflowService.class),
                org.mockito.Mockito.mock(pl.gov.kprm.oceny.repository.UserRepository.class), 65536, 4000);
        var employee = new pl.gov.kprm.oceny.domain.User();
        employee.setOfficeId(1L);
        employee.setRole(pl.gov.kprm.oceny.domain.Role.EVALUATED);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.call("consistency-check", new ObjectMapper().createObjectNode(), employee, null))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(expected));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"appreciation-nudge", "team-mentoring"})
    void newHrPartnerOperationsAreSupervisorOnly(String operation) {
        var service = new AiProxyService(org.springframework.web.client.RestClient.builder(), "http://ai",
                org.mockito.Mockito.mock(AuditService.class), org.mockito.Mockito.mock(CycleWorkflowService.class),
                org.mockito.Mockito.mock(pl.gov.kprm.oceny.repository.UserRepository.class), 65536, 4000);
        var employee = new pl.gov.kprm.oceny.domain.User();
        employee.setOfficeId(1L);
        employee.setRole(pl.gov.kprm.oceny.domain.Role.EVALUATED);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.call(operation, null, employee, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403");
    }
}
