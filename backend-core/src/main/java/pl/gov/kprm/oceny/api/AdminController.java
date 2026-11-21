package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Dtos.SchemaDto;
import pl.gov.kprm.oceny.api.Requests.CreateSchemaRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.SchemaAdminService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/schemas")
@RequiredArgsConstructor
public class AdminController {
    private final SchemaAdminService schemas;

    @GetMapping
    public List<SchemaDto> list(@CurrentUser User user) {
        return schemas.list(user).stream().map(SchemaDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchemaDto create(@CurrentUser User user, @Valid @RequestBody CreateSchemaRequest req) {
        return SchemaDto.of(schemas.create(user, req.versionCode(), req.effectiveFrom(), req.periodMonths(),
                req.config()));
    }
}
