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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Etap 7 — domknięcie przypadków użycia KPRM: przypisywanie cykli (pojedynczo i grupowo), przypomnienia kwartalne i
 * procesowe, mały urząd bez DG, zastępstwa, edycja/anonimizacja w kadrach, historia ocen, ochrona zmiany terminu,
 * pola formularza i tryb korekty. Każdy test używa innych cykli/osób z seedu (kolejność testów dowolna).
 */
@SpringBootTest(properties = "app.jwt.secret=process-test-secret-process-test-secret!!")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProcessCompletionIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DEMO = "X-Demo-User-Id";
    private static final long DG = 1, EVALUATOR = 2, EMPLOYEE = 3, HR = 4, ADMIN = 5, TOMASZ = 6;
    private static final long SMALL_HEAD = 17, SMALL_EVALUATOR = 18, SMALL_CYCLE = 18;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void smallOfficeWithoutDirectorGeneralUsesHeadOfOffice() throws Exception {
        JsonNode cycle = fetch("/api/cycles/" + SMALL_CYCLE, SMALL_EVALUATOR);
        assertThat(cycle.path("officeHasDgRole").asBoolean()).isFalse();
        assertThat(cycle.path("appealAuthority").asText()).isEqualTo("Kierownik urzędu");
        assertThat(fetch("/api/cycles/1", HR).path("appealAuthority").asText()).isEqualTo("Dyrektor Generalny");
        JsonNode head = find(fetch("/api/auth/demo-users", HR), "id", SMALL_HEAD);
        assertThat(head.path("officeHasDgRole").asBoolean()).isFalse();
        assertThat(head.path("officeCity").asText()).isEqualTo("Płock");
        // izolacja urzędów nadal obowiązuje
        expect(as(get("/api/cycles/" + SMALL_CYCLE), HR), 403);
    }

    @Test
    void quarterlyCheckinReminderAfterNinetyDaysWithoutNotes() throws Exception {
        JsonNode reminder = notification(SMALL_EVALUATOR, "QUARTERLY_CHECKIN", SMALL_CYCLE);
        assertThat(reminder).as("ostatnia notatka 15.07.2025").isNotNull();
        assertThat(reminder.path("severity").asText()).isEqualTo("HIGH");
        assertThat(notification(SMALL_HEAD, "QUARTERLY_CHECKIN", SMALL_CYCLE)).as("tylko dla oceniającego").isNull();
    }

    @Test
    void pendingEmployeeRescheduleRequestNotifiesEvaluator() throws Exception {
        boolean pending = false;
        for (JsonNode r : fetch("/api/cycles/1/reschedule", EVALUATOR)) pending |= "PENDING".equals(r.path("status").asText());
        assertThat(pending).as("seed: wniosek ocenianego w cyklu 1").isTrue();
        assertThat(notification(EVALUATOR, "RESCHEDULE_PENDING", 1)).isNotNull();
        assertThat(notification(EMPLOYEE, "RESCHEDULE_PENDING", 1)).isNull();
    }

    @Test
    void gradeRequestAndAppealDecisionNotifications() throws Exception {
        jdbc.update("UPDATE evaluation_cycles SET evaluated_on = ? WHERE id = 7", LocalDate.now().minusDays(1));
        assertThat(notification(HR, "GRADE_REQUEST_SUBMITTED", 7)).isNotNull();
        assertThat(notification(TOMASZ, "GRADE_REQUEST_SUBMITTED", 7)).as("dla kadr i kierownictwa").isNull();

        send(post("/api/cycles/14/appeal/decision"), DG, "{\"decision\": \"REDO\", \"comment\": \"Ponowne sporządzenie\"}", 200);
        assertThat(notification(16, "APPEAL_DECIDED", 14)).as("oceniany").isNotNull();
        JsonNode forEvaluator = notification(EVALUATOR, "APPEAL_DECIDED", 14);
        assertThat(forEvaluator).isNotNull();
        assertThat(forEvaluator.path("message").asText()).contains("Dyrektor Generalny", "ponowne sporządzenie");
    }

    @Test
    void assignSingleCycleWithEvaluatorPlaceAndDuePreview() throws Exception {
        JsonNode preview = fetch("/api/cycles/due-preview?employeeId=13&startDate=2026-12-01", HR);
        assertThat(preview.path("dueDate").asText()).startsWith("2028-12");
        assertThat(preview.path("periodMonths").asInt()).isEqualTo(24);
        assertThat(preview.path("defaultEvaluatorId").asLong()).isEqualTo(EVALUATOR);
        assertThat(preview.path("defaultPlace").asText()).isEqualTo("Warszawa");

        JsonNode created = send(post("/api/cycles"), HR, """
                {"employeeId": 13, "sheetType": "B", "startDate": "2026-12-01", "evaluatorId": %d, "place": "Kraków"}
                """.formatted(TOMASZ), 201);
        assertThat(created.path("evaluatorId").asLong()).isEqualTo(TOMASZ);
        assertThat(created.path("place").asText()).isEqualTo("Kraków");
        assertThat(created.path("sheetType").asText()).isEqualTo("B");
        assertThat(created.path("dueDate").asText()).isEqualTo(preview.path("dueDate").asText());

        send(post("/api/cycles"), HR, "{\"employeeId\": 13}", 409); // aktywny cykl już istnieje
        send(post("/api/cycles"), HR, "{\"employeeId\": 11, \"evaluatorId\": " + EMPLOYEE + "}", 409); // oceniany nie ocenia
    }

    @Test
    void bulkOpeningSkipsEmployeesWithActiveCycle() throws Exception {
        JsonNode byIds = send(post("/api/cycles/bulk"), HR, "{\"employeeIds\": [9, 10, 15], \"startDate\": \"2026-12-01\"}", 201);
        assertThat(byIds.path("created").size()).isEqualTo(1);
        assertThat(byIds.path("created").get(0).path("employeeId").asLong()).isEqualTo(9);
        assertThat(byIds.path("skipped").size()).isEqualTo(2);
        assertThat(byIds.path("skipped").get(0).path("reason").asText()).contains("aktywny cykl");

        JsonNode byDepartment = send(post("/api/cycles/bulk"), DG, "{\"department\": \"Biuro Kadr i Szkoleń\"}", 201);
        assertThat(byDepartment.path("created").size() + byDepartment.path("skipped").size()).isEqualTo(1);
        send(post("/api/cycles/bulk"), EVALUATOR, "{\"employeeIds\": [9]}", 403);
        send(post("/api/cycles/bulk"), HR, "{}", 422);
    }

    @Test
    void activeDeputyRunsSubstitutedCyclesWithAnnotatedAudit() throws Exception {
        expect(as(get("/api/cycles/16"), TOMASZ), 403);
        JsonNode s = send(post("/api/substitutions"), HR, """
                {"evaluatorId": %d, "deputyId": %d, "startDate": "%s", "endDate": "%s", "reason": "Urlop wypoczynkowy"}
                """.formatted(EVALUATOR, TOMASZ, LocalDate.now().minusDays(1), LocalDate.now().plusDays(10)), 201);
        assertThat(s.path("activeToday").asBoolean()).isTrue();

        assertThat(ids(fetch("/api/cycles", TOMASZ))).contains(16L).doesNotContain(3L); // tylko aktywne cykle zastępowanego
        send(post("/api/cycles/16/notes"), TOMASZ, "{\"content\": \"Notatka zastępcy z obserwacji\"}", 201);
        boolean annotated = false;
        for (JsonNode a : fetch("/api/audit-logs?limit=50", HR)) {
            annotated |= "NOTE_ADDED".equals(a.path("action").asText()) && a.path("entityId").asLong() == 16
                    && a.path("payload").path("substituteFor").asLong() == EVALUATOR;
        }
        assertThat(annotated).as("adnotacja zastępstwa w audycie").isTrue();
        assertThat(ids(fetch("/api/substitutions", EVALUATOR))).contains(s.path("id").asLong());
        send(post("/api/substitutions"), EVALUATOR, "{\"evaluatorId\": 2, \"deputyId\": 6, \"startDate\": \"2026-01-01\","
                + " \"endDate\": \"2026-01-02\", \"reason\": \"x\"}", 403);

        mvc.perform(as(delete("/api/substitutions/" + s.path("id").asLong()), HR)).andExpect(status().isNoContent());
        expect(as(get("/api/cycles/16"), TOMASZ), 403);
    }

    @Test
    void hrEditsEmployeeAndRecordsHistoricalEvaluation() throws Exception {
        JsonNode updated = send(patch("/api/hr/employees/12"), HR,
                "{\"positionTitle\": \"Starszy specjalista\", \"civilServant\": true, \"civilServiceGrade\": 3}", 200);
        assertThat(updated.path("positionTitle").asText()).isEqualTo("Starszy specjalista");
        assertThat(updated.path("civilServiceGrade").asInt()).isEqualTo(3);
        send(patch("/api/hr/employees/12"), HR, "{\"role\": \"KPRM_ADMIN\"}", 403);
        send(patch("/api/hr/employees/12"), HR, "{\"civilServiceGrade\": 10}", 422);
        send(patch("/api/hr/employees/12"), EVALUATOR, "{\"positionTitle\": \"x\"}", 403);

        send(post("/api/hr/employees/12/history"), HR,
                "{\"outcome\": \"POSITIVE\", \"level\": \"ABOVE\", \"evaluatedOn\": \"2021-05-10\"}", 201);
        send(post("/api/hr/employees/12/history"), HR,
                "{\"outcome\": \"POSITIVE\", \"level\": \"ABOVE\", \"evaluatedOn\": \"" + LocalDate.now().plusDays(5) + "\"}", 409);
        boolean historical = false;
        for (JsonNode h : fetch("/api/cycles/17/history", EVALUATOR)) historical |= "HISTORICAL".equals(h.path("source").asText());
        assertThat(historical).as("historia widoczna na cyklu").isTrue();
    }

    @Test
    void removalDeletesUnusedAccountAndAnonymizesOneWithHistory() throws Exception {
        jdbc.update("INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id) "
                + "VALUES (960, 1, 'Nowy Pracownik', 'nowy@mc-demo.gov.pl', 'EVALUATED', FALSE, 2) ON CONFLICT DO NOTHING");
        assertThat(send(delete("/api/hr/employees/960"), HR, "{}", 200).path("outcome").asText()).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = 960", Integer.class)).isZero();

        assertThat(send(delete("/api/hr/employees/7"), HR, "{}", 200).path("outcome").asText()).isEqualTo("ANONYMIZED");
        assertThat(ids(fetch("/api/auth/demo-users", HR))).doesNotContain(7L);
        assertThat(fetch("/api/cycles/3", HR).path("employeeId").asLong()).as("arkusz zachowany").isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = 7", String.class)).startsWith("anonimizacja-");
        send(post("/api/auth/gov-login"), null, "{\"userId\": 7}", 403);

        send(delete("/api/hr/employees/3"), HR, "{}", 409); // aktywny cykl
        send(delete("/api/hr/employees/" + EVALUATOR), HR, "{}", 409); // ma aktywne cykle jako oceniający
    }

    @Test
    void absenceRecalculationKeepsAcceptedRescheduledDueDate() throws Exception {
        JsonNode r = send(post("/api/cycles/16/reschedule"), EVALUATOR,
                "{\"mode\": \"EVALUATOR_ABSENCE\", \"newDueDate\": \"2027-03-01\", \"reason\": \"Planowana nieobecność\"}", 201);
        assertThat(r.path("status").asText()).isEqualTo("APPLIED");
        JsonNode recalculated = send(post("/api/hr/absences"), HR,
                "{\"userId\": 8, \"startDate\": \"2026-11-23\", \"endDate\": \"2026-11-24\", \"reason\": \"Urlop\"}", 200);
        assertThat(find(recalculated, "id", 16).path("dueDate").asText()).isEqualTo("2027-03-01");
    }

    @Test
    void formDetailsAndAdminCorrectionAreValidatedAndAudited() throws Exception {
        JsonNode draft = send(patch("/api/cycles/2/details"), DG,
                "{\"place\": \"Gdańsk\", \"dueDate\": \"2028-10-27\"}", 200);
        assertThat(draft.path("place").asText()).isEqualTo("Gdańsk");
        send(patch("/api/cycles/" + SMALL_CYCLE + "/details"), SMALL_EVALUATOR, "{\"dueDate\": \"2027-05-31\"}", 422);
        send(patch("/api/cycles/" + SMALL_CYCLE + "/details"), SMALL_EVALUATOR,
                "{\"dueDate\": \"2027-05-31\", \"reason\": \"Ujednolicenie terminu z planem kontroli\"}", 200);
        send(patch("/api/cycles/" + SMALL_CYCLE + "/details"), SMALL_EVALUATOR, "{\"dueDate\": \"2025-01-01\", \"reason\": \"x\"}", 422);

        send(post("/api/cycles/10/admin-correction"), HR, "{\"targetStatus\": \"EVALUATION_DRAFT\"}", 400);
        send(post("/api/cycles/10/admin-correction"), EMPLOYEE, "{\"targetStatus\": \"EVALUATION_DRAFT\", \"reason\": \"x\"}", 403);
        send(post("/api/cycles/10/admin-correction"), HR, "{\"targetStatus\": \"OBSERVATION\", \"reason\": \"x\"}", 409);
        JsonNode corrected = send(post("/api/cycles/10/admin-correction"), HR,
                "{\"targetStatus\": \"EVALUATION_DRAFT\", \"reason\": \"Błąd w ocenie cząstkowej zgłoszony przez oceniającego\"}", 200);
        assertThat(corrected.path("status").asText()).isEqualTo("EVALUATION_DRAFT");
        assertThat(corrected.path("evaluatedOn").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluation_history WHERE cycle_id = 10", Integer.class)).isZero();
        boolean audited = false;
        for (JsonNode a : fetch("/api/audit-logs?limit=50", HR)) {
            audited |= "ADMIN_CORRECTION".equals(a.path("action").asText()) && a.path("entityId").asLong() == 10
                    && a.path("payload").path("reason").asText().startsWith("Błąd w ocenie");
        }
        assertThat(audited).isTrue();
        send(post("/api/cycles/" + SMALL_CYCLE + "/admin-correction"), ADMIN,
                "{\"place\": \"Płock (siedziba)\", \"reason\": \"Korekta miejscowości przez administratora KPRM\"}", 200);
    }

    @Test
    void manualPreviousEvaluationIsUsedWhenHistoryIsMissing() throws Exception {
        // cykl 16: jedyna ocena pracownika (cykl 4) jest późniejsza niż początek okresu — brak poprzedniej w historii
        assertThat(fetch("/api/cycles/16", EVALUATOR).path("previousEvaluation").isNull()).isTrue();
        send(put("/api/cycles/16/evaluate"), EVALUATOR, """
                {"scores": {}, "submit": false,
                 "previousEvaluation": {"outcome": "POSITIVE", "level": "MEETS", "evaluatedOn": "2023-05-30"}}
                """, 200);
        send(put("/api/cycles/16/evaluate"), EVALUATOR, """
                {"scores": {}, "submit": false, "previousEvaluation": {"outcome": "POSITIVE"}}
                """, 422);
        JsonNode prev = fetch("/api/cycles/16", EVALUATOR).path("previousEvaluation");
        assertThat(prev.path("source").asText()).isEqualTo("MANUAL");
        assertThat(prev.path("level").asText()).isEqualTo("MEETS");
    }

    private JsonNode notification(long userId, String type, long cycleId) throws Exception {
        for (JsonNode n : fetch("/api/notifications", userId)) {
            if (type.equals(n.path("type").asText()) && n.path("cycleId").asLong() == cycleId) return n;
        }
        return null;
    }

    private static java.util.List<Long> ids(JsonNode array) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.path("id").asLong()));
        return out;
    }

    private JsonNode fetch(String path, long userId) throws Exception {
        String body = mvc.perform(as(get(path), userId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private JsonNode send(MockHttpServletRequestBuilder req, Long userId, String body, int expected) throws Exception {
        MockHttpServletRequestBuilder r = req.contentType(MediaType.APPLICATION_JSON).content(body);
        String res = mvc.perform(userId == null ? r : as(r, userId)).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return res.isBlank() ? json.nullNode() : json.readTree(res);
    }

    private void expect(MockHttpServletRequestBuilder req, int expected) throws Exception {
        mvc.perform(req).andExpect(status().is(expected));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r, long userId) {
        return r.header(DEMO, String.valueOf(userId));
    }

    private static JsonNode find(JsonNode array, String field, long value) {
        for (JsonNode n : array) {
            if (n.path(field).asLong() == value) return n;
        }
        throw new AssertionError("Brak elementu " + field + "=" + value);
    }
}
