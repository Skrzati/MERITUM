package pl.gov.kprm.oceny.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.gov.kprm.oceny.domain.DgDecision;
import pl.gov.kprm.oceny.domain.RescheduleMode;
import pl.gov.kprm.oceny.domain.SheetType;
import pl.gov.kprm.oceny.domain.SignatureMethod;
import pl.gov.kprm.oceny.rules.GradeRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Requests {
    private Requests() {
    }

    /** sheetType opcjonalny (domyślnie B dla kierujących, C dla pozostałych); startDate domyślnie dziś. */
    public record CreateCycleRequest(@NotNull Long employeeId, SheetType sheetType, LocalDate startDate,
                                     boolean reevaluation) {
    }

    /** code — kryterium z katalogu schematu; albo name — kryterium własne (o ile schemat na to pozwala). */
    public record CriterionInput(String code, String name) {
    }

    /** interviewDate — data rozmowy rozpoczynającej (§ 5 ust. 1); domyślnie dziś. */
    public record SetCriteriaRequest(@NotNull List<CriterionInput> additional, LocalDate interviewDate) {
    }

    public record NoteRequest(@NotBlank String content, boolean isSharedAfterEval) {
    }

    /**
     * submit=false zapisuje szkic (EVALUATION_DRAFT), submit=true waliduje i przekazuje do zapoznania.
     * Ocena po terminie nie jest blokowana — delayReason trafia do arkusza i audytu.
     */
    public record EvaluateRequest(@NotNull Map<String, Integer> scores, String justification, String iprzNotes,
                                  GradeRequest gradeRequest, LocalDate evaluationInterviewDate, boolean submit,
                                  String delayReason, SignatureMethod signatureMethod) {
    }

    public record AcknowledgeRequest(SignatureMethod signatureMethod) {
    }

    public record RefusalRequest(@NotBlank String reason, @NotNull LocalDate date) {
    }

    public record AppealRequest(@NotBlank String content) {
    }

    public record AppealDecisionRequest(@NotNull DgDecision decision, String comment, Map<String, Integer> scores,
                                        String justification, String iprzNotes) {
    }

    public record RescheduleBody(@NotNull RescheduleMode mode, @NotNull LocalDate newDueDate, @NotBlank String reason) {
    }

    public record RescheduleDecision(boolean accept, String comment) {
    }

    public record ScopeChangeRequest(@NotNull LocalDate changeDate, String newPositionTitle,
                                     @NotBlank String description, boolean significant) {
    }

    public record WellbeingRequest(@Min(1) @Max(5) int workload, @Min(1) @Max(5) int energy,
                                   @Min(1) @Max(5) int support, boolean wantsConversation) {
    }

    public record GovLoginRequest(@NotNull Long userId) {
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
