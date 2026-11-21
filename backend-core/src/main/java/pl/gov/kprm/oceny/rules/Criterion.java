package pl.gov.kprm.oceny.rules;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Criterion(String code, String name, boolean mandatory) {
}
