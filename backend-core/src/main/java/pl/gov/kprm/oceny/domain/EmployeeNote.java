package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "employee_notes")
@Getter @Setter @NoArgsConstructor
public class EmployeeNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long cycleId;
    private Long authorId;
    private String content;
    private boolean isSharedAfterEval;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
