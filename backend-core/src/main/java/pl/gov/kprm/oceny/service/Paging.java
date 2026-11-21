package pl.gov.kprm.oceny.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Opcjonalne stronicowanie list: brak page i size = pełna lista (zgodność wsteczna). */
public final class Paging {
    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 50;

    private Paging() {
    }

    public static Optional<PageRequest> of(Integer page, Integer size, Sort sort) {
        if (page == null && size == null) {
            return Optional.empty();
        }
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_PAGE_SIZE : size;
        if (p < 0 || s < 1 || s > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Parametry stronicowania: page ≥ 0, size 1–" + MAX_PAGE_SIZE);
        }
        return Optional.of(PageRequest.of(p, s, sort));
    }
}
