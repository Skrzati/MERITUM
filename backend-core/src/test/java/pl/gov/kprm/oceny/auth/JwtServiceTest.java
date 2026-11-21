package pl.gov.kprm.oceny.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private static final String SECRET = "test-secret-test-secret-test-secret-!!";
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private final JwtService jwt = service(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issuesAndVerifiesSignedToken() {
        String token = jwt.issue(user(), "SIMULATED_WK_PZ");

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwt.verify(token)).hasValueSatisfying(c -> {
            assertThat(c.userId()).isEqualTo(2L);
            assertThat(c.role()).isEqualTo("EVALUATOR");
        });
    }

    @Test
    void rejectsTamperedPayloadAndForeignKey() {
        String token = jwt.issue(user(), "SIMULATED_WK_PZ");
        String[] p = token.split("\\.");
        String forged = p[0] + "." + p[1].substring(0, p[1].length() - 2) + "AA." + p[2];

        assertThat(jwt.verify(forged)).isEmpty();
        assertThat(new JwtService("another-secret-another-secret-another!!", 60, Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper()).verify(token)).isEmpty();
        assertThat(jwt.verify("not.a.jwt")).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        String token = jwt.issue(user(), "SIMULATED_WK_PZ");
        JwtService later = service(Clock.fixed(NOW.plusSeconds(61 * 60), ZoneOffset.UTC));

        assertThat(later.verify(token)).isEmpty();
    }

    private static JwtService service(Clock clock) {
        return new JwtService(SECRET, 60, clock, new ObjectMapper());
    }

    private static User user() {
        User u = new User();
        u.setId(2L);
        u.setOfficeId(1L);
        u.setFullName("Piotr Nowak");
        u.setRole(Role.EVALUATOR);
        return u;
    }
}
