package pl.gov.kprm.oceny.api;

import com.fasterxml.jackson.databind.JsonNode;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.EvaluationResult;
import pl.gov.kprm.oceny.rules.PerformanceLevel;
import pl.gov.kprm.oceny.service.HrService.GdprExport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    public record UserDto(Long id, Long officeId, String fullName, String email, Role role,
                          boolean civilServant, Long supervisorId) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getOfficeId(), u.getFullName(), u.getEmail(), u.getRole(),
                    u.isCivilServant(), u.getSupervisorId());
        }
    }

    public record CycleDto(Long id, Long officeId, Long employeeId, Long evaluatorId, Long schemaVersionId,
                           SheetType sheetType, CycleStatus status, boolean reevaluation, LocalDate startDate,
                           LocalDate dueDate, JsonNode criteria, JsonNode scores, BigDecimal averageScore,
                           PerformanceLevel performanceLevel, EvaluationOutcome finalResult,
                           String resultExplanation, String justification, String iprzNotes, JsonNode gradeRequest,
                           LocalDate acknowledgedAt, String refusalReason, LocalDate refusalDate,
                           LocalDate appealDeadline, String appealText, OffsetDateTime appealedAt,
                           DgDecision dgDecision, String dgComment, OffsetDateTime dgDecidedAt) {
        public static CycleDto of(EvaluationCycle c) {
            return new CycleDto(c.getId(), c.getOfficeId(), c.getEmployeeId(), c.getEvaluatorId(),
                    c.getSchemaVersionId(), c.getSheetType(), c.getStatus(), c.isReevaluation(), c.getStartDate(),
                    c.getDueDate(), c.getCriteriaJson(), c.getScoresJson(), c.getAverageScore(),
                    c.getPerformanceLevel(), c.getFinalResult(), c.getResultExplanation(), c.getJustification(),
                    c.getIprzNotes(), c.getGradeRequestJson(), c.getAcknowledgedAt(), c.getRefusalReason(),
                    c.getRefusalDate(), c.getAppealDeadline(), c.getAppealText(), c.getAppealedAt(),
                    c.getDgDecision(), c.getDgComment(), c.getDgDecidedAt());
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

    public record AuditDto(Long id, Long actorId, String action, String entityType, Long entityId,
                           JsonNode payload, OffsetDateTime createdAt) {
        public static AuditDto of(AuditLog a) {
            return new AuditDto(a.getId(), a.getActorId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                    a.getPayloadJson(), a.getCreatedAt());
        }
    }

    public record SchemaDto(Long id, String versionCode, LocalDate effectiveFrom, int periodMonths,
                            JsonNode config, Long createdBy) {
        public static SchemaDto of(SchemaVersion s) {
            return new SchemaDto(s.getId(), s.getVersionCode(), s.getEffectiveFrom(), s.getPeriodMonths(),
                    s.getConfigJson(), s.getCreatedBy());
        }
    }

    public record GdprExportDto(UserDto user, List<CycleDto> cycles, List<NoteDto> notes,
                                List<AbsenceDto> absences, List<AuditDto> auditLogs) {
        public static GdprExportDto of(GdprExport e) {
            return new GdprExportDto(UserDto.of(e.user()),
                    e.cycles().stream().map(CycleDto::of).toList(),
                    e.notes().stream().map(NoteDto::of).toList(),
                    e.absences().stream().map(AbsenceDto::of).toList(),
                    e.auditLogs().stream().map(AuditDto::of).toList());
        }
    }
}
