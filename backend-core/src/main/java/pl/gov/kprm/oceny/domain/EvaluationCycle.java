package pl.gov.kprm.oceny.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode criteriaJson;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode scoresJson;
    private BigDecimal averageScore;
    private String finalResult;
    private String justification;
    private String iprzNotes;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode gradeRequestJson;
}
