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
import pl.gov.kprm.oceny.rules.RuleViolationException;
import pl.gov.kprm.oceny.rules.SchemaRules;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static pl.gov.kprm.oceny.service.Access.conflict;
import static pl.gov.kprm.oceny.service.Access.requireRole;

/** „Prawo jako kod”: nowe wersje schematu. Istniejące cykle zachowują swoje schema_version_id (§ 13). */
@Service
@Transactional
@RequiredArgsConstructor
public class SchemaAdminService {
    public static final String RULE_PACKAGE_FORMAT = "meritum-rules/1";

    private final SchemaVersionRepository schemas;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<SchemaVersion> list(User admin) {
        requireRole(admin, Role.KPRM_ADMIN);
        return schemas.findAllByOrderByEffectiveFromDesc();
    }

    /** Odczyt pojedynczej wersji (katalog kryteriów) — dostępny dla każdego zalogowanego użytkownika. */
    @Transactional(readOnly = true)
    public SchemaVersion get(Long id) {
        return schemas.findById(id).orElseThrow(() -> Access.notFound("Schemat", id));
    }

    /** Pakiet reguł („prawo jako kod”) do przeniesienia między instalacjami w urzędach. */
    @Transactional(readOnly = true)
    public Map<String, Object> rulePackage(User admin, Long id) {
        requireRole(admin, Role.KPRM_ADMIN);
        SchemaVersion s = get(id);
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("format", RULE_PACKAGE_FORMAT);
        pkg.put("versionCode", s.getVersionCode());
        pkg.put("effectiveFrom", s.getEffectiveFrom());
        pkg.put("periodMonths", s.getPeriodMonths());
        pkg.put("config", s.getConfigJson());
        return pkg;
    }

    public SchemaVersion create(User admin, String versionCode, LocalDate effectiveFrom, int periodMonths,
                                JsonNode config) {
        requireRole(admin, Role.KPRM_ADMIN);
        if (schemas.existsByVersionCode(versionCode)) {
            throw conflict("Wersja schematu " + versionCode + " już istnieje");
        }
        CriteriaCatalog.from(config);
        List<String> ruleViolations = SchemaRules.violations(config);
        if (!ruleViolations.isEmpty()) {
            throw new RuleViolationException(ruleViolations);
        }
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
