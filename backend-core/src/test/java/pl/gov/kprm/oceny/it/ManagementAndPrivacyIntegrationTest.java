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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Oczekiwania dla kryteriów, trzy tryby podpisu, łączenie ról w małym urzędzie, prywatny dziennik pracownika
 * i raporty zarządcze (plan szkoleń z IPRZ, kalibracja z k-anonimizacją, sprawozdanie Szefa SC).
 */
@SpringBootTest(properties = "app.jwt.secret=management-test-secret-management-test!!")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ManagementAndPrivacyIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DEMO = "X-Demo-User-Id";
    private static final long DG = 1, EVALUATOR = 2, EMPLOYEE = 3, HR = 4, ADMIN = 5;
    private static final long SMALL_HEAD = 17, AWAITING_ACK_CYCLE = 12, AWAITING_ACK_EMPLOYEE = 15;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void criteriaExpectationsAreStoredWithCriteriaAndReturned() throws Exception {
        JsonNode cycle = send(post("/api/cycles/2/criteria"), DG, "{\"additional\": []}", 200);
        String code = cycle.path("criteria").get(0).path("code").asText();
        JsonNode withExpectations = send(post("/api/cycles/2/criteria"), DG, """
                {"additional": [], "expectations": {"%s": "Terminowo przygotowuje analizy bez poprawek merytorycznych"}}
                """.formatted(code), 200);
        assertThat(withExpectations.path("criteriaExpectations").path(code).asText()).contains("Terminowo");
        assertThat(fetch("/api/cycles/2", HR).path("criteriaExpectations").path(code).isTextual()).isTrue();
        send(post("/api/cycles/2/criteria"), DG, "{\"additional\": [], \"expectations\": {\"K99\": \"x\"}}", 422);
    }

    @Test
    void hybridPaperSignatureRequiresHandSignedDateAndStoresVerificationCode() throws Exception {
        String url = "/api/cycles/" + AWAITING_ACK_CYCLE + "/acknowledge";
        send(post(url), AWAITING_ACK_EMPLOYEE, "{\"signatureMethod\": \"HYBRID_PAPER_SCAN\"}", 422);
        JsonNode signed = send(post(url), AWAITING_ACK_EMPLOYEE, """
                {"signatureMethod": "HYBRID_PAPER_SCAN", "paperSignedOn": "%s", "scanReference": "EZD/2026/77"}
                """.formatted(LocalDate.now()), 200);
        assertThat(signed.path("signatureHash").asText()).hasSize(64);
        assertThat(signed.path("signatureVerificationCode").asText()).matches("[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}");
        assertThat(signed.path("scanReference").asText()).isEqualTo("EZD/2026/77");
        assertThat(signed.path("signedAt").isNull()).isFalse();

        JsonNode ack = null;
        for (JsonNode a : fetch("/api/audit-logs?limit=50", HR)) {
            if (a.path("action").asText().equals("EVALUATION_ACKNOWLEDGED") && a.path("entityId").asLong() == AWAITING_ACK_CYCLE) {
                ack = a;
            }
        }
        assertThat(ack).isNotNull();
        assertThat(ack.path("payload").path("sha256").asText()).isEqualTo(signed.path("signatureHash").asText());
        assertThat(ack.path("payload").path("signatureMode").asText()).isEqualTo("HYBRID_PAPER_SCAN");
    }

    @Test
    void smallOfficeHeadCombinesDirectorAndHrRoles() throws Exception {
        expectStatus(get("/api/hr/employees"), SMALL_HEAD, 403);
        send(patch("/api/admin/accounts/" + SMALL_HEAD + "/roles"), ADMIN, "{\"secondaryRoles\": [\"KPRM_ADMIN\"]}", 403);
        JsonNode account = send(patch("/api/admin/accounts/" + SMALL_HEAD + "/roles"), ADMIN,
                "{\"secondaryRoles\": [\"HR\", \"EVALUATOR\", \"DG\"]}", 200);
        assertThat(account.path("user").path("secondaryRoles").toString()).contains("HR", "EVALUATOR").doesNotContain("DG");

        JsonNode staff = fetch("/api/hr/employees", SMALL_HEAD);
        assertThat(staff.size()).isPositive();
        for (JsonNode u : staff) {
            assertThat(u.path("officeId").asLong()).as("nadal tylko własny urząd").isEqualTo(2);
        }
        expectStatus(get("/api/hr/employees"), 19, 403);
    }

    @Test
    void privateJournalIsVisibleOnlyToItsOwner() throws Exception {
        JsonNode note = send(post("/api/private-notes"), EMPLOYEE,
                "{\"cycleId\": 1, \"content\": \"Moje przemyślenia przed rozmową\"}", 201);
        long id = note.path("id").asLong();
        assertThat(fetch("/api/private-notes?cycleId=1", EMPLOYEE).size()).isEqualTo(1);

        for (long other : new long[]{EVALUATOR, HR, DG, ADMIN}) {
            assertThat(fetch("/api/private-notes", other).toString()).doesNotContain("przemyślenia");
            send(put("/api/private-notes/" + id), other, "{\"content\": \"zmiana\"}", 403);
            expectStatus(delete("/api/private-notes/" + id), other, 403);
        }
        send(post("/api/private-notes"), EMPLOYEE, "{\"cycleId\": 5, \"content\": \"cudzy cykl\"}", 403);
        assertThat(send(put("/api/private-notes/" + id), EMPLOYEE, "{\"content\": \"Poprawione\"}", 200)
                .path("content").asText()).isEqualTo("Poprawione");
        assertThat(fetch("/api/cycles/1/ezd-package", HR).path("excludedFromArchive").toString()).contains("EmployeePrivateNote");
        assertThat(fetch("/api/hr/employees/" + EMPLOYEE + "/gdpr-export", HR).toString()).doesNotContain("Poprawione");
        expectStatus(delete("/api/private-notes/" + id), EMPLOYEE, 204);
    }

    @Test
    void managementReportsAggregateWithKAnonymity() throws Exception {
        JsonNode iprz = fetch("/api/hr/iprz-summary", HR);
        assertThat(iprz.has("approvedPlans")).isTrue();
        assertThat(iprz.path("byCriterion").isArray()).isTrue();
        expectStatus(get("/api/hr/iprz-summary"), EMPLOYEE, 403);

        JsonNode calibration = fetch("/api/stats/calibration", DG);
        int k = calibration.path("kAnonymity").asInt();
        assertThat(calibration.path("departments").size() + calibration.path("suppressedDepartments").asInt()).isPositive();
        for (JsonNode d : calibration.path("departments")) {
            assertThat(d.path("evaluations").asInt()).isGreaterThanOrEqualTo(k);
            d.path("levels").forEach(l -> assertThat(l.path("suppressed").asBoolean() || l.path("value").asLong() == 0
                    || l.path("value").asLong() >= k).isTrue());
        }
        expectStatus(get("/api/stats/calibration"), EVALUATOR, 403);

        String csv = mvc.perform(get("/api/hr/export/ssc-report.csv?year=2026").header(DEMO, String.valueOf(HR)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿sekcja;kategoria;liczba")
                .contains("Oceny okresowe;negatywne;", "Sprzeciwy (art. 83 ust. 1);wniesione;",
                        "Wnioski o kolejny stopień (art. 89);dołączone do ocen;",
                        "Art. 71 ust. 1 pkt 1;dwie kolejne oceny negatywne urzędnika SC;1");
        expectStatus(get("/api/hr/export/ssc-report.csv"), EMPLOYEE, 403);
    }

    private JsonNode fetch(String path, long userId) throws Exception {
        String body = mvc.perform(get(path).header(DEMO, String.valueOf(userId))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private JsonNode send(MockHttpServletRequestBuilder req, long userId, String body, int expected) throws Exception {
        String res = mvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(body).header(DEMO, String.valueOf(userId)))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return res.isBlank() ? json.nullNode() : json.readTree(res);
    }

    private void expectStatus(MockHttpServletRequestBuilder req, long userId, int expected) throws Exception {
        mvc.perform(req.header(DEMO, String.valueOf(userId))).andExpect(status().is(expected));
    }
}
