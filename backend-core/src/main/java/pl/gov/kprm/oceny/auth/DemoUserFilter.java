package pl.gov.kprm.oceny.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.gov.kprm.oceny.repository.UserRepository;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class DemoUserFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Demo-User-Id";
    public static final String ATTR = DemoUserFilter.class.getName() + ".user";

    private final UserRepository users;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(HEADER);
        if (header != null && !header.isBlank()) {
            try {
                var user = users.findById(Long.parseLong(header.trim()));
                if (user.isEmpty()) {
                    res.sendError(HttpStatus.UNAUTHORIZED.value(), "Unknown demo user");
                    return;
                }
                req.setAttribute(ATTR, user.get());
            } catch (NumberFormatException e) {
                res.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid " + HEADER);
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
