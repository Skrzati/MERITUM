package pl.gov.kprm.oceny.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long officeId;
    private Long actorId;
    private String action;
    private String entityType;
    private Long entityId;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payloadJson;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
