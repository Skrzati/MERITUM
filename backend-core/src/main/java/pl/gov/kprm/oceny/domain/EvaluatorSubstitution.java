package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Czasowe zastępstwo oceniającego: zastępca prowadzi aktywne cykle zastępowanego w okresie [startDate, endDate]. */
@Entity
@Table(name = "evaluator_substitutions")
@Getter @Setter @NoArgsConstructor
public class EvaluatorSubstitution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long officeId;
    private Long evaluatorId;
    private Long deputyId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private Long createdBy;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean activeOn(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }
}
