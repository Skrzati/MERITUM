package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.RescheduleRequest;
import pl.gov.kprm.oceny.domain.RescheduleStatus;

import java.util.Collection;
import java.util.List;

public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, Long> {
    List<RescheduleRequest> findByCycleIdOrderByCreatedAtAsc(Long cycleId);

    List<RescheduleRequest> findByCycleIdInAndStatus(Collection<Long> cycleIds, RescheduleStatus status);

    List<RescheduleRequest> findByCycleIdAndStatus(Long cycleId, RescheduleStatus status);
}
