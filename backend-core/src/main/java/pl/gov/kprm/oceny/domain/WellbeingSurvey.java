package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Dobrowolna ankieta dobrostanu — nie jest powiązana z cyklem oceny. */
@Entity
@Table(name = "wellbeing_surveys")
@Getter @Setter @NoArgsConstructor
public class WellbeingSurvey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long officeId;
    private int workload;
    private int energy;
    private int support;
    private boolean wantsConversation;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime submittedAt;
}
