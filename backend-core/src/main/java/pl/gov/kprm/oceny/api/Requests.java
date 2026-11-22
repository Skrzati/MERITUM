package pl.gov.kprm.oceny.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.gov.kprm.oceny.domain.CycleStatus;
import pl.gov.kprm.oceny.domain.DgDecision;
import pl.gov.kprm.oceny.domain.EmploymentStatus;
import pl.gov.kprm.oceny.domain.IprzItem;
import pl.gov.kprm.oceny.domain.RescheduleMode;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.SheetType;
import pl.gov.kprm.oceny.domain.SignatureMethod;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.GradeRequest;
import pl.gov.kprm.oceny.rules.PerformanceLevel;
import pl.gov.kprm.oceny.service.CycleWorkflowService.PreviousEvaluationInput;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Requests {
    private Requests() {
    }

    /** sheetType opcjonalny (domyślnie B dla kierujących, C dla pozostałych); startDate domyślnie dziś. */
    public record CreateCycleRequest(@NotNull Long employeeId, SheetType sheetType, LocalDate startDate,
                                     boolean reevaluation, Long evaluatorId, @Size(max = 100) String place) {
    }

    /** Lista pracowników albo komórka organizacyjna; arkusz domyślny per osoba (§ 3), termin z najnowszego schematu. */
    public record BulkCreateRequest(@Size(max = 500) List<Long> employeeIds, @Size(max = 255) String department,
                                    LocalDate startDate) {
    }

    public record CycleDetailsRequest(@Size(max = 100) String place, LocalDate startDate, LocalDate dueDate,
                                      @Size(max = 1000) String reason) {
    }

    public record AdminCorrectionRequest(CycleStatus targetStatus, @NotBlank @Size(max = 2000) String reason,
                                         LocalDate criteriaInterviewDate, LocalDate criteriaConfirmedOn,
                                         LocalDate evaluationInterviewDate, LocalDate evaluatedOn,
                                         LocalDate acknowledgedAt, @Size(max = 100) String place,
                                         LocalDate startDate, LocalDate dueDate) {
    }

    public record UpdateEmployeeRequest(@Size(max = 100) String firstName, @Size(max = 150) String lastName,
                                        @Size(max = 255) String department, @Size(max = 255) String positionTitle,
                                        Role role, Boolean civilServant, EmploymentStatus employmentStatus,
                                        Integer civilServiceGrade,
                                        Integer appointmentYear, LocalDate lastGradeDate, Long supervisorId,
                                        @Size(max = 10000) String jobDescription, LocalDate employmentStartDate,
                                        java.util.Set<Role> secondaryRoles) {
    }

    public record HistoryRequest(@NotNull EvaluationOutcome outcome, @NotNull PerformanceLevel level,
                                 @NotNull LocalDate evaluatedOn) {
    }

    public record SelfPrepRequest(@Size(max = 5000) String achievements, @Size(max = 5000) String challenges,
                                  @Size(max = 5000) String developmentNeeds, boolean sharedWithEvaluator) {
    }

    public record CheckinRequest(LocalDate conductedOn, @NotBlank @Size(max = 255) String topic,
                                 @NotBlank @Size(max = 5000) String summary, @Size(max = 2000) String nextSteps,
                                 boolean sharedWithEmployee) {
    }

    /** POST: actionTitle i formType wymagane (walidacja w kontrolerze); PATCH: dowolny podzbiór pól. */
    public record IprzItemRequest(@Size(max = 16) String criterionCode, @Size(max = 500) String actionTitle,
                                  IprzItem.FormType formType, LocalDate targetDate, IprzItem.Status status) {
        public IprzItem toItem() {
            IprzItem i = new IprzItem();
            i.setCriterionCode(criterionCode);
            i.setActionTitle(actionTitle);
            i.setFormType(formType);
            i.setTargetDate(targetDate);
            i.setStatus(status);
            return i;
        }
    }

    public record SubstitutionRequest(@NotNull Long evaluatorId, @NotNull Long deputyId, @NotNull LocalDate startDate,
                                      @NotNull LocalDate endDate, @NotBlank @Size(max = 500) String reason) {
    }

    /** code — kryterium z katalogu schematu; albo name — kryterium własne (o ile schemat na to pozwala). */
    public record CriterionInput(String code, String name) {
    }

    /** interviewDate — data rozmowy rozpoczynającej (§ 5 ust. 1); domyślnie dziś. */
    /** expectations — kod kryterium → ustalone oczekiwania na poziomie 5 pkt (§ 5 ust. 1 pkt 3); opcjonalne. */
    public record SetCriteriaRequest(@NotNull List<CriterionInput> additional, LocalDate interviewDate,
                                     Map<String, @Size(max = 2000) String> expectations) {
    }

    public record NoteRequest(@NotBlank String content, boolean isSharedAfterEval) {
    }

    /**
     * submit=false zapisuje szkic (EVALUATION_DRAFT), submit=true waliduje i przekazuje do zapoznania.
     * Ocena po terminie nie jest blokowana — delayReason trafia do arkusza i audytu.
     */
    public record EvaluateRequest(@NotNull Map<String, Integer> scores, String justification, String iprzNotes,
                                  GradeRequest gradeRequest, LocalDate evaluationInterviewDate, boolean submit,
                                  String delayReason, SignatureMethod signatureMethod,
                                  PreviousEvaluationInput previousEvaluation) {
    }

    /** paperSignedOn wymagane w trybie HYBRID_PAPER_SCAN (także dawne PAPER_HYBRID), scanReference opcjonalne. */
    public record AcknowledgeRequest(SignatureMethod signatureMethod, LocalDate paperSignedOn,
                                     @Size(max = 255) String scanReference) {
    }

    public record RefusalRequest(@NotBlank String reason, @NotNull LocalDate date) {
    }

    public record AppealRequest(@NotBlank String content) {
    }

    public record CourtAppealRequest(@NotNull LocalDate filedOn, @Size(max = 100) String caseReference) {
    }

    public record CourtAppealCloseRequest(@NotBlank @Size(max = 2000) String outcome) {
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

    /** Konto lokalne (tryb Mały Urząd): login = adres e-mail konta. */
    public record LocalLoginRequest(@NotBlank @Size(max = 255) String login, @NotBlank @Size(max = 128) String password) {
    }

    public record SecondaryRolesRequest(java.util.Set<Role> secondaryRoles) {
    }

    public record SetPasswordRequest(@NotBlank @Size(max = 128) String password) {
    }

    /** Ręczne dodanie konta (/admin): officeId wymagany dla administratora KPRM, kadry — zawsze własny urząd. */
    public record CreateAccountRequest(Long officeId, @NotBlank @Size(max = 100) String firstName,
                                       @NotBlank @Size(max = 150) String lastName,
                                       @NotBlank @Size(max = 255) String email, @NotNull Role role,
                                       EmploymentStatus employmentStatus, Long supervisorId,
                                       @Size(max = 255) String department, @Size(max = 255) String positionTitle,
                                       @Size(max = 128) String password, java.util.Set<Role> secondaryRoles) {
    }

    public record PrivateNoteRequest(Long cycleId, @NotBlank @Size(max = 10000) String content) {
    }

    public record SurveyQuestionRequest(@NotBlank @Size(max = 500) String text) {
    }

    public record SurveyAnswersRequest(@NotNull Map<String, @Size(max = 5000) String> answers) {
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
