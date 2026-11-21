package pl.gov.kprm.oceny.service;

import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.UserRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

/** Imiona i nazwiska wielu osób jednym zapytaniem (zamiast findById w pętli — N+1). */
final class UserNames {
    private UserNames() {
    }

    static Map<Long, String> byId(UserRepository users, Collection<Long> ids) {
        return users.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
    }
}
