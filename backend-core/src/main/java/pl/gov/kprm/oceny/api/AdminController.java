package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Dtos.SchemaDto;
import pl.gov.kprm.oceny.api.Requests.CreateSchemaRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.SchemaAdminService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/schemas")
@RequiredArgsConstructor
public class AdminController {
    private final SchemaAdminService schemas;

    @GetMapping
    public List<SchemaDto> list(@CurrentUser User user) {
        return schemas.list(user).stream().map(SchemaDto::of).toList();
    }

    /** Pakiet reguł jako załącznik meritum-rules-&lt;versionCode&gt;.json. */
    @GetMapping("/{id}/package")
    public ResponseEntity<Map<String, Object>> rulePackage(@CurrentUser User user, @PathVariable Long id) {
        Map<String, Object> pkg = schemas.rulePackage(user, id);
        String safeCode = String.valueOf(pkg.get("versionCode")).replaceAll("[^A-Za-z0-9._-]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("meritum-rules-" + safeCode + ".json").build().toString())
                .body(pkg);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchemaDto create(@CurrentUser User user, @Valid @RequestBody CreateSchemaRequest req) {
        return SchemaDto.of(schemas.create(user, req.versionCode(), req.effectiveFrom(), req.periodMonths(),
                req.config()));
    }
}
