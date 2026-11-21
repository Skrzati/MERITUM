package pl.gov.kprm.oceny.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.PerformanceLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "evaluation_cycles")
@Getter @Setter @NoArgsConstructor
public class EvaluationCycle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long officeId;
    private Long employeeId;
    private Long evaluatorId;
    private Long schemaVersionId;
    @Enumerated(EnumType.STRING)
    private SheetType sheetType;
    @Enumerated(EnumType.STRING)
    private CycleStatus status;
    private LocalDate startDate;
    private LocalDate dueDate;
    private boolean isReevaluation;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode criteriaJson;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode scoresJson;
    private BigDecimal averageScore;
    @Enumerated(EnumType.STRING)
    private PerformanceLevel performanceLevel;
    @Enumerated(EnumType.STRING)
    private EvaluationOutcome finalResult;
    private String resultExplanation;
    private String justification;
    private String iprzNotes;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode gradeRequestJson;
    private LocalDate acknowledgedAt;
    private String refusalReason;
    private LocalDate refusalDate;
    private LocalDate appealDeadline;
    private String appealText;
    private OffsetDateTime appealedAt;
    @Enumerated(EnumType.STRING)
    private DgDecision dgDecision;
    private String dgComment;
    private OffsetDateTime dgDecidedAt;
    private LocalDate criteriaInterviewDate;
    private LocalDate criteriaConfirmedOn;
    private LocalDate evaluationInterviewDate;
    private LocalDate evaluatedOn;
    private String place;
    private LocalDate appealDecisionDeadline;
    private String delayReason;
    @Enumerated(EnumType.STRING)
    private SignatureMethod signatureMethod;
    @Enumerated(EnumType.STRING)
    private EvaluationOutcome prevEvaluationOutcome;
    @Enumerated(EnumType.STRING)
    private PerformanceLevel prevEvaluationLevel;
    private LocalDate prevEvaluationDate;
    /** Art. 108: IPRZ ustala przełożony, zatwierdza kierownictwo (DG / kierownik urzędu), oceniany się zapoznaje. */
    @Enumerated(EnumType.STRING)
    private IprzStatus iprzStatus = IprzStatus.DRAFT;
    private Long iprzApprovedBy;
    private OffsetDateTime iprzApprovedAt;
    private OffsetDateTime iprzEmployeeAckAt;

    public enum IprzStatus { DRAFT, PROPOSED, APPROVED }
}
