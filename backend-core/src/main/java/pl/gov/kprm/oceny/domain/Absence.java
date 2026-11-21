package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "absences")
@Getter @Setter @NoArgsConstructor
public class Absence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private Long createdBy;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
