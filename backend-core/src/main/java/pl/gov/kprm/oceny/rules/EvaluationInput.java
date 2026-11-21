package pl.gov.kprm.oceny.rules;

import java.util.List;
import java.util.Map;

public record EvaluationInput(List<Criterion> criteria, Map<String, Integer> scores, String justification,
                              String iprzNotes, GradeRequest gradeRequest, boolean civilServant,
                              Integer civilServiceGrade) {
}
