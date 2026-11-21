package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.gov.kprm.oceny.domain.EvaluatorSubstitution;

import java.time.LocalDate;
import java.util.List;

public interface EvaluatorSubstitutionRepository extends JpaRepository<EvaluatorSubstitution, Long> {
    @Query("select s from EvaluatorSubstitution s where s.deputyId = :deputyId and :day between s.startDate and s.endDate")
    List<EvaluatorSubstitution> activeForDeputy(@Param("deputyId") Long deputyId, @Param("day") LocalDate day);

    List<EvaluatorSubstitution> findByOfficeIdOrderByStartDateDesc(Long officeId);

    List<EvaluatorSubstitution> findByEvaluatorIdOrDeputyIdOrderByStartDateDesc(Long evaluatorId, Long deputyId);

    boolean existsByEvaluatorIdOrDeputyId(Long evaluatorId, Long deputyId);
}
