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
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Poprawki po konsultacji z mentorami KPRM i audycie prawnym: statusy zatrudnienia (pierwsza ocena art. 37–38,
 * wyższe stanowiska), alerty kadrowe (art. 71 ust. 1 pkt 1, art. 89 ust. 2), ponowny sprzeciw po REDO
 * (art. 83 ust. 4) i odwołanie do sądu pracy z legal hold (art. 83 ust. 5).
 */
@SpringBootTest(properties = "app.jwt.secret=legal-test-secret-legal-test-secret!!!!")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LegalComplianceIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DEMO = "X-Demo-User-Id";
    private static final long DG = 1, EVALUATOR = 2, EMPLOYEE = 3, HR = 4;
    private static final long FIXED_TERM = 20, TWICE_NEGATIVE = 21;
    private static final long APPEALED_CYCLE = 14, APPEALED_EMPLOYEE = 16;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void fixedTermAndSeniorPositionsAreNotSubjectToPeriodicEvaluation() throws Exception {
        JsonNode rejected = send(post("/api/cycles"), HR, "{\"employeeId\": " + FIXED_TERM + "}", 422);
        assertThat(rejected.path("violations").get(0).asText()).contains("art. 37–38", "art. 35 ust. 3");
        JsonNode senior = send(post("/api/cycles"), HR, "{\"employeeId\": " + DG + "}", 422);
        assertThat(senior.path("violations").get(0).asText()).contains("wyższe stanowisko");

        JsonNode preview = fetch("/api/cycles/due-preview?employeeId=" + FIXED_TERM, HR);
        assertThat(preview.path("employmentStatus").asText()).isEqualTo("PRACOWNIK_SC_OKRESLONY");
        assertThat(preview.path("blockedReason").asText()).contains("pierwszej ocenie");
        assertThat(fetch("/api/cycles/due-preview?employeeId=9", HR).path("blockedReason").isNull()).isTrue();

        JsonNode bulk = send(post("/api/cycles/bulk"), HR, "{\"employeeIds\": [" + FIXED_TERM + "]}", 201);
        assertThat(bulk.path("skipped").get(0).path("reason").asText()).contains("art. 37–38");

        JsonNode employee = find(fetch("/api/hr/employees", HR), "id", FIXED_TERM);
        assertThat(employee.path("civilServant").asBoolean()).isFalse();
        assertThat(find(fetch("/api/hr/employees", HR), "id", TWICE_NEGATIVE).path("employmentStatus").asText())
                .isEqualTo("URZEDNIK_SC");
    }

    @Test
    void hrCanChangeEmploymentStatusKeepingCivilServantFlagConsistent() throws Exception {
        JsonNode updated = send(patch("/api/hr/employees/13"), HR, "{\"employmentStatus\": \"URZEDNIK_SC\"}", 200);
        assertThat(updated.path("civilServant").asBoolean()).isTrue();
        updated = send(patch("/api/hr/employees/13"), HR, "{\"employmentStatus\": \"PRACOWNIK_SC_NIEOKRESLONY\"}", 200);
        assertThat(updated.path("civilServant").asBoolean()).isFalse();
    }

    @Test
    void twoConsecutiveNegativesOfCivilServantRaiseCriticalHrAlert() throws Exception {
        JsonNode alerts = fetch("/api/hr/alerts", HR);
        JsonNode critical = find(alerts, "employeeId", TWICE_NEGATIVE);
        assertThat(critical.path("type").asText()).isEqualTo("TWO_NEGATIVE_TERMINATION");
        assertThat(critical.path("severity").asText()).isEqualTo("CRITICAL");
        assertThat(critical.path("legalBasis").asText()).contains("art. 71 ust. 1 pkt 1");
        assertThat(alerts.get(0).path("severity").asText()).as("krytyczne na początku").isEqualTo("CRITICAL");

        boolean notified = false;
        for (JsonNode n : fetch("/api/notifications", HR)) {
            notified |= n.path("type").asText().equals("HR_CRITICAL_ALERT") && n.path("message").asText().contains("Sikora");
        }
        assertThat(notified).isTrue();
        expect(as(get("/api/hr/alerts"), EMPLOYEE), 403);
        expect(as(get("/api/hr/alerts"), EVALUATOR), 403);
    }

    @Test
    void redoAllowsSecondAppealAndChangedEvaluationOnlyGoesToCourt() throws Exception {
        JsonNode redo = send(post("/api/cycles/" + APPEALED_CYCLE + "/appeal/decision"), DG,
                "{\"decision\": \"REDO\", \"comment\": \"Ponowne sporządzenie — brak notatek z obserwacji\"}", 200);
        assertThat(redo.path("status").asText()).isEqualTo("EVALUATION_DRAFT");
        assertThat(redo.path("appealRound").asInt()).isEqualTo(1);
        assertThat(redo.path("evaluationInterviewDate").isNull()).isTrue();
        assertThat(LocalDate.parse(redo.path("dueDate").asText())).as("nowy termin — bez natychmiastowej eskalacji")
                .isAfterOrEqualTo(LocalDate.now().plusDays(14));
        assertThat(redo.path("overdueDays").asLong()).isZero();
        String ics = text(get("/api/cycles/" + APPEALED_CYCLE + "/interview.ics"), EVALUATOR);
        assertThat(ics).contains("SEQUENCE:1", "Rozmowa oceniająca");

        evaluate(APPEALED_CYCLE, redo, 5);
        send(post("/api/cycles/" + APPEALED_CYCLE + "/acknowledge"), APPEALED_EMPLOYEE, "{}", 200);
        JsonNode second = send(post("/api/cycles/" + APPEALED_CYCLE + "/appeal"), APPEALED_EMPLOYEE,
                "{\"content\": \"Ponowny sprzeciw od oceny sporządzonej po raz drugi\"}", 200);
        assertThat(second.path("status").asText()).isEqualTo("APPEALED");
        assertThat(second.path("dgDecision").isNull()).as("nowe rozstrzygnięcie").isTrue();

        JsonNode changed = send(post("/api/cycles/" + APPEALED_CYCLE + "/appeal/decision"), DG,
                "{\"decision\": \"CHANGE\", \"comment\": \"Zmiana oceny\", \"scores\": " + scores(redo, 7) + "}", 200);
        assertThat(changed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(changed.path("appealAllowed").asBoolean()).isFalse();
        send(post("/api/cycles/" + APPEALED_CYCLE + "/appeal"), APPEALED_EMPLOYEE, "{\"content\": \"Trzeci sprzeciw\"}", 409);

        send(post("/api/cycles/" + APPEALED_CYCLE + "/court-appeal"), EVALUATOR,
                "{\"filedOn\": \"" + LocalDate.now() + "\"}", 403);
        JsonNode court = send(post("/api/cycles/" + APPEALED_CYCLE + "/court-appeal"), HR,
                "{\"filedOn\": \"" + LocalDate.now() + "\", \"caseReference\": \"IV P 123/26\"}", 200);
        assertThat(court.path("status").asText()).isEqualTo("APPEALED_TO_COURT");
        assertThat(court.path("legalHold").asBoolean()).isTrue();
        assertThat(court.path("courtCaseReference").asText()).isEqualTo("IV P 123/26");
        send(post("/api/cycles/" + APPEALED_CYCLE + "/court-appeal/close"), HR, "{\"outcome\": \" \"}", 400);
        JsonNode closed = send(post("/api/cycles/" + APPEALED_CYCLE + "/court-appeal/close"), HR,
                "{\"outcome\": \"Wyrok IV P 123/26 — powództwo oddalone\"}", 200);
        assertThat(closed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(closed.path("legalHold").asBoolean()).isFalse();
    }

    @Test
    void archivePackageCarriesPersonnelFileRetentionAndExcludesWorkingData() throws Exception {
        JsonNode recent = fetch("/api/cycles/9/ezd-package", HR);
        assertThat(recent.path("retention").path("personnelFilePart").asText()).isEqualTo("B");
        assertThat(recent.path("retention").path("years").asInt()).as("zatrudniony 2021 r.").isEqualTo(10);
        assertThat(recent.path("retention").path("legalHold").asBoolean()).isFalse();
        assertThat(recent.path("archiveContents").size()).isEqualTo(2);
        assertThat(recent.path("excludedFromArchive").toString()).contains("EmployeeNote", "PreInterviewSurvey");
        assertThat(recent.path("exclusionReason").asText()).contains("czarnych teczek");
        assertThat(fetch("/api/cycles/3/ezd-package", HR).path("retention").path("years").asInt())
                .as("brak daty zatrudnienia — kategoria ostrożnościowa").isEqualTo(50);
    }

    @Test
    void closedCycleWorkingNotesArePurgedButSheetAndHistoryStay() throws Exception {
        long completed = 9;
        jdbc.update("INSERT INTO employee_notes (cycle_id, author_id, content, is_shared_after_eval) "
                + "VALUES (?, 2, 'Robocza notatka po zamknięciu', FALSE)", completed);
        jdbc.update("INSERT INTO pre_interview_surveys (cycle_id, questions_json, status) VALUES (?, '[]'::jsonb, 'SENT')",
                completed);
        JsonNode before = fetch("/api/cycles/" + completed, HR);
        int historyBefore = fetch("/api/hr/employees/13/history", HR).size();

        expect(as(post("/api/hr/retention/purge-working-notes"), EVALUATOR), 403);
        JsonNode result = send(post("/api/hr/retention/purge-working-notes"), HR, "", 200);
        assertThat(result.path("cyclesPurged").asInt()).isPositive();
        assertThat(result.path("notesDeleted").asLong()).isPositive();

        JsonNode after = fetch("/api/cycles/" + completed, HR);
        assertThat(after.path("workingNotesPurgedAt").isNull()).isFalse();
        assertThat(after.path("scores")).isEqualTo(before.path("scores"));
        assertThat(after.path("finalResult").asText()).isEqualTo(before.path("finalResult").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee_notes WHERE cycle_id = ?", Long.class, completed)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pre_interview_surveys WHERE cycle_id = ?", Long.class, completed)).isZero();
        assertThat(fetch("/api/hr/employees/13/history", HR).size()).isEqualTo(historyBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluation_cycles WHERE id = ?", Long.class, completed)).isOne();
        // aktywne cykle nie są czyszczone
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee_notes WHERE cycle_id = 13", Long.class)).isPositive();
    }

    @Test
    void jobDescriptionIsStoredInEmployeeProfile() throws Exception {
        JsonNode updated = send(patch("/api/hr/employees/12"), HR,
                "{\"jobDescription\": \"Obsługa kancelarii i rejestracja korespondencji w EZD\", \"employmentStartDate\": \"2020-05-04\"}", 200);
        assertThat(updated.path("jobDescription").asText()).contains("EZD");
        assertThat(updated.path("employmentStartDate").asText()).isEqualTo("2020-05-04");
        send(patch("/api/hr/employees/12"), HR, "{\"employmentStartDate\": \"2099-01-01\"}", 422);
    }

    @Test
    void pdfPartsParameterIsValidated() throws Exception {
        expect(as(get("/api/cycles/1/pdf?parts=2"), HR), 400);
    }

    private void evaluate(long cycleId, JsonNode cycle, int score) throws Exception {
        send(put("/api/cycles/" + cycleId + "/evaluate"), EVALUATOR, """
                {"scores": %s, "justification": "Uzasadnienie oceny sporządzonej po raz drugi",
                 "iprzNotes": "Szkolenie z planowania pracy", "submit": true}
                """.formatted(scores(cycle, score)), 200);
    }

    private static String scores(JsonNode cycle, int score) {
        StringBuilder s = new StringBuilder("{");
        for (Iterator<JsonNode> it = cycle.path("criteria").elements(); it.hasNext(); ) {
            s.append('"').append(it.next().path("code").asText()).append("\": ").append(score);
            if (it.hasNext()) s.append(", ");
        }
        return s.append('}').toString();
    }

    private JsonNode fetch(String path, long userId) throws Exception {
        return json.readTree(text(get(path), userId));
    }

    private String text(MockHttpServletRequestBuilder req, long userId) throws Exception {
        return mvc.perform(as(req, userId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode send(MockHttpServletRequestBuilder req, long userId, String body, int expected) throws Exception {
        String res = mvc.perform(as(req.contentType(MediaType.APPLICATION_JSON).content(body), userId))
                .andExpect(status().is(expected))
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
