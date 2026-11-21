package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Bieżąca rozmowa informacyjna (check-in) w trakcie okresu oceny — Standardy ZZL w służbie cywilnej. */
@Entity
@Table(name = "feedback_checkins")
@Getter @Setter @NoArgsConstructor
public class FeedbackCheckin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long cycleId;
    private Long authorId;
    private LocalDate conductedOn;
    private String topic;
    private String summary;
    private String nextSteps;
    private boolean sharedWithEmployee;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
