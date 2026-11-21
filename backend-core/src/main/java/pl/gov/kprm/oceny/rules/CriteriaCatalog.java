package pl.gov.kprm.oceny.rules;

import com.fasterxml.jackson.databind.JsonNode;
import pl.gov.kprm.oceny.domain.SheetType;

import java.util.*;

/**
 * Katalog kryteriów wersji schematu (§ 4-5): obowiązkowe zależne od arkusza
 * + maksymalnie {@code maxAdditional} dodatkowych z katalogu (załącznik nr 1).
 */
public record CriteriaCatalog(Map<String, Criterion> criteria, Map<SheetType, List<String>> mandatoryBySheet,
                              int maxAdditional, boolean allowCustom) {
    public static final int DEFAULT_MAX_ADDITIONAL = 3;
    private static final String CUSTOM_PREFIX = "D";

    /** Parsuje i waliduje config_json schematu. */
    public static CriteriaCatalog from(JsonNode config) {
        List<String> violations = new ArrayList<>();
        Map<String, Criterion> criteria = new LinkedHashMap<>();
        for (JsonNode n : config.path("criteria")) {
            String code = normalize(n.path("code").asText(""));
            String name = n.path("name").asText("").trim();
            if (code.isEmpty() || name.isEmpty()) {
                violations.add("Każde kryterium katalogu wymaga kodu i nazwy");
            } else if (criteria.putIfAbsent(code, new Criterion(code, name, false)) != null) {
                violations.add("Zduplikowany kod kryterium w katalogu: " + code);
            }
        }
        if (criteria.isEmpty()) {
            violations.add("config.criteria musi być niepustą tablicą kryteriów");
        }

        Map<SheetType, List<String>> mandatory = new EnumMap<>(SheetType.class);
        JsonNode bySheet = config.path("mandatoryBySheet");
        for (SheetType sheet : SheetType.values()) {
            JsonNode codes = bySheet.path(sheet.name());
            if (!codes.isArray() || codes.isEmpty()) {
                violations.add("config.mandatoryBySheet." + sheet + " musi być niepustą tablicą kodów");
                continue;
            }
            List<String> list = new ArrayList<>();
            for (JsonNode c : codes) {
                String code = normalize(c.asText());
                if (!criteria.containsKey(code)) {
                    violations.add("Arkusz " + sheet + ": kryterium obowiązkowe " + code + " spoza katalogu");
                }
                list.add(code);
            }
            mandatory.put(sheet, List.copyOf(list));
        }

        JsonNode rules = config.path("criteriaRules");
        int maxAdditional = rules.path("maxAdditional").asInt(DEFAULT_MAX_ADDITIONAL);
        if (maxAdditional < 0) {
            violations.add("criteriaRules.maxAdditional nie może być ujemne");
        }
        if (!violations.isEmpty()) {
            throw new RuleViolationException(violations);
        }
        return new CriteriaCatalog(criteria, mandatory, maxAdditional, rules.path("allowCustom").asBoolean(false));
    }

    public List<Criterion> mandatoryFor(SheetType sheet) {
        return mandatoryBySheet.get(sheet).stream()
                .map(code -> new Criterion(code, criteria.get(code).name(), true))
                .toList();
    }

    /** Zwraca pełny zestaw kryteriów arkusza: obowiązkowe + wybrane dodatkowe. */
    public List<Criterion> select(SheetType sheet, List<Criterion> requested) {
        List<String> violations = new ArrayList<>();
        if (requested.size() > maxAdditional) {
            violations.add("Można wybrać maksymalnie " + maxAdditional + " kryteria dodatkowe");
        }
        List<Criterion> selected = new ArrayList<>(mandatoryFor(sheet));
        Set<String> mandatoryCodes = new HashSet<>(mandatoryBySheet.get(sheet));
        Set<String> seen = new HashSet<>();
        int customCount = 0;
        for (Criterion r : requested) {
            String code = r.code() == null ? "" : normalize(r.code());
            if (code.isEmpty()) {
                String name = r.name() == null ? "" : r.name().trim();
                if (!allowCustom) {
                    violations.add("Kryterium „" + name + "” spoza katalogu — dozwolone są wyłącznie kryteria z załącznika nr 1");
                } else if (name.isEmpty()) {
                    violations.add("Kryterium własne wymaga nazwy");
                } else if (!seen.add("name:" + name.toLowerCase(Locale.ROOT))) {
                    violations.add("Kryterium „" + name + "” powtarza się");
                } else {
                    selected.add(new Criterion(CUSTOM_PREFIX + (++customCount), name, false));
                }
                continue;
            }
            Criterion fromCatalog = criteria.get(code);
            if (fromCatalog == null) {
                violations.add("Kryterium " + code + " nie należy do katalogu kryteriów");
            } else if (mandatoryCodes.contains(code)) {
                violations.add("Kryterium „" + fromCatalog.name() + "” jest już obowiązkowe dla arkusza " + sheet);
            } else if (!seen.add(code)) {
                violations.add("Kryterium „" + fromCatalog.name() + "” powtarza się");
            } else {
                selected.add(fromCatalog);
            }
        }
        if (!violations.isEmpty()) {
            throw new RuleViolationException(violations);
        }
        return selected;
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
