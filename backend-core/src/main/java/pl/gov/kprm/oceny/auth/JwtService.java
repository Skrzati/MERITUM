package pl.gov.kprm.oceny.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.gov.kprm.oceny.domain.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Minimalny JWT (HS256) — podpis HMAC-SHA256, weryfikacja w czasie stałym, kontrola wygaśnięcia i wystawcy.
 * Brak JWT_SECRET => losowy klucz na czas życia procesu (tokeny tracą ważność po restarcie).
 */
@Slf4j
@Component
public class JwtService {
    static final String ISSUER = "meritum";
    private static final String HMAC = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;
    private final ObjectMapper json;

    public JwtService(@Value("${app.jwt.secret:}") String secret, @Value("${app.jwt.ttl-minutes:480}") long ttlMinutes,
                      Clock clock, ObjectMapper json) {
        byte[] configured = secret.getBytes(StandardCharsets.UTF_8);
        if (configured.length >= MIN_SECRET_BYTES) {
            this.secret = configured;
        } else {
            log.warn("JWT_SECRET nie ustawiony lub krótszy niż {} bajtów — używam losowego klucza (tylko środowisko demo)",
                    MIN_SECRET_BYTES);
            this.secret = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(this.secret);
        }
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.clock = clock;
        this.json = json;
    }

    public record Claims(long userId, String role, long expiresAt) {
    }

    public String issue(User user, String identityProvider) {
        long now = clock.instant().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", String.valueOf(user.getId()));
        claims.put("name", user.getFullName());
        claims.put("role", user.getRole().name());
        claims.put("office", user.getOfficeId());
        claims.put("idp", identityProvider);
        claims.put("iat", now);
        claims.put("exp", now + ttl.toSeconds());
        try {
            String payload = B64.encodeToString(json.writeValueAsBytes(claims));
            String unsigned = HEADER + "." + payload;
            return unsigned + "." + B64.encodeToString(sign(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("Nie można wystawić tokenu", e);
        }
    }

    public Optional<Claims> verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !HEADER.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, B64D.decode(parts[2]))) {
                return Optional.empty();
            }
            Map<String, Object> claims = json.readValue(B64D.decode(parts[1]), new TypeReference<>() {
            });
            long exp = ((Number) claims.get("exp")).longValue();
            if (!ISSUER.equals(claims.get("iss")) || exp <= clock.instant().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(new Claims(Long.parseLong((String) claims.get("sub")), (String) claims.get("role"), exp));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    private byte[] sign(String data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC);
        mac.init(new SecretKeySpec(secret, HMAC));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
