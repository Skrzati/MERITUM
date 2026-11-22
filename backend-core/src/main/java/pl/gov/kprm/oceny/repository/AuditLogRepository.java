package pl.gov.kprm.oceny.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.AuditLog;

import java.util.Collection;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByActorIdOrEntityTypeAndEntityIdInOrderByCreatedAtAsc(Long actorId, String entityType,
                                                                             Collection<Long> entityIds);

    List<AuditLog> findByOfficeIdOrderByCreatedAtDescIdDesc(Long officeId, Pageable page);

    List<AuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable page);

    long countByOfficeIdAndActionAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long officeId, String action,
                                                                                java.time.OffsetDateTime from,
                                                                                java.time.OffsetDateTime to);
}
