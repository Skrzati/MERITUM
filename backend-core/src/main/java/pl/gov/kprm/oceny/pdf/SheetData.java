package pl.gov.kprm.oceny.pdf;

import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.Office;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.rules.Criterion;
import pl.gov.kprm.oceny.rules.GradeRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Komplet danych potrzebnych do wydruku arkusza oceny. */
public record SheetData(Office office, User employee, User evaluator, EvaluationCycle cycle, String sheetDescription,
                        List<Criterion> criteria, Map<String, Integer> scores, GradeRequest gradeRequest,
                        LocalDate criteriaSetOn, LocalDate criteriaConfirmedOn) {

    /** Część III tylko dla urzędnika służby cywilnej, który ma złożony wniosek o stopień. */
    public boolean includesGradeRequest() {
        return gradeRequest != null && gradeRequest.requested() && employee.isCivilServant();
    }
}
