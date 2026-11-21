package pl.gov.kprm.oceny.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.gov.kprm.oceny.domain.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lekki rate-limiting (okno stałe) dla logowania i wywołań AI — ochrona przed zgadywaniem kont, zalewaniem
 * modelu językowego i kosztami. Logowanie liczone per adres klienta, AI per zalogowany użytkownik.
 * Stan w pamięci procesu (jedna instancja); przy skalowaniu poziomym — współdzielony magazyn (np. Redis).
 */
@Component
@Order(CurrentUserFilter.ORDER + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    static final int MAX_TRACKED_KEYS = 10_000;
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    record Rule(String name, String method, String pathPrefix, int limit) {
        boolean matches(HttpServletRequest req) {
            return (method == null || method.equalsIgnoreCase(req.getMethod())) && req.getRequestURI().startsWith(pathPrefix);
        }
    }

    private record Window(long startSecond, int count) {
    }

    private final boolean enabled;
    private final long windowSeconds;
    private final boolean trustForwardedFor;
    private final List<Rule> rules;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.window-seconds:60}") long windowSeconds,
                           @Value("${app.rate-limit.login-per-window:30}") int loginLimit,
                           @Value("${app.rate-limit.ai-per-window:60}") int aiLimit,
                           @Value("${app.rate-limit.trust-forwarded-for:true}") boolean trustForwardedFor,
                           Clock clock) {
        this.enabled = enabled;
        this.windowSeconds = Math.max(1, windowSeconds);
        this.trustForwardedFor = trustForwardedFor;
        this.rules = List.of(new Rule("login", "POST", "/api/auth/", loginLimit), // gov-login i local-login
                new Rule("ai", null, "/api/core-ai/", aiLimit));
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<Rule> rule = enabled ? rules.stream().filter(r -> r.matches(req)).findFirst() : Optional.empty();
        if (rule.isEmpty()) {
            chain.doFilter(req, res);
            return;
        }
        long now = clock.instant().getEpochSecond();
        long windowStart = now - now % windowSeconds;
        Window window = windows.compute(rule.get().name() + ":" + clientKey(req), (k, w) ->
                w == null || w.startSecond() != windowStart ? new Window(windowStart, 1) : new Window(windowStart, w.count() + 1));
        evictStale(windowStart);
        if (window.count() > rule.get().limit()) {
            reject(res, rule.get(), windowStart + windowSeconds - now);
            return;
        }
        chain.doFilter(req, res);
    }

    private String clientKey(HttpServletRequest req) {
        if (req.getAttribute(CurrentUserFilter.ATTR) instanceof User user) {
            return "user:" + user.getId();
        }
        String forwarded = trustForwardedFor ? req.getHeader(FORWARDED_FOR) : null;
        return "ip:" + (forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : req.getRemoteAddr());
    }

    private void evictStale(long currentWindowStart) {
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> w.startSecond() < currentWindowStart);
        }
    }

    private static void reject(HttpServletResponse res, Rule rule, long retryAfterSeconds) throws IOException {
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfterSeconds)));
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Przekroczono limit " + rule.limit() + " żądań (" + rule.name() + ") — spróbuj ponownie za "
                + Math.max(1, retryAfterSeconds) + " s\"}");
    }
}
