package pl.gov.kprm.oceny.rules;

import lombok.Getter;

import java.util.List;

@Getter
public class RuleViolationException extends RuntimeException {
    private final List<String> violations;
    private final transient EvaluationResult result;

    public RuleViolationException(List<String> violations) {
        this(violations, null);
    }

    public RuleViolationException(List<String> violations, EvaluationResult result) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
        this.result = result;
    }
}
