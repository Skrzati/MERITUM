package pl.gov.kprm.oceny.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(name = "schema_versions")
@Getter @Setter @NoArgsConstructor
public class SchemaVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String versionCode;
    private LocalDate effectiveFrom;
    private int periodMonths;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode configJson;
    private Long createdBy;
}
