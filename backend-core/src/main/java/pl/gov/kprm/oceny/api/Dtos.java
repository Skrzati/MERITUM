package pl.gov.kprm.oceny.api;

import com.fasterxml.jackson.databind.JsonNode;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.EvaluationResult;
import pl.gov.kprm.oceny.rules.PerformanceLevel;
import pl.gov.kprm.oceny.rules.AppealAuthority;
import pl.gov.kprm.oceny.service.CycleWorkflowService.BulkSkip;
import pl.gov.kprm.oceny.service.EvaluationHistoryService.PreviousEvaluation;
import pl.gov.kprm.oceny.service.HrService.GdprExport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    /**
     * Konto do wyboru w logowaniu demo (endpoint publiczny) — bez e-maila i dat kadrowych; pola stanowiska i stopnia
     * są potrzebne interfejsowi (podpowiedź kryteriów, blokada wniosku przy 9. stopniu).
     */
    public record DemoUserDto(Long id, Long officeId, String fullName, Role role, boolean civilServant, Long supervisorId,
                              String department, String positionTitle, Integer civilServiceGrade, String officeName,
                              String officeCity, boolean officeHasDgRole) {
        public static DemoUserDto of(User u, Office office) {
            return new DemoUserDto(u.getId(), u.getOfficeId(), u.getFullName(), u.getRole(), u.isCivilServant(),
                    u.getSupervisorId(), u.getDepartment(), u.getPositionTitle(), u.getCivilServiceGrade(),
                    office == null ? null : office.getName(), office == null ? null : office.getCity(),
                    office == null || office.isHasDgRole());
        }
    }

    public record UserDto(Long id, Long officeId, String fullName, String firstName, String lastName, String email,
                          Role role, boolean civilServant, Long supervisorId, String department, String positionTitle,
                          Integer appointmentYear, Integer civilServiceGrade, LocalDate lastGradeDate, boolean active) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getOfficeId(), u.getFullName(), u.getFirstName(), u.getLastName(),
                    u.getEmail(), u.getRole(), u.isCivilServant(), u.getSupervisorId(), u.getDepartment(),
                    u.getPositionTitle(), u.getAppointmentYear(), u.getCivilServiceGrade(), u.getLastGradeDate(),
                    u.isActive());
        }
    }

    public record SubstitutionDto(Long id, Long officeId, Long evaluatorId, Long deputyId, LocalDate startDate,
                                  LocalDate endDate, String reason, Long createdBy, boolean activeToday) {
        public static SubstitutionDto of(EvaluatorSubstitution s, LocalDate today) {
            return new SubstitutionDto(s.getId(), s.getOfficeId(), s.getEvaluatorId(), s.getDeputyId(), s.getStartDate(),
                    s.getEndDate(), s.getReason(), s.getCreatedBy(), s.activeOn(today));
        }
    }

    public record BulkCreateDto(List<CycleDto> created, List<BulkSkip> skipped) {
    }

    /**
     * @param criteriaDeadline       § 7 — termin ustalenia kryteriów (30 dni / 7 dni po ocenie negatywnej)
     * @param mandatoryGradeEligible art. 89 ust. 2 — obligatoryjny kolejny stopień służbowy
     */
    public record CycleDto(Long id, Long officeId, Long employeeId, Long evaluatorId, Long schemaVersionId,
                           SheetType sheetType, CycleStatus status, boolean reevaluation, LocalDate startDate,
                           LocalDate dueDate, LocalDate criteriaDeadline, LocalDate criteriaInterviewDate,
                           LocalDate criteriaConfirmedOn, LocalDate evaluationInterviewDate, LocalDate evaluatedOn,
                           String place, JsonNode criteria, JsonNode scores, BigDecimal averageScore,
                           PerformanceLevel performanceLevel, EvaluationOutcome finalResult,
                           String resultExplanation, String justification, String iprzNotes, JsonNode gradeRequest,
                           LocalDate acknowledgedAt, String refusalReason, LocalDate refusalDate,
                           LocalDate appealDeadline, String appealText, OffsetDateTime appealedAt,
                           LocalDate appealDecisionDeadline, DgDecision dgDecision, String dgComment,
                           OffsetDateTime dgDecidedAt, boolean mandatoryGradeEligible,
                           LocalDate mandatoryGradeDeadline, String delayReason, SignatureMethod signatureMethod,
                           long overdueDays, boolean officeHasDgRole, String appealAuthority,
                           PreviousEvaluation previousEvaluation) {
        /** Pola wyliczane: terminy, art. 89 ust. 2, opóźnienie, organ rozpatrujący sprzeciw, poprzednia ocena. */
        public record Facts(LocalDate criteriaDeadline, boolean gradeEligible, LocalDate gradeDeadline, long overdueDays,
                            boolean officeHasDgRole, PreviousEvaluation previousEvaluation) {
            static final Facts NONE = new Facts(null, false, null, 0, true, null);
        }

        public static CycleDto of(EvaluationCycle c, Facts f) {
            return new CycleDto(c.getId(), c.getOfficeId(), c.getEmployeeId(), c.getEvaluatorId(),
                    c.getSchemaVersionId(), c.getSheetType(), c.getStatus(), c.isReevaluation(), c.getStartDate(),
                    c.getDueDate(), f.criteriaDeadline(), c.getCriteriaInterviewDate(), c.getCriteriaConfirmedOn(),
                    c.getEvaluationInterviewDate(), c.getEvaluatedOn(), c.getPlace(), c.getCriteriaJson(),
                    c.getScoresJson(), c.getAverageScore(), c.getPerformanceLevel(), c.getFinalResult(),
                    c.getResultExplanation(), c.getJustification(), c.getIprzNotes(), c.getGradeRequestJson(),
                    c.getAcknowledgedAt(), c.getRefusalReason(), c.getRefusalDate(), c.getAppealDeadline(),
                    c.getAppealText(), c.getAppealedAt(), c.getAppealDecisionDeadline(), c.getDgDecision(),
                    c.getDgComment(), c.getDgDecidedAt(), f.gradeEligible(), f.gradeDeadline(), c.getDelayReason(),
                    c.getSignatureMethod(), f.overdueDays(), f.officeHasDgRole(),
                    AppealAuthority.of(f.officeHasDgRole()).label(), f.previousEvaluation());
        }

        public static CycleDto of(EvaluationCycle c) {
            return of(c, Facts.NONE);
        }
    }

    public record EvaluationResponse(CycleDto cycle, EvaluationResult result) {
    }

    public record NoteDto(Long id, Long cycleId, Long authorId, String content, boolean isSharedAfterEval,
                          OffsetDateTime createdAt) {
        public static NoteDto of(EmployeeNote n) {
            return new NoteDto(n.getId(), n.getCycleId(), n.getAuthorId(), n.getContent(), n.isSharedAfterEval(),
                    n.getCreatedAt());
        }
    }

    public record AbsenceDto(Long id, Long userId, LocalDate startDate, LocalDate endDate, String reason) {
        public static AbsenceDto of(Absence a) {
            return new AbsenceDto(a.getId(), a.getUserId(), a.getStartDate(), a.getEndDate(), a.getReason());
        }
    }

    public record AuditDto(Long id, Long officeId, Long actorId, String action, String entityType, Long entityId,
                           JsonNode payload, OffsetDateTime createdAt) {
        public static AuditDto of(AuditLog a) {
            return new AuditDto(a.getId(), a.getOfficeId(), a.getActorId(), a.getAction(), a.getEntityType(),
                    a.getEntityId(), a.getPayloadJson(), a.getCreatedAt());
        }
    }

    public record SchemaDto(Long id, String versionCode, LocalDate effectiveFrom, int periodMonths,
                            JsonNode config, Long createdBy) {
        public static SchemaDto of(SchemaVersion s) {
            return new SchemaDto(s.getId(), s.getVersionCode(), s.getEffectiveFrom(), s.getPeriodMonths(),
                    s.getConfigJson(), s.getCreatedBy());
        }
    }

    public record HistoryDto(Long id, Long cycleId, EvaluationOutcome outcome, PerformanceLevel level,
                             LocalDate evaluatedOn, HistorySource source) {
        public static HistoryDto of(EvaluationHistory h) {
            return new HistoryDto(h.getId(), h.getCycleId(), h.getOutcome(), h.getLevel(), h.getEvaluatedOn(),
                    h.getSource());
        }
    }

    public record RescheduleDto(Long id, Long cycleId, RescheduleMode mode, int statutePoint, RescheduleStatus status,
                                String reason, LocalDate previousDueDate, LocalDate proposedDueDate,
                                LocalDate newDueDate, String noticeText, Long requestedBy, Long decidedBy,
                                String decisionComment, OffsetDateTime createdAt, OffsetDateTime decidedAt) {
        public static RescheduleDto of(RescheduleRequest r) {
            return new RescheduleDto(r.getId(), r.getCycleId(), r.getMode(), r.getMode().statutePoint(), r.getStatus(),
                    r.getReason(), r.getPreviousDueDate(), r.getProposedDueDate(), r.getNewDueDate(),
                    r.getNoticeText(), r.getRequestedBy(), r.getDecidedBy(), r.getDecisionComment(),
                    r.getCreatedAt(), r.getDecidedAt());
        }
    }

    public record ScopeChangeDto(Long id, Long cycleId, LocalDate changeDate, String newPositionTitle,
                                 String description, boolean significant, ScopeChangeOutcome outcome,
                                 LocalDate actionDeadline, Long recordedBy) {
        public static ScopeChangeDto of(ScopeChange s) {
            return new ScopeChangeDto(s.getId(), s.getCycleId(), s.getChangeDate(), s.getNewPositionTitle(),
                    s.getDescription(), s.isSignificant(), s.getOutcome(), s.getActionDeadline(), s.getRecordedBy());
        }
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, String identityProvider,
                                UserDto user) {
    }

    public record GdprExportDto(UserDto user, List<CycleDto> cycles, List<NoteDto> notes, List<AbsenceDto> absences,
                                List<HistoryDto> evaluationHistory, List<AuditDto> auditLogs) {
        public static GdprExportDto of(GdprExport e) {
            return new GdprExportDto(UserDto.of(e.user()),
                    e.cycles().stream().map(CycleDto::of).toList(),
                    e.notes().stream().map(NoteDto::of).toList(),
                    e.absences().stream().map(AbsenceDto::of).toList(),
                    e.evaluationHistory().stream().map(HistoryDto::of).toList(),
                    e.auditLogs().stream().map(AuditDto::of).toList());
        }
    }
}
