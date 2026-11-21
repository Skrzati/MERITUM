package pl.gov.kprm.oceny.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Ankieta przygotowawcza przed rozmową (nie mylić z anonimową ankietą dobrostanu): pytania ze schematu i własne pytania
 * kadr / przełożonego, odpowiedzi ocenianego widoczne dla przełożonego po wysłaniu. Dane pomocnicze — poza aktami.
 */
@Entity
@Table(name = "pre_interview_surveys")
@Getter @Setter @NoArgsConstructor
public class PreInterviewSurvey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long cycleId;
    /** [{id, text, custom, addedBy, answer}] */
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode questionsJson;
    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;
    private OffsetDateTime sentAt;
    private OffsetDateTime updatedAt;

    public enum Status { DRAFT, SENT }
}
