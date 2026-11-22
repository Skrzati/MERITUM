package pl.gov.kprm.oceny.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code users.secondary_roles}: role rozdzielone przecinkami, pusty napis = brak ról dodatkowych. */
@Converter
public class RoleSetConverter implements AttributeConverter<Set<Role>, String> {
    @Override
    public String convertToDatabaseColumn(Set<Role> roles) {
        return roles == null ? "" : roles.stream().sorted().map(Role::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<Role> convertToEntityAttribute(String value) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (value != null && !value.isBlank()) {
            Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).map(Role::valueOf).forEach(roles::add);
        }
        return roles;
    }
}
