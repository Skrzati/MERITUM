package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.RescheduleRequest;

import java.util.List;

public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, Long> {
    List<RescheduleRequest> findByCycleIdOrderByCreatedAtAsc(Long cycleId);
}
