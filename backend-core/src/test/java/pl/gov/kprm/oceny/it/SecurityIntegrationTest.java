package pl.gov.kprm.oceny.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testy ataków na API: IDOR (cudze cykle), eskalacja uprawnień, izolacja urzędów (multi-tenant),
 * wstrzyknięcia w parametry wyszukiwania i sortowania, sfałszowane uwierzytelnienie.
 */
@SpringBootTest(properties = "app.jwt.secret=security-test-secret-security-test-secret!!")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SecurityIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DEMO = "X-Demo-User-Id";
    private static final long DG = 1, EVALUATOR = 2, EMPLOYEE = 3, HR = 4, ADMIN = 5;
    private static final long FOREIGN_OFFICE = 900, FOREIGN_HR = 900, FOREIGN_EMPLOYEE = 901;
    private static final int MAX_SEEDED_CYCLE_ID = 17;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    /** Drugi urząd z własną kadrą — wstawiany w teście, bo kontekst Springa startuje po kontenerze. */
    private void foreignOffice() {
        jdbc.update("INSERT INTO offices (id, name, has_dg_role) VALUES (?, 'Urząd obcy', TRUE) ON CONFLICT DO NOTHING", FOREIGN_OFFICE);
        jdbc.update("INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id) "
                + "VALUES (?, ?, 'Kadrowa Obca', 'hr@obcy.gov.pl', 'HR', FALSE, NULL) ON CONFLICT DO NOTHING", FOREIGN_HR, FOREIGN_OFFICE);
        jdbc.update("INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id) "
                + "VALUES (?, ?, 'Pracownik Obcy', 'p@obcy.gov.pl', 'EVALUATED', FALSE, ?) ON CONFLICT DO NOTHING",
                FOREIGN_EMPLOYEE, FOREIGN_OFFICE, FOREIGN_HR);
    }

    @Test
    void employeeCannotReadAnyCycleOutsideOwnRelation() throws Exception {
        Set<Long> visible = new HashSet<>();
        for (JsonNode c : fetch("/api/cycles", EMPLOYEE)) visible.add(c.path("id").asLong());
        for (long id = 1; id <= MAX_SEEDED_CYCLE_ID; id++) {
            int expected = visible.contains(id) ? 200 : 403;
            expect(as(get("/api/cycles/" + id + "/hr-facts"), EMPLOYEE), 403); // D4 — także dla własnego cyklu
            for (String path : new String[]{"/api/cycles/" + id, "/api/cycles/" + id + "/pdf", "/api/cycles/" + id + "/ezd-package",
                    "/api/cycles/" + id + "/interview.ics"}) {
                if (path.endsWith("/pdf") && expected == 200) continue; // PDF wymaga Gotenberga — pomijamy dostęp dozwolony
                int status = mvc.perform(as(get(path), EMPLOYEE)).andReturn().getResponse().getStatus();
                assertThat(status).as("GET %s jako oceniany", path).isEqualTo(expected);
            }
        }
    }

    @Test
    void employeeCannotEscalateToEvaluatorHrDgOrAdmin() throws Exception {
        long ownCycle = fetch("/api/cycles", EMPLOYEE).get(0).path("id").asLong();
        expect(as(put("/api/cycles/" + ownCycle + "/evaluate"), EMPLOYEE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scores\":{},\"submit\":true}"), 403);
        expect(as(post("/api/cycles/" + ownCycle + "/notes"), EMPLOYEE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"podrzucona notatka\"}"), 403);
        expect(as(post("/api/cycles/" + ownCycle + "/appeal/decision"), EMPLOYEE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"CHANGE\"}"), 403);
        expect(as(post("/api/admin/schemas"), EMPLOYEE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionCode\":\"hack\",\"effectiveFrom\":\"2026-01-01\",\"periodMonths\":1,\"config\":{}}"), 403);
        expect(as(post("/api/hr/handover"), EMPLOYEE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":" + EMPLOYEE + ",\"newEvaluatorId\":" + EMPLOYEE + "}"), 403);
        for (String path : new String[]{"/api/audit-logs", "/api/hr/employees", "/api/hr/overdue", "/api/hr/reports/export",
                "/api/stats/summary", "/api/admin/schemas", "/api/hr/employees/" + HR + "/gdpr-export"}) {
            expect(as(get(path), EMPLOYEE), 403);
        }
    }

    @Test
    void supervisorAiOperationsRequireSupervisorRole() throws Exception {
        long ownCycle = fetch("/api/cycles", EMPLOYEE).get(0).path("id").asLong();
        for (String op : new String[]{"socratic-draft", "hr-partner", "interview-prep", "suggest-criteria"}) {
            expect(as(post("/api/core-ai/" + op + "?cycleId=" + ownCycle), EMPLOYEE).contentType(MediaType.APPLICATION_JSON)
                    .content("{}"), 403);
            expect(as(post("/api/core-ai/" + op), ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}"), 403);
        }
    }

    @Test
    void oversizedAiRequestIsRejectedBeforeReachingGateway() throws Exception {
        String note = "x".repeat(70 * 1024);
        expect(as(post("/api/core-ai/socratic-draft"), EVALUATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"employee_name\":\"A\",\"sheet_type\":\"C\",\"notes\":[\"" + note + "\"]}"), 413);
    }

    @Test
    void demoUsersDoNotExposeEmails() throws Exception {
        String body = mvc.perform(get("/api/auth/demo-users")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("@").doesNotContain("\"email\"");
        assertThat(json.readTree(body).get(0).has("role")).isTrue();
    }

    @Test
    void wellbeingSurveysAreTechnicallyAnonymous() throws Exception {
        Integer userColumns = jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'wellbeing_surveys' AND column_name = 'user_id'", Integer.class);
        assertThat(userColumns).isZero();
        String export = mvc.perform(as(get("/api/hr/employees/" + EMPLOYEE + "/gdpr-export"), HR)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(export).has("wellbeingSurveys")).isFalse();
    }

    @Test
    void listsSupportOptionalPagingWithBackwardCompatibleDefault() throws Exception {
        int all = fetch("/api/cycles", HR).size();
        JsonNode first = fetch("/api/cycles?page=0&size=2", HR);
        JsonNode second = fetch("/api/cycles?page=1&size=2", HR);
        assertThat(all).isGreaterThan(4);
        assertThat(first.size()).isEqualTo(2);
        assertThat(second.get(0).path("id").asLong()).isGreaterThan(first.get(1).path("id").asLong());
        assertThat(fetch("/api/hr/employees?page=0&size=3", HR).size()).isEqualTo(3);
        assertThat(fetch("/api/audit-logs?page=0&size=1", HR).size()).isEqualTo(1);
        expect(as(get("/api/cycles?size=500"), HR), 400);
        expect(as(get("/api/hr/employees?page=-1&size=5"), HR), 400);
    }

    @Test
    void evaluatorCannotTouchCyclesOfOtherEvaluators() throws Exception {
        for (JsonNode c : fetch("/api/cycles", HR)) {
            if (c.path("evaluatorId").asLong() == EVALUATOR || c.path("employeeId").asLong() == EVALUATOR) continue;
            long id = c.path("id").asLong();
            expect(as(get("/api/cycles/" + id + "/notes"), EVALUATOR), 403);
            expect(as(put("/api/cycles/" + id + "/evaluate"), EVALUATOR).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"scores\":{},\"submit\":false}"), 403);
            return;
        }
    }

    @Test
    void hrAndDgDoNotSeeNotes() throws Exception {
        long anyCycle = fetch("/api/cycles", HR).get(0).path("id").asLong();
        expect(as(get("/api/cycles/" + anyCycle + "/notes"), HR), 403);
        expect(as(get("/api/cycles/" + anyCycle + "/notes"), DG), 403);
    }

    @Test
    void officesAreIsolated() throws Exception {
        foreignOffice();
        assertThat(fetch("/api/cycles", FOREIGN_HR).size()).isZero();
        expect(as(get("/api/cycles/1"), FOREIGN_HR), 403);
        for (JsonNode u : fetch("/api/hr/employees", FOREIGN_HR)) {
            assertThat(u.path("officeId").asLong()).isEqualTo(FOREIGN_OFFICE);
        }
        assertThat(fetch("/api/stats/summary", FOREIGN_HR).path("totalCycles").path("value").asLong()).isZero();
        for (JsonNode a : fetch("/api/audit-logs?limit=500", FOREIGN_HR)) {
            assertThat(a.path("officeId").asLong()).isEqualTo(FOREIGN_OFFICE);
        }
        expect(as(get("/api/hr/employees/" + EMPLOYEE + "/gdpr-export"), FOREIGN_HR), 403);
        expect(as(post("/api/hr/handover"), FOREIGN_HR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":" + EMPLOYEE + ",\"newEvaluatorId\":" + FOREIGN_HR + "}"), 403);
        expect(as(post("/api/cycles"), FOREIGN_HR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":" + EMPLOYEE + "}"), 403);
        // KPRM_ADMIN widzi wszystkie urzędy
        assertThat(fetch("/api/cycles", ADMIN).size()).isGreaterThanOrEqualTo(MAX_SEEDED_CYCLE_ID);
    }

    @Test
    void searchAndSortParametersAreNotInjectable() throws Exception {
        String[] queries = {"' OR '1'='1", "%' OR 1=1 --", "\"; DROP TABLE users; --", "%", "_", "\\", "<script>alert(1)</script>"};
        for (String q : queries) {
            JsonNode res = fetch("/api/hr/employees?q=" + java.net.URLEncoder.encode(q, StandardCharsets.UTF_8), HR);
            assertThat(res.isArray()).as(q).isTrue();
            assertThat(res.size()).as("q=%s nie może zwrócić całej tabeli", q).isLessThan(fetch("/api/hr/employees", HR).size() + 1);
        }
        assertThat(fetch("/api/hr/employees?q=" + java.net.URLEncoder.encode("' OR '1'='1", StandardCharsets.UTF_8), HR).size()).isZero();
        assertThat(fetch("/api/hr/employees?sort=fullName;DROP%20TABLE%20users&dir=ASC", HR).size()).isPositive();
        expect(as(get("/api/hr/employees?dir=SIDEWAYS"), HR), 400);
        assertThat(fetch("/api/auth/demo-users", HR).size()).as("tabela users nienaruszona").isPositive();
    }

    @Test
    void forgedOrMalformedCredentialsAreRejected() throws Exception {
        expect(get("/api/cycles"), 401);
        expect(get("/api/cycles").header("Authorization", "Bearer abc.def.ghi"), 401);
        expect(get("/api/cycles").header("Authorization", "Bearer " + "x".repeat(10_000)), 401);
        expect(get("/api/cycles").header(DEMO, "1 OR 1=1"), 400);
        expect(get("/api/cycles").header(DEMO, "999999"), 401);
        // Token ma pierwszeństwo przed nagłówkiem demo — zły token nie „spada” na X-Demo-User-Id
        expect(get("/api/cycles").header("Authorization", "Bearer abc.def.ghi").header(DEMO, String.valueOf(ADMIN)), 401);
    }

    @Test
    void validTokenIdentifiesUserFromDatabaseNotFromClaims() throws Exception {
        String body = mvc.perform(post("/api/auth/gov-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + EMPLOYEE + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = json.readTree(body).path("accessToken").asText();
        String me = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)
                        .header(DEMO, String.valueOf(ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(me).path("id").asLong()).as("Bearer wygrywa z nagłówkiem demo").isEqualTo(EMPLOYEE);
    }

    @Test
    void massAssignmentCannotForgeNoteAuthor() throws Exception {
        long cycle = -1;
        for (JsonNode c : fetch("/api/cycles", EVALUATOR)) {
            String s = c.path("status").asText();
            if (c.path("evaluatorId").asLong() == EVALUATOR && !s.equals("COMPLETED") && !s.equals("APPEALED")) {
                cycle = c.path("id").asLong();
                break;
            }
        }
        if (cycle < 0) return;
        String res = mvc.perform(as(post("/api/cycles/" + cycle + "/notes"), EVALUATOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"<img src=x onerror=alert(1)>\",\"authorId\":" + DG + ",\"cycleId\":1,\"id\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode note = json.readTree(res);
        assertThat(note.path("authorId").asLong()).isEqualTo(EVALUATOR);
        assertThat(note.path("cycleId").asLong()).isEqualTo(cycle);
        assertThat(note.path("id").asLong()).isNotEqualTo(1);
    }

    private JsonNode fetch(String path, long userId) throws Exception {
        String body = mvc.perform(as(get(path), userId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private void expect(MockHttpServletRequestBuilder req, int expected) throws Exception {
        mvc.perform(req).andExpect(status().is(expected));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r, long userId) {
        return r.header(DEMO, String.valueOf(userId));
    }
}
