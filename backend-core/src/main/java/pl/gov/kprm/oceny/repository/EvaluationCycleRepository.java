package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.EvaluationCycle;

import java.util.List;

public interface EvaluationCycleRepository extends JpaRepository<EvaluationCycle, Long> {
    List<EvaluationCycle> findByEmployeeIdOrEvaluatorIdOrderByIdAsc(Long employeeId, Long evaluatorId);

    List<EvaluationCycle> findByEmployeeIdOrderByIdAsc(Long employeeId);

    List<EvaluationCycle> findByOfficeIdOrderByIdAsc(Long officeId);

    List<EvaluationCycle> findAllByOrderByIdAsc();

    List<EvaluationCycle> findByEmployeeIdOrderByStartDateDesc(Long employeeId);
}
