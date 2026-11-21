package pl.gov.kprm.oceny.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.UserRepository;

import java.io.IOException;
import java.util.Optional;

/**
 * Ustala bieżącego użytkownika: preferowany {@code Authorization: Bearer <JWT>} (gov-login),
 * a dla testów/Swaggera — nagłówek {@code X-Demo-User-Id} (wyłączany przez app.auth.demo-header-enabled=false).
 */
@Component
public class CurrentUserFilter extends OncePerRequestFilter {
    public static final String DEMO_HEADER = "X-Demo-User-Id";
    public static final String ATTR = CurrentUserFilter.class.getName() + ".user";
    private static final String BEARER = "Bearer ";

    private final UserRepository users;
    private final JwtService jwt;
    private final boolean demoHeaderEnabled;

    public CurrentUserFilter(UserRepository users, JwtService jwt,
                             @Value("${app.auth.demo-header-enabled:true}") boolean demoHeaderEnabled) {
        this.users = users;
        this.jwt = jwt;
        this.demoHeaderEnabled = demoHeaderEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String authorization = req.getHeader(HttpHeaders.AUTHORIZATION);
        String demoId = req.getHeader(DEMO_HEADER);
        Optional<User> user = Optional.empty();
        if (authorization != null && authorization.startsWith(BEARER)) {
            Optional<JwtService.Claims> claims = jwt.verify(authorization.substring(BEARER.length()).trim());
            if (claims.isEmpty()) {
                res.sendError(HttpStatus.UNAUTHORIZED.value(), "Nieprawidłowy lub wygasły token");
                return;
            }
            user = users.findById(claims.get().userId());
        } else if (demoHeaderEnabled && demoId != null && !demoId.isBlank()) {
            try {
                user = users.findById(Long.parseLong(demoId.trim()));
            } catch (NumberFormatException e) {
                res.sendError(HttpStatus.BAD_REQUEST.value(), "Nieprawidłowy " + DEMO_HEADER);
                return;
            }
        } else {
            chain.doFilter(req, res);
            return;
        }
        if (user.isEmpty()) {
            res.sendError(HttpStatus.UNAUTHORIZED.value(), "Nieznany użytkownik");
            return;
        }
        req.setAttribute(ATTR, user.get());
        chain.doFilter(req, res);
    }
}
