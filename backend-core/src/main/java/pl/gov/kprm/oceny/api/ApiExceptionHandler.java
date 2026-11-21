package pl.gov.kprm.oceny.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.gov.kprm.oceny.rules.RuleViolationException;

/** Błędy w formacie RFC 7807. Naruszenia reguł prawnych -> 422 z listą naruszeń. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RuleViolationException.class)
    public ProblemDetail ruleViolation(RuleViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Arkusz narusza reguły oceny okresowej");
        problem.setProperty("violations", e.getViolations());
        if (e.getResult() != null) {
            problem.setProperty("result", e.getResult());
        }
        return problem;
    }
}
