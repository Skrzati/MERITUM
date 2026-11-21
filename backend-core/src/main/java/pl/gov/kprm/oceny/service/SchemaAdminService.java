package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.SchemaVersion;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.SchemaVersionRepository;
import pl.gov.kprm.oceny.rules.CriteriaCatalog;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static pl.gov.kprm.oceny.service.Access.conflict;
import static pl.gov.kprm.oceny.service.Access.requireRole;

/** „Prawo jako kod”: nowe wersje schematu. Istniejące cykle zachowują swoje schema_version_id (§ 13). */
@Service
@Transactional
@RequiredArgsConstructor
public class SchemaAdminService {
    private final SchemaVersionRepository schemas;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<SchemaVersion> list(User admin) {
        requireRole(admin, Role.KPRM_ADMIN);
        return schemas.findAllByOrderByEffectiveFromDesc();
    }

    public SchemaVersion create(User admin, String versionCode, LocalDate effectiveFrom, int periodMonths,
                                JsonNode config) {
        requireRole(admin, Role.KPRM_ADMIN);
        if (schemas.existsByVersionCode(versionCode)) {
            throw conflict("Wersja schematu " + versionCode + " już istnieje");
        }
        CriteriaCatalog.from(config);
        SchemaVersion schema = new SchemaVersion();
        schema.setVersionCode(versionCode);
        schema.setEffectiveFrom(effectiveFrom);
        schema.setPeriodMonths(periodMonths);
        schema.setConfigJson(config);
        schema.setCreatedBy(admin.getId());
        SchemaVersion saved = schemas.saveAndFlush(schema);
        audit.log(admin, admin.getOfficeId(), "SCHEMA_PUBLISHED", AuditService.SCHEMA, saved.getId(),
                Map.of("versionCode", versionCode, "periodMonths", periodMonths, "effectiveFrom", effectiveFrom));
        return saved;
    }
}
