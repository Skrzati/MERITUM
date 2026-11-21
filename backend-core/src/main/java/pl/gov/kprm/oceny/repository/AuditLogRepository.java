package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.AuditLog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByActorIdOrEntityTypeAndEntityIdInOrderByCreatedAtAsc(Long actorId, String entityType,
                                                                             Collection<Long> entityIds);

    Optional<AuditLog> findFirstByEntityTypeAndEntityIdAndActionOrderByCreatedAtDesc(String entityType, Long entityId,
                                                                                    String action);
}
