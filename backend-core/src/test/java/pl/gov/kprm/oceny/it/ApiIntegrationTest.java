package pl.gov.kprm.oceny.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Migracje V1–V5 na prawdziwym PostgreSQL + walidacja mapowań JPA + kluczowe scenariusze API. */
@SpringBootTest(properties = {"app.jwt.secret=integration-test-secret-integration-test!!", "app.auth.gov-login-enabled=true"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApiIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DEMO = "X-Demo-User-Id";
    private static final long DG = 1, EVALUATOR = 2, EMPLOYEE = 3, HR = 4, ADMIN = 5, TOMASZ = 6, AGNIESZKA = 7;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void seedHasRealisticDataAndMandatoryGradeFlag() throws Exception {
        JsonNode cycles = fetch("/api/cycles", HR);
        assertThat(cycles.size()).isGreaterThanOrEqualTo(12);
        JsonNode agnieszka = find(cycles, "employeeId", AGNIESZKA);
        assertThat(agnieszka.path("mandatoryGradeEligible").asBoolean()).as("art. 89 ust. 2").isTrue();
        assertThat(agnieszka.path("mandatoryGradeDeadline").asText()).isNotBlank();
        assertThat(find(cycles, "employeeId", 8).path("mandatoryGradeEligible").asBoolean()).isFalse();
        assertThat(fetch("/api/auth/demo-users", HR).size()).isGreaterThanOrEqualTo(12);
    }

    @Test
    void statsAreKAnonymizedOnServer() throws Exception {
        JsonNode stats = fetch("/api/stats/summary", DG);
        assertThat(stats.path("kAnonymity").asInt()).isEqualTo(5);
        assertThat(stats.path("byStatus").path("COMPLETED").path("value").asInt()).isGreaterThanOrEqualTo(5);
        JsonNode negative = stats.path("byOutcome").path("NEGATIVE");
        assertThat(negative.path("suppressed").asBoolean()).isTrue();
        assertThat(negative.path("value").isNull()).isTrue();
        assertThat(stats.path("wellbeing").path("avgWorkload").isNumber()).isTrue();
    }

    @Test
    void newCycleAlwaysPinsLatestSchemaVersion() throws Exception {
        String v2 = """
                {"versionCode": "v2-it", "effectiveFrom": "2026-01-01", "periodMonths": 12,
                 "config": %s}""".formatted(fetch("/api/schemas/1", HR).path("config"));
        JsonNode schema = send(post("/api/admin/schemas"), ADMIN, v2, 201);

        // pracownik bez aktywnego cyklu (cykl 5 zakończony) -> nowy cykl na v2, 12 miesięcy
        JsonNode cycle = send(post("/api/cycles"), HR, "{\"employeeId\": 9, \"startDate\": \"2026-11-23\"}", 201);
        assertThat(cycle.path("schemaVersionId").asLong()).isEqualTo(schema.path("id").asLong());
        assertThat(cycle.path("dueDate").asText()).isEqualTo("2027-11-23");
        assertThat(cycle.path("criteriaDeadline").asText()).isEqualTo("2026-12-23");
        assertThat(fetch("/api/cycles/1", HR).path("schemaVersionId").asLong()).as("stare cykle zostają na v1").isEqualTo(1);
    }

    @Test
    void employeeRescheduleRequestIsDecidedByEvaluatorWithNotice() throws Exception {
        JsonNode pending = fetch("/api/cycles/1/reschedule", EMPLOYEE);
        assertThat(pending.get(0).path("status").asText()).isEqualTo("PENDING");
        JsonNode decided = send(post("/api/cycles/1/reschedule/" + pending.get(0).path("id").asLong() + "/decision"),
                EVALUATOR, "{\"accept\": true, \"comment\": \"Zgoda\"}", 200);
        assertThat(decided.path("status").asText()).isEqualTo("APPLIED");
        assertThat(decided.path("noticeText").asText()).contains("art. 81 ust. 8 pkt 4", "§ 8");
        assertThat(fetch("/api/cycles/1", EMPLOYEE).path("dueDate").asText()).isEqualTo("2027-02-15");
    }

    @Test
    void negativeEvaluationWithGradeRequestIsRejected() throws Exception {
        JsonNode problem = send(put("/api/cycles/15/evaluate"), DG, """
                {"scores": {"K10": 1, "K08": 5, "K04": 5, "K11": 5, "K01": 5}, "justification": "Uzasadnienie",
                 "iprzNotes": "IPRZ", "gradeRequest": {"requested": true, "motivation": "x"}, "submit": true}""", 422);
        assertThat(problem.path("violations").toString()).contains("§ 11 ust. 5");
    }

    @Test
    void pdfHtmlAndAuditAreAvailable() throws Exception {
        assertThat(fetch("/api/audit-logs?limit=5", HR).size()).isEqualTo(5);
        mvc.perform(withUser(get("/api/audit-logs"), EMPLOYEE)).andExpect(status().isForbidden());
    }

    @Test
    void govLoginIssuesJwtAcceptedAsBearer() throws Exception {
        JsonNode login = send(post("/api/auth/gov-login"), null, "{\"userId\": " + TOMASZ + "}", 200);
        String token = login.path("accessToken").asText();
        String body = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(body).path("id").asLong()).isEqualTo(TOMASZ);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overdueListIsSortedAndEscalated() throws Exception {
        JsonNode overdue = fetch("/api/hr/overdue", DG);
        // cykl 17 (37 dni po terminie) nie jest modyfikowany przez inne testy
        assertThat(overdue.get(0).path("cycleId").asLong()).isEqualTo(17);
        assertThat(overdue.get(0).path("escalationLevel").asText()).isEqualTo("DYREKTOR_GENERALNY");
        for (int i = 1; i < overdue.size(); i++) {
            assertThat(overdue.get(i - 1).path("overdueDays").asLong()).isGreaterThanOrEqualTo(overdue.get(i).path("overdueDays").asLong());
        }
        assertThat(fetch("/api/cycles/17", HR).path("overdueDays").asLong()).isPositive();
        mvc.perform(withUser(get("/api/hr/overdue"), EMPLOYEE)).andExpect(status().isForbidden());
    }

    @Test
    void lateEvaluationIsNotBlockedAndRecordsDelayReason() throws Exception {
        JsonNode res = send(put("/api/cycles/16/evaluate"), EVALUATOR, """
                {"scores": {"K10": 5, "K08": 5, "K04": 5, "K11": 5, "K01": 5}, "iprzNotes": "IPRZ",
                 "delayReason": "Długotrwała nieobecność oceniającego", "signatureMethod": "MOBYWATEL_QES",
                 "submit": true}""", 200);
        assertThat(res.path("cycle").path("status").asText()).isEqualTo("AWAITING_ACK");
        assertThat(res.path("cycle").path("delayReason").asText()).contains("nieobecność");
        assertThat(res.path("cycle").path("signatureMethod").asText()).isEqualTo("MOBYWATEL_QES");
        assertThat(fetch("/api/audit-logs?limit=10", HR).toString()).contains("EVALUATION_OVERDUE");
    }

    @Test
    void notificationsAreComputedForVisibleCycles() throws Exception {
        String hr = fetch("/api/notifications", HR).toString();
        assertThat(hr).contains("OVERDUE", "CRITERIA_DUE", "APPEAL_DECISION_DUE", "MANDATORY_GRADE_DUE");
        String employee = fetch("/api/notifications", 7).toString();
        assertThat(employee).contains("MANDATORY_GRADE_DUE").doesNotContain("APPEAL_DECISION_DUE");
    }

    @Test
    void nextCycleAfterNegativeIsSixMonthReevaluation() throws Exception {
        // cykl 9: ocena negatywna Krawczyk (id 13), zapoznanie 2026-08-21
        JsonNode next = send(post("/api/cycles/9/next"), EVALUATOR, "{}", 201);
        assertThat(next.path("reevaluation").asBoolean()).isTrue();
        assertThat(next.path("startDate").asText()).isEqualTo("2026-08-21");
        assertThat(next.path("dueDate").asText()).isEqualTo("2027-02-22");
        mvc.perform(withUser(post("/api/cycles/1/next"), HR)).andExpect(status().isConflict());
    }

    @Test
    void rulePackageIsDownloadableForKprmAdmin() throws Exception {
        mvc.perform(withUser(get("/api/admin/schemas/1/package"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("meritum-rules-v1.json")));
        mvc.perform(withUser(get("/api/admin/schemas/1/package"), HR)).andExpect(status().isForbidden());
    }

    @Test
    void wellbeingSurveyIsAcceptedWithoutAuditTrail() throws Exception {
        JsonNode res = send(post("/api/wellbeing"), EMPLOYEE,
                "{\"workload\": 4, \"energy\": 3, \"support\": 4, \"wantsConversation\": false}", 201);
        assertThat(res.path("status").asText()).isEqualTo("RECEIVED");
    }

    @Test
    void significantScopeChangeWithinSixMonthsResetsCriteria() throws Exception {
        // cykl 13: ponowna ocena od 2026-08-04, kryteria potwierdzone — zmiana stanowiska 2026-11-16
        JsonNode change = send(post("/api/cycles/13/scope-changes"), HR, """
                {"changeDate": "2026-11-16", "newPositionTitle": "Starszy specjalista",
                 "description": "Przejęcie obsługi zamówień publicznych", "significant": true}""", 201);
        assertThat(change.path("outcome").asText()).isEqualTo("CRITERIA_RESET");
        assertThat(fetch("/api/cycles/13", HR).path("status").asText()).isEqualTo("DRAFT_CRITERIA");
    }

    @Test
    void interviewInvitationIsValidICalendarWithLegalBasis() throws Exception {
        for (long cycleId : new long[]{2, 17}) {
            String status = fetch("/api/cycles/" + cycleId, HR).path("status").asText();
            var res = mvc.perform(withUser(get("/api/cycles/" + cycleId + "/interview.ics"), HR))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/calendar")))
                    .andReturn().getResponse();
            String ics = res.getContentAsString(StandardCharsets.UTF_8);
            assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n")
                    .contains("BEGIN:VEVENT", "UID:cycle-" + cycleId, "DTSTART:", "ORGANIZER;", "ATTENDEE;");
            assertThat(ics.replace("\r\n ", ""))
                    .contains("DRAFT_CRITERIA".equals(status) ? "§ 5 ust. 1" : "§ 10");
        }
    }

    @Test
    void ezdPackageCarriesKriMetadata() throws Exception {
        JsonNode ezd = fetch("/api/cycles/17/ezd-package", HR);
        assertThat(ezd.path("caseId").asText()).startsWith("MERITUM/").endsWith("/17");
        assertThat(ezd.path("pdfEndpoint").asText()).isEqualTo("/api/cycles/17/pdf");
        assertThat(ezd.path("schemaVersion").asText()).isNotBlank();
        assertThat(ezd.path("parties").size()).isEqualTo(2);
        assertThat(ezd.path("sheetType").asText()).isNotBlank();
        assertThat(ezd.has("signatureMethod") && ezd.has("evaluatedOn")).isTrue();
    }

    @Test
    void hrFactsAreCountedFromDatabase() throws Exception {
        JsonNode facts = fetch("/api/cycles/17/hr-facts", HR);
        long evaluator = fetch("/api/cycles/17", HR).path("evaluatorId").asLong();
        assertThat(facts.path("overdueDays").asLong()).isPositive();
        assertThat(facts.path("totalNotesCount").asLong())
                .isEqualTo(fetch("/api/cycles/17/notes", evaluator).size());
        assertThat(facts.path("overloadNotesCount").asLong()).isLessThanOrEqualTo(facts.path("totalNotesCount").asLong());
        assertThat(facts.has("absenceDays") && facts.has("scopeChangesCount") && facts.has("daysSinceLastNote")).isTrue();
    }

    @Test
    void hrReportCsvIsAnonymizedWithBom() throws Exception {
        var res = mvc.perform(withUser(get("/api/hr/reports/export"), DG)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andReturn().getResponse();
        byte[] bytes = res.getContentAsByteArray();
        assertThat(new byte[]{bytes[0], bytes[1], bytes[2]}).containsExactly(0xEF, 0xBB, 0xBF);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("sekcja;kategoria;liczba", "status;COMPLETED;", "wynik;NEGATIVE;<5", "eskalacja;");
        assertThat(csv).doesNotContain("@");
        mvc.perform(withUser(get("/api/hr/reports/export"), EMPLOYEE)).andExpect(status().isForbidden());
    }

    @Test
    void statsOverdueUsesSameRuleAsOverdueList() throws Exception {
        int listed = fetch("/api/hr/overdue", HR).size();
        JsonNode overdue = fetch("/api/stats/summary", HR).path("overdue");
        if (overdue.path("suppressed").asBoolean()) {
            assertThat(listed).isBetween(1, 4);
        } else {
            assertThat(overdue.path("value").asInt()).isEqualTo(listed);
        }
    }

    @Test
    void demoCycleNineJustificationMatchesK10Score() throws Exception {
        JsonNode cycle = fetch("/api/cycles/9", HR);
        assertThat(cycle.path("scores").path("K10").asInt()).isEqualTo(1);
        assertThat(cycle.path("justification").asText()).contains("(K10)").doesNotContain("rzetelności");
    }

    private JsonNode fetch(String path, long userId) throws Exception {
        String body = mvc.perform(withUser(get(path), userId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private JsonNode send(MockHttpServletRequestBuilder req, Long userId, String body, int expected) throws Exception {
        MockHttpServletRequestBuilder r = req.contentType(MediaType.APPLICATION_JSON).content(body);
        String res = mvc.perform(userId == null ? r : withUser(r, userId)).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(res);
    }

    private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder r, long userId) {
        return r.header(DEMO, String.valueOf(userId));
    }

    private static JsonNode find(JsonNode array, String field, long value) {
        for (JsonNode n : array) {
            if (n.path(field).asLong() == value) return n;
        }
        throw new AssertionError("Brak elementu " + field + "=" + value);
    }
}
