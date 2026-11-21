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

/** Komplet danych potrzebnych do wydruku arkusza oceny (wzór z załączników nr 2–4 rozporządzenia). */
public record SheetData(Office office, User employee, User evaluator, EvaluationCycle cycle,
                        List<Criterion> criteria, Map<String, Integer> scores, GradeRequest gradeRequest,
                        EvaluationHistory previousEvaluation, List<IprzItem> iprzItems) {

    /** Część III: urzędnik SC, ocena pozytywna i dołączony wniosek (przypis 16 do wzoru). */
    public boolean includesGradeRequest() {
        return gradeRequest != null && gradeRequest.requested() && employee.isCivilServant()
                && cycle.getFinalResult() == EvaluationOutcome.POSITIVE;
    }
}
