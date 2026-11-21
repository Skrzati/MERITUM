package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import pl.gov.kprm.oceny.domain.EvaluationCycle;

import java.util.Collection;
import java.util.List;

public interface EvaluationCycleRepository extends JpaRepository<EvaluationCycle, Long> {
    List<EvaluationCycle> findByEmployeeIdOrEvaluatorIdOrderByIdAsc(Long employeeId, Long evaluatorId);

    List<EvaluationCycle> findByEmployeeIdOrEvaluatorIdOrderByIdAsc(Long employeeId, Long evaluatorId, Pageable page);

    List<EvaluationCycle> findByEmployeeIdOrderByIdAsc(Long employeeId);

    List<EvaluationCycle> findByEvaluatorIdInOrderByIdAsc(Collection<Long> evaluatorIds);

    List<EvaluationCycle> findByEmployeeIdInOrderByIdAsc(Collection<Long> employeeIds);

    boolean existsByEmployeeIdOrEvaluatorId(Long employeeId, Long evaluatorId);

    List<EvaluationCycle> findByOfficeIdOrderByIdAsc(Long officeId);

    List<EvaluationCycle> findByOfficeIdOrderByIdAsc(Long officeId, Pageable page);

    List<EvaluationCycle> findAllByOrderByIdAsc();

    List<EvaluationCycle> findAllByOrderByIdAsc(Pageable page);

    List<EvaluationCycle> findByEmployeeIdOrderByStartDateDesc(Long employeeId);
}
