package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Pozycja indywidualnego programu rozwoju zawodowego (art. 108 ustawy o SC) — działanie z formą i terminem. */
@Entity
@Table(name = "iprz_items")
@Getter @Setter @NoArgsConstructor
public class IprzItem {
    public enum FormType { SZKOLENIE, MENTORING, SAMOKSZTALCENIE, PROJEKT, INNE }

    public enum Status { PLANNED, IN_PROGRESS, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long cycleId;
    private String criterionCode;
    private String actionTitle;
    @Enumerated(EnumType.STRING)
    private FormType formType;
    private LocalDate targetDate;
    @Enumerated(EnumType.STRING)
    private Status status = Status.PLANNED;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
