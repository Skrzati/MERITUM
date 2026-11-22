package pl.gov.kprm.oceny.pdf;

import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.EvaluationHistory;
import pl.gov.kprm.oceny.domain.IprzItem;
import pl.gov.kprm.oceny.domain.Office;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.rules.Criterion;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.GradeRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Komplet danych potrzebnych do wydruku arkusza oceny (wzór z załączników nr 2–4 rozporządzenia). */
public record SheetData(Office office, User employee, User evaluator, EvaluationCycle cycle,
                        List<Criterion> criteria, Map<String, Integer> scores, GradeRequest gradeRequest,
                        EvaluationHistory previousEvaluation, List<IprzItem> iprzItems, User evaluatedBy) {

    public SheetData(Office office, User employee, User evaluator, EvaluationCycle cycle, List<Criterion> criteria,
                     Map<String, Integer> scores, GradeRequest gradeRequest, EvaluationHistory previousEvaluation,
                     List<IprzItem> iprzItems) {
        this(office, employee, evaluator, cycle, criteria, scores, gradeRequest, previousEvaluation, iprzItems, null);
    }

    /** Osoba, która sporządziła ocenę (cz. II–III) — zastępca, jeśli to on ją złożył; inaczej przypisany oceniający. */
    public User signingEvaluator() {
        return evaluatedBy != null ? evaluatedBy : evaluator;
    }

    /** Ocenę sporządził zastępca, a nie przypisany oceniający. */
    public boolean signedBySubstitute() {
        return evaluatedBy != null && evaluator != null && !Objects.equals(evaluatedBy.getId(), evaluator.getId());
    }

    /** Część III: urzędnik SC, ocena pozytywna i dołączony wniosek (przypis 16 do wzoru). */
    public boolean includesGradeRequest() {
        return gradeRequest != null && gradeRequest.requested() && employee.isCivilServant()
                && cycle.getFinalResult() == EvaluationOutcome.POSITIVE;
    }
}
