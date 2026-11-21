package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "scope_changes")
@Getter @Setter @NoArgsConstructor
public class ScopeChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long cycleId;
    private Long userId;
    private LocalDate changeDate;
    private String newPositionTitle;
    private String description;
    private boolean significant;
    @Enumerated(EnumType.STRING)
    private ScopeChangeOutcome outcome;
    private LocalDate actionDeadline;
    private Long recordedBy;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
