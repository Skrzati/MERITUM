package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reschedule_requests")
@Getter @Setter @NoArgsConstructor
public class RescheduleRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long cycleId;
    @Enumerated(EnumType.STRING)
    private RescheduleMode mode;
    @Enumerated(EnumType.STRING)
    private RescheduleStatus status;
    private String reason;
    private LocalDate previousDueDate;
    private LocalDate proposedDueDate;
    private LocalDate newDueDate;
    private String noticeText;
    private Long requestedBy;
    private Long decidedBy;
    private String decisionComment;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    private OffsetDateTime decidedAt;
}
