package pl.gov.kprm.oceny.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.gov.kprm.oceny.domain.DgDecision;
import pl.gov.kprm.oceny.rules.GradeRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Requests {
    private Requests() {
    }

    /** code — kryterium z katalogu schematu; albo name — kryterium własne. */
    public record CriterionInput(String code, String name) {
    }

    public record SetCriteriaRequest(@NotNull List<CriterionInput> additional) {
    }

    public record NoteRequest(@NotBlank String content, boolean isSharedAfterEval) {
    }

    /** submit=false zapisuje szkic (EVALUATION_DRAFT), submit=true waliduje i przekazuje do zapoznania. */
    public record EvaluateRequest(@NotNull Map<String, Integer> scores, String justification, String iprzNotes,
                                  GradeRequest gradeRequest, boolean submit) {
    }

    public record RefusalRequest(@NotBlank String reason, @NotNull LocalDate date) {
    }

    public record AppealRequest(@NotBlank String content) {
    }

    public record AppealDecisionRequest(@NotNull DgDecision decision, String comment, Map<String, Integer> scores,
                                        String justification, String iprzNotes) {
    }

    public record AbsenceRequest(@NotNull Long userId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                 String reason) {
    }

    public record HandoverRequest(@NotNull Long employeeId, @NotNull Long newEvaluatorId) {
    }

    public record CreateSchemaRequest(@NotBlank String versionCode, @NotNull LocalDate effectiveFrom,
                                      @Min(1) int periodMonths, @NotNull JsonNode config) {
    }
}
