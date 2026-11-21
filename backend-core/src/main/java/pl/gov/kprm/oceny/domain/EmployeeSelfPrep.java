package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Przygotowanie ocenianego do rozmowy — prywatne, widoczne dla przełożonego wyłącznie po udostępnieniu. */
@Entity
@Table(name = "employee_self_prep")
@Getter @Setter @NoArgsConstructor
public class EmployeeSelfPrep {
    @Id
    private Long cycleId;
    private String achievements;
    private String challenges;
    private String developmentNeeds;
    private boolean sharedWithEvaluator;
    private OffsetDateTime updatedAt;
}
