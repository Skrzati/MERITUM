package pl.gov.kprm.oceny.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Etap 2 — małe urzędy: konta lokalne (BCrypt) i zarządzanie kontami w /admin, ankieta przygotowawcza przed rozmową
 * („Wyślij przełożonemu”), e-maile SMTP przy przedłużeniu terminu, po sprzeciwie i przy krytycznych alertach kadrowych.
 */
@SpringBootTest(properties = {"app.jwt.secret=small-office-secret-small-office-secret!!",
        "app.mail.enabled=true", "app.auth.bcrypt-strength=4"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SmallOfficeIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DEMO = "X-Demo-User-Id";
    private static final long DG = 1, EVALUATOR = 2, HR = 4, ADMIN = 5;
    private static final long SMALL_HEAD = 17, SMALL_EVALUATOR = 18, SMALL_EMPLOYEE = 19, SMALL_CYCLE = 18, SMALL_OFFICE = 2;
    private static final String PASSWORD = "Weterynaria2026!";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @MockBean
    JavaMailSender mail;

    @Test
    void adminCreatesLocalAccountForSmallOfficeAndUserLogsInWithPassword() throws Exception {
        JsonNode created = send(post("/api/admin/accounts"), ADMIN, """
                {"officeId": %d, "firstName": "Ewa", "lastName": "Sroka", "email": "ewa.sroka@piw-demo.gov.pl",
                 "role": "EVALUATED", "employmentStatus": "PRACOWNIK_SC_NIEOKRESLONY", "supervisorId": %d,
                 "password": "%s"}
                """.formatted(SMALL_OFFICE, SMALL_EVALUATOR, PASSWORD), 201);
        assertThat(created.path("hasLocalPassword").asBoolean()).isTrue();
        assertThat(created.toString()).doesNotContain("passwordHash").doesNotContain(PASSWORD);

        JsonNode login = send(post("/api/auth/local-login"), null,
                "{\"login\": \"EWA.SROKA@piw-demo.gov.pl\", \"password\": \"" + PASSWORD + "\"}", 200);
        assertThat(login.path("identityProvider").asText()).isEqualTo("LOCAL");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + login.path("accessToken").asText()))
                .andExpect(status().isOk());

        send(post("/api/auth/local-login"), null, "{\"login\": \"ewa.sroka@piw-demo.gov.pl\", \"password\": \"zle-haslo-123\"}", 401);
        send(post("/api/auth/local-login"), null, "{\"login\": \"nie.ma@piw-demo.gov.pl\", \"password\": \"" + PASSWORD + "\"}", 401);
        send(post("/api/admin/accounts"), ADMIN, """
                {"officeId": %d, "firstName": "A", "lastName": "B", "email": "slabe@piw-demo.gov.pl", "role": "EVALUATED",
                 "password": "krotkie"}
                """.formatted(SMALL_OFFICE), 422);
        send(post("/api/admin/accounts"), HR, """
                {"officeId": %d, "firstName": "A", "lastName": "B", "email": "obcy@piw-demo.gov.pl", "role": "EVALUATED"}
                """.formatted(SMALL_OFFICE), 403);
        send(post("/api/admin/accounts"), ADMIN, """
                {"officeId": %d, "firstName": "A", "lastName": "B", "email": "admin2@piw-demo.gov.pl", "role": "KPRM_ADMIN"}
                """.formatted(SMALL_OFFICE), 403);
    }

    @Test
    void localLoginRequiresSmallOfficeModeEnabledForTheOffice() throws Exception {
        send(post("/api/admin/accounts/" + EVALUATOR + "/password"), HR, "{\"password\": \"" + PASSWORD + "\"}", 204);
        String body = "{\"login\": \"piotr.nowak@mc-demo.gov.pl\", \"password\": \"" + PASSWORD + "\"}";
        send(post("/api/auth/local-login"), null, body, 401);
        send(patch("/api/admin/offices/1/local-auth"), HR, "{\"enabled\": true}", 403);
        JsonNode office = send(patch("/api/admin/offices/1/local-auth"), ADMIN, "{\"enabled\": true}", 200);
        assertThat(office.path("localAuthEnabled").asBoolean()).isTrue();
        send(post("/api/auth/local-login"), null, body, 200);
        send(patch("/api/admin/offices/1/local-auth"), ADMIN, "{\"enabled\": false}", 200);
    }

    @Test
    void adminImportsCsvIntoChosenOffice() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "konta.csv", "text/csv", """
                full_name,email,role,employment_status,supervisor_email
                Jan Wrona,jan.wrona@piw-demo.gov.pl,EVALUATED,PRACOWNIK_SC_OKRESLONY,dariusz.kowal@piw-demo.gov.pl
                Ola Mak,ola.mak@piw-demo.gov.pl,EVALUATED,URZEDNIK_SC,
                """.getBytes(StandardCharsets.UTF_8));
        String res = mvc.perform(multipart("/api/admin/accounts/import-csv").file(csv).param("officeId", "2")
                        .header(DEMO, String.valueOf(ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(res).path("imported").asInt()).isEqualTo(2);
        JsonNode accounts = fetch("/api/admin/accounts?officeId=2", ADMIN);
        boolean fixedTerm = false;
        for (JsonNode a : accounts) {
            fixedTerm |= a.path("user").path("email").asText().equals("jan.wrona@piw-demo.gov.pl")
                    && a.path("user").path("employmentStatus").asText().equals("PRACOWNIK_SC_OKRESLONY");
        }
        assertThat(fixedTerm).isTrue();
        expectStatus(get("/api/admin/accounts?officeId=2"), HR, 403);
    }

    @Test
    void preInterviewSurveyWithCustomQuestionIsSentToSupervisor() throws Exception {
        String base = "/api/cycles/" + SMALL_CYCLE + "/pre-interview-survey";
        JsonNode hidden = fetch(base, SMALL_EMPLOYEE);
        assertThat(hidden.path("open").asBoolean()).isFalse();
        assertThat(hidden.path("questions").size()).isZero();
        send(put(base + "/answers"), SMALL_EMPLOYEE, "{\"answers\": {\"Q1\": \"za wcześnie\"}}", 409);
        JsonNode initial = fetch(base, SMALL_EVALUATOR);
        assertThat(initial.path("status").asText()).isEqualTo("DRAFT");
        assertThat(initial.path("allowCustom").asBoolean()).isTrue();
        assertThat(initial.path("canSchedule").asBoolean()).isTrue();
        assertThat(initial.path("questions").size()).isEqualTo(5);

        JsonNode withCustom = send(post(base + "/questions"), SMALL_EVALUATOR,
                "{\"text\": \"Jak ocenia Pani współpracę z inspektorami terenowymi?\"}", 200);
        JsonNode custom = withCustom.path("questions").get(5);
        assertThat(custom.path("custom").asBoolean()).isTrue();
        send(post(base + "/questions"), SMALL_EMPLOYEE, "{\"text\": \"Pytanie ocenianego\"}", 403);

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        send(put(base + "/settings"), SMALL_EMPLOYEE, "{\"opensOn\": \"%s\"}".formatted(today), 403);
        send(put(base + "/settings"), SMALL_EVALUATOR,
                "{\"opensOn\": \"%s\", \"answerDeadline\": \"%s\"}".formatted(today, today.minusDays(1)), 422);
        JsonNode opened = send(put(base + "/settings"), SMALL_EVALUATOR,
                "{\"opensOn\": \"%s\", \"answerDeadline\": \"%s\", \"introMessage\": \"Proszę o odpowiedzi przed rozmową.\"}"
                        .formatted(today, today.plusDays(7)), 200);
        assertThat(opened.path("open").asBoolean()).isTrue();
        assertThat(fetch(base, SMALL_EMPLOYEE).path("introMessage").asText()).isEqualTo("Proszę o odpowiedzi przed rozmową.");
        boolean employeeNotified = false;
        for (JsonNode n : fetch("/api/notifications", SMALL_EMPLOYEE)) {
            employeeNotified |= n.path("type").asText().equals("PRE_INTERVIEW_SURVEY_OPEN");
        }
        assertThat(employeeNotified).isTrue();

        send(put(base + "/answers"), SMALL_EMPLOYEE, """
                {"answers": {"Q1": "Kontrole gospodarstw bez uwag", "%s": "Bardzo dobra"}}
                """.formatted(custom.path("id").asText()), 200);
        JsonNode draftForEvaluator = fetch(base, SMALL_EVALUATOR);
        assertThat(draftForEvaluator.path("answersVisible").asBoolean()).isFalse();
        assertThat(draftForEvaluator.path("questions").get(0).path("answer").isNull()).isTrue();

        JsonNode sent = send(post(base + "/send"), SMALL_EMPLOYEE, "", 200);
        assertThat(sent.path("status").asText()).isEqualTo("SENT");
        JsonNode forEvaluator = fetch(base, SMALL_EVALUATOR);
        assertThat(forEvaluator.path("questions").get(0).path("answer").asText()).isEqualTo("Kontrole gospodarstw bez uwag");
        boolean notified = false;
        for (JsonNode n : fetch("/api/notifications", SMALL_EVALUATOR)) {
            notified |= n.path("type").asText().equals("PRE_INTERVIEW_SURVEY_SENT");
        }
        assertThat(notified).isTrue();

        send(put(base + "/answers"), SMALL_EMPLOYEE, "{\"answers\": {\"Q1\": \"zmiana\"}}", 409);
        expectStatus(get(base), HR, 403);
        expectStatus(get(base), SMALL_HEAD, 403);
    }

    @Test
    void longAbsenceExtensionIsEmailedToEmployeeAndEvaluator() throws Exception {
        clearInvocations(mail);
        send(post("/api/hr/absences"), HR,
                "{\"userId\": 8, \"startDate\": \"2026-01-05\", \"endDate\": \"2026-03-06\", \"reason\": \"L4\"}", 200);
        List<SimpleMailMessage> sent = sentMails();
        SimpleMailMessage extension = sent.stream().filter(m -> m.getSubject().contains("Przedłużenie terminu"))
                .findFirst().orElseThrow();
        assertThat(extension.getTo()).contains("michal.lewicki@mc-demo.gov.pl", "piotr.nowak@mc-demo.gov.pl");
        assertThat(extension.getText()).contains("art. 81 ust. 7");
    }

    @Test
    void criticalAlertAndReevaluationDateAreEmailedAfterAppealDecision() throws Exception {
        // #16 jako urzędnik SC: wcześniejsza ocena negatywna + zmieniona po sprzeciwie (cykl 14) negatywna = art. 71 ust. 1 pkt 1
        send(patch("/api/hr/employees/16"), HR, "{\"employmentStatus\": \"URZEDNIK_SC\"}", 200);
        jdbc.update("INSERT INTO evaluation_history (user_id, office_id, outcome, level, evaluated_on, source, entered_by) "
                + "VALUES (16, 1, 'NEGATIVE', 'BELOW', DATE '2025-06-02', 'HISTORICAL', 4)");
        JsonNode cycle = fetch("/api/cycles/14", HR);
        StringBuilder scores = new StringBuilder("{");
        cycle.path("criteria").forEach(c -> scores.append('"').append(c.path("code").asText()).append("\": 3,"));
        scores.setCharAt(scores.length() - 1, '}');
        clearInvocations(mail);
        send(post("/api/cycles/14/appeal/decision"), DG,
                "{\"decision\": \"CHANGE\", \"comment\": \"Ocena utrzymana jako negatywna\", \"scores\": " + scores + "}", 200);

        List<SimpleMailMessage> sent = sentMails();
        SimpleMailMessage critical = sent.stream().filter(m -> m.getSubject().contains("ALERT KRYTYCZNY")).findFirst().orElseThrow();
        assertThat(critical.getTo()).contains("marek.zielinski@mc-demo.gov.pl", "anna.kowalska@mc-demo.gov.pl");
        assertThat(critical.getText()).contains("art. 71 ust. 1 pkt 1");
        assertThat(sent).anyMatch(m -> m.getSubject().contains("Termin ponownej oceny po sprzeciwie")
                && m.getText().contains("6 miesięcy"));
    }

    private List<SimpleMailMessage> sentMails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    private JsonNode fetch(String path, long userId) throws Exception {
        String body = mvc.perform(get(path).header(DEMO, String.valueOf(userId))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private JsonNode send(MockHttpServletRequestBuilder req, Long userId, String body, int expected) throws Exception {
        MockHttpServletRequestBuilder r = req.contentType(MediaType.APPLICATION_JSON).content(body);
        String res = mvc.perform(userId == null ? r : r.header(DEMO, String.valueOf(userId)))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return res.isBlank() ? json.nullNode() : json.readTree(res);
    }

    private void expectStatus(MockHttpServletRequestBuilder req, long userId, int expected) throws Exception {
        mvc.perform(req.header(DEMO, String.valueOf(userId))).andExpect(status().is(expected));
    }
}
