package pl.gov.kprm.oceny.export;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Zaproszenie iCalendar (RFC 5545): CRLF, escapowanie tekstu, zawijanie linii do 75 oktetów, czas w UTC. */
public final class IcsCalendarRenderer {
    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;
    private static final DateTimeFormatter UTC_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public record Attendee(String name, String email) {
    }

    public record Invitation(String uid, Instant stamp, LocalDate date, LocalTime start, int durationMinutes,
                             ZoneId zone, String summary, String description, String location,
                             Attendee organizer, List<Attendee> attendees) {
    }

    private IcsCalendarRenderer() {
    }

    public static String render(Invitation inv) {
        Instant start = inv.date().atTime(inv.start()).atZone(inv.zone()).toInstant();
        Instant end = start.plusSeconds(inv.durationMinutes() * 60L);
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//KPRM//MERITUM//PL");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:REQUEST");
        line(out, "BEGIN:VEVENT");
        line(out, "UID:" + inv.uid());
        line(out, "DTSTAMP:" + UTC_FORMAT.format(inv.stamp()));
        line(out, "DTSTART:" + UTC_FORMAT.format(start));
        line(out, "DTEND:" + UTC_FORMAT.format(end));
        line(out, "SUMMARY:" + escape(inv.summary()));
        line(out, "DESCRIPTION:" + escape(inv.description()));
        if (inv.location() != null && !inv.location().isBlank()) {
            line(out, "LOCATION:" + escape(inv.location()));
        }
        line(out, "ORGANIZER;CN=" + param(inv.organizer().name()) + ":mailto:" + inv.organizer().email());
        for (Attendee a : inv.attendees()) {
            line(out, "ATTENDEE;CN=" + param(a.name()) + ";ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:"
                    + a.email());
        }
        line(out, "STATUS:CONFIRMED");
        line(out, "END:VEVENT");
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n");
    }

    private static String param(String value) {
        return "\"" + value.replace("\"", "'") + "\"";
    }

    /** Zawija linię tak, by żadna nie przekroczyła 75 oktetów UTF-8 (bez dzielenia znaku wielobajtowego). */
    private static void line(StringBuilder out, String content) {
        int octets = 0;
        int limit = MAX_LINE_OCTETS;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append(CRLF).append(' ');
                octets = 0;
                limit = MAX_LINE_OCTETS - 1;
            }
            out.appendCodePoint(cp);
            octets += size;
            i += Character.charCount(cp);
        }
        out.append(CRLF);
    }
}
