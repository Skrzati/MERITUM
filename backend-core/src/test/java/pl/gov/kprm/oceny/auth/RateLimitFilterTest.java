package pl.gov.kprm.oceny.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {
    private static final int LOGIN_LIMIT = 3;
    private static final int AI_LIMIT = 2;
    private static final long WINDOW = 60;

    /** Zegar przesuwany w teście — przejście do kolejnego okna limitu. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-21T12:00:00Z");

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();

    private RateLimitFilter filter(boolean enabled, boolean trustForwardedFor) {
        return new RateLimitFilter(enabled, WINDOW, LOGIN_LIMIT, AI_LIMIT, trustForwardedFor, clock);
    }

    private static MockHttpServletRequest login(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/gov-login");
        req.setRemoteAddr(ip);
        return req;
    }

    private static MockHttpServletRequest ai(long userId) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/core-ai/socratic-draft");
        User u = new User();
        u.setId(userId);
        u.setRole(Role.EVALUATOR);
        req.setAttribute(CurrentUserFilter.ATTR, u);
        return req;
    }

    private static int status(RateLimitFilter f, MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void loginFloodFromOneClientGets429WithRetryAfter() throws Exception {
        RateLimitFilter f = filter(true, false);
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            assertThat(status(f, login("10.0.0.1"))).isEqualTo(200);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(login("10.0.0.1"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(res.getHeader("Retry-After"))).isBetween(1L, WINDOW);
        assertThat(res.getContentAsString()).contains("\"status\":429");
        assertThat(status(f, login("10.0.0.2"))).as("inny klient ma własny limit").isEqualTo(200);
    }

    @Test
    void limitResetsInNextWindow() throws Exception {
        RateLimitFilter f = filter(true, false);
        for (int i = 0; i <= LOGIN_LIMIT; i++) status(f, login("10.0.0.1"));
        assertThat(status(f, login("10.0.0.1"))).isEqualTo(429);

        clock.advance(WINDOW);

        assertThat(status(f, login("10.0.0.1"))).isEqualTo(200);
    }

    @Test
    void aiIsLimitedPerAuthenticatedUser() throws Exception {
        RateLimitFilter f = filter(true, false);
        for (int i = 0; i < AI_LIMIT; i++) {
            assertThat(status(f, ai(2))).isEqualTo(200);
        }
        assertThat(status(f, ai(2))).isEqualTo(429);
        assertThat(status(f, ai(4))).as("inny użytkownik").isEqualTo(200);
    }

    @Test
    void forwardedForIdentifiesClientBehindProxyOnlyWhenTrusted() throws Exception {
        RateLimitFilter trusted = filter(true, true);
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            MockHttpServletRequest req = login("172.18.0.5");
            req.addHeader("X-Forwarded-For", "203.0.113.7, 172.18.0.5");
            status(trusted, req);
        }
        MockHttpServletRequest sameClient = login("172.18.0.5");
        sameClient.addHeader("X-Forwarded-For", "203.0.113.7");
        MockHttpServletRequest otherClient = login("172.18.0.5");
        otherClient.addHeader("X-Forwarded-For", "198.51.100.9");

        assertThat(status(trusted, sameClient)).isEqualTo(429);
        assertThat(status(trusted, otherClient)).isEqualTo(200);

        RateLimitFilter untrusted = filter(true, false);
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            MockHttpServletRequest spoofed = login("172.18.0.5");
            spoofed.addHeader("X-Forwarded-For", "10.9.9." + i);
            status(untrusted, spoofed);
        }
        assertThat(status(untrusted, login("172.18.0.5"))).as("podrabianie X-Forwarded-For nie omija limitu").isEqualTo(429);
    }

    @Test
    void otherEndpointsAndDisabledFilterAreNotLimited() throws Exception {
        RateLimitFilter f = filter(true, false);
        for (int i = 0; i < LOGIN_LIMIT * 3; i++) {
            assertThat(status(f, new MockHttpServletRequest("GET", "/api/cycles"))).isEqualTo(200);
            assertThat(status(f, new MockHttpServletRequest("GET", "/api/auth/gov-login"))).as("tylko POST").isEqualTo(200);
        }
        RateLimitFilter off = filter(false, false);
        for (int i = 0; i < LOGIN_LIMIT * 3; i++) {
            assertThat(status(off, login("10.0.0.1"))).isEqualTo(200);
        }
    }
}
