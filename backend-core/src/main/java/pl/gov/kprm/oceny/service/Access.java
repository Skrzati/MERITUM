package pl.gov.kprm.oceny.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;

import java.util.Arrays;

public final class Access {
    private Access() {
    }

    public static void requireRole(User user, Role... roles) {
        if (Arrays.stream(roles).noneMatch(r -> r == user.getRole())) {
            throw forbidden("Wymagana rola: " + Arrays.toString(roles));
        }
    }

    public static void requireSameOffice(User user, Long officeId) {
        if (!user.getOfficeId().equals(officeId)) {
            throw forbidden("Brak dostępu do danych innego urzędu");
        }
    }

    public static ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }

    public static ResponseStatusException notFound(String what, Object id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " " + id + " nie istnieje");
    }

    public static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
