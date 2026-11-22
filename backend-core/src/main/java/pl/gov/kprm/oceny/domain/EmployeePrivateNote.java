package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Prywatny dziennik pracownika — widoczny wyłącznie dla właściciela; poza aktami, eksportem EZD i audytem treści. */
@Entity
@Table(name = "employee_private_notes")
@Getter @Setter @NoArgsConstructor
public class EmployeePrivateNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    private Long cycleId;
    private String content;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
