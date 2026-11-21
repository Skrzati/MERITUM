package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.ScopeChange;

import java.util.List;

public interface ScopeChangeRepository extends JpaRepository<ScopeChange, Long> {
    List<ScopeChange> findByCycleIdOrderByChangeDateAsc(Long cycleId);
}
