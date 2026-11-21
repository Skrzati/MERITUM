package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.PerformanceLevel;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "evaluation_history")
@Getter @Setter @NoArgsConstructor
public class EvaluationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long officeId;
    private Long cycleId;
    @Enumerated(EnumType.STRING)
    private EvaluationOutcome outcome;
    @Enumerated(EnumType.STRING)
    private PerformanceLevel level;
    private LocalDate evaluatedOn;
    @Enumerated(EnumType.STRING)
    private HistorySource source;
    private Long enteredBy;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
