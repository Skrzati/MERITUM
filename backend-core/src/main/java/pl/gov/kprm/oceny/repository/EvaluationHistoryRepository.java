package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.EvaluationHistory;

import java.util.List;
import java.util.Optional;

public interface EvaluationHistoryRepository extends JpaRepository<EvaluationHistory, Long> {
    List<EvaluationHistory> findByUserIdOrderByEvaluatedOnAsc(Long userId);

    Optional<EvaluationHistory> findByCycleId(Long cycleId);
}
