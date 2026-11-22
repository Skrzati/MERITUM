package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Dtos.PrivateNoteDto;
import pl.gov.kprm.oceny.api.Requests.PrivateNoteRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.PrivateNoteService;

import java.util.List;

/** Prywatny dziennik pracownika — wyłącznie własne notatki bieżącego użytkownika. */
@RestController
@RequestMapping("/api/private-notes")
@RequiredArgsConstructor
public class PrivateNoteController {
    private final PrivateNoteService notes;

    @GetMapping
    public List<PrivateNoteDto> list(@CurrentUser User user, @RequestParam(required = false) Long cycleId) {
        return notes.list(user, cycleId).stream().map(PrivateNoteDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrivateNoteDto create(@CurrentUser User user, @Valid @RequestBody PrivateNoteRequest req) {
        return PrivateNoteDto.of(notes.create(user, req.cycleId(), req.content()));
    }

    @PutMapping("/{id}")
    public PrivateNoteDto update(@CurrentUser User user, @PathVariable Long id, @Valid @RequestBody PrivateNoteRequest req) {
        return PrivateNoteDto.of(notes.update(user, id, req.content()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser User user, @PathVariable Long id) {
        notes.delete(user, id);
    }
}
