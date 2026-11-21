package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gov.kprm.oceny.api.Dtos.SchemaDto;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.SchemaAdminService;

@RestController
@RequestMapping("/api/schemas")
@RequiredArgsConstructor
public class SchemaController {
    private final SchemaAdminService schemas;

    @GetMapping("/{id}")
    public SchemaDto get(@PathVariable Long id, @CurrentUser User user) {
        return SchemaDto.of(schemas.get(id));
    }
}
