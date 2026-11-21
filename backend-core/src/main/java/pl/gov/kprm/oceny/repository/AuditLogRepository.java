package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
