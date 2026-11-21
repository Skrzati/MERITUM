package pl.gov.kprm.oceny.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Typowe ataki na JWT: alg=none, podmiana nagłówka/payloadu, obcy klucz, wygaśnięcie, śmieciowe tokeny. */
class JwtAttackTest {
    private static final String SECRET = "attack-test-secret-attack-test-secret!!";
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final JwtService jwt = new JwtService(SECRET, 60, Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setOfficeId(1L);
        u.setFullName("Jan Testowy");
        u.setRole(role);
        return u;
    }

    private static String b64(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsAlgNoneToken() {
        String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = b64("{\"iss\":\"meritum\",\"sub\":\"5\",\"role\":\"KPRM_ADMIN\",\"exp\":9999999999}");
        assertThat(jwt.verify(header + "." + payload + ".")).isEmpty();
        assertThat(jwt.verify(header + "." + payload)).isEmpty();
    }

    @Test
    void rejectsPrivilegeEscalationBySwappingPayload() {
        String token = jwt.issue(user(3, Role.EVALUATED), "TEST");
        String[] parts = token.split("\\.");
        String forged = b64("{\"iss\":\"meritum\",\"sub\":\"5\",\"role\":\"KPRM_ADMIN\",\"exp\":9999999999}");
        assertThat(jwt.verify(parts[0] + "." + forged + "." + parts[2])).isEmpty();
    }

    @Test
    void rejectsHeaderDowngradeAndSignatureFromOtherToken() {
        String employee = jwt.issue(user(3, Role.EVALUATED), "TEST");
        String admin = jwt.issue(user(5, Role.KPRM_ADMIN), "TEST");
        String[] e = employee.split("\\.");
        String[] a = admin.split("\\.");
        assertThat(jwt.verify(e[0] + "." + a[1] + "." + e[2])).as("cudzy podpis").isEmpty();
        assertThat(jwt.verify(b64("{\"alg\":\"HS512\",\"typ\":\"JWT\"}") + "." + e[1] + "." + e[2])).isEmpty();
    }

    @Test
    void rejectsTokenFromAnotherInstanceAndAfterExpiry() {
        JwtService other = new JwtService("other-instance-secret-other-instance!!", 60, Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper());
        assertThat(jwt.verify(other.issue(user(1, Role.DG), "TEST"))).isEmpty();

        JwtService later = new JwtService(SECRET, 60, Clock.fixed(NOW.plusSeconds(3601), ZoneOffset.UTC), new ObjectMapper());
        assertThat(later.verify(jwt.issue(user(1, Role.DG), "TEST"))).isEmpty();
    }

    @Test
    void garbageTokensNeverThrow() {
        String valid = jwt.issue(user(1, Role.DG), "TEST");
        String[] garbage = {"", ".", "..", "a.b.c", "%%%.%%%.%%%", valid + "x", valid.replace('.', '_'),
                valid.substring(0, valid.length() / 2), "x".repeat(100_000)};
        for (String g : garbage) {
            assertThatCode(() -> assertThat(jwt.verify(g)).isEmpty()).as(g.length() > 40 ? g.substring(0, 40) : g)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shortSecretFallsBackToRandomKeySoDefaultsCannotBeGuessed() {
        JwtService a = new JwtService("short", 60, Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        JwtService b = new JwtService("short", 60, Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        assertThat(b.verify(a.issue(user(1, Role.DG), "TEST"))).isEmpty();
    }
}
