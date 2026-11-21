package pl.gov.kprm.oceny.export;

import org.junit.jupiter.api.Test;
import pl.gov.kprm.oceny.export.IcsCalendarRenderer.Attendee;
import pl.gov.kprm.oceny.export.IcsCalendarRenderer.Invitation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IcsCalendarRendererTest {
    private static final Attendee EVALUATOR = new Attendee("Jan Nowak", "j.nowak@kprm.gov.pl");
    private static final Attendee EMPLOYEE = new Attendee("Anna Kowalska", "a.kowalska@kprm.gov.pl");

    private static String render(String description) {
        return IcsCalendarRenderer.render(new Invitation("cycle-1-initial@test", Instant.parse("2026-11-21T08:00:00Z"),
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), 60, ZoneId.of("Europe/Warsaw"),
                "Rozmowa rozpoczynająca", description, "KPRM, Warszawa", EVALUATOR, List.of(EMPLOYEE, EVALUATOR)));
    }

    @Test
    void convertsWarsawTimeToUtcAndUsesCrlf() {
        String ics = render("§ 5 ust. 1");
        assertThat(ics).contains("DTSTART:20261201T090000Z\r\n", "DTEND:20261201T100000Z\r\n", "METHOD:REQUEST\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void escapesTextAndFoldsLongLinesWithinOctetLimit() {
        String ics = render("Omówienie zadań, wybór kryteriów; oczekiwania ".repeat(5));
        assertThat(ics.replace("\r\n ", "")).contains("Omówienie zadań\\, wybór kryteriów\\; oczekiwania");
        assertThat(Arrays.stream(ics.split("\r\n")).mapToInt(l -> l.getBytes(StandardCharsets.UTF_8).length).max()
                .orElseThrow()).isLessThanOrEqualTo(75);
    }
}
