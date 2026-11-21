package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.PreInterviewSurvey;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PreInterviewSurveyRepository extends JpaRepository<PreInterviewSurvey, Long> {
    Optional<PreInterviewSurvey> findByCycleId(Long cycleId);

    List<PreInterviewSurvey> findByCycleIdInAndStatus(Collection<Long> cycleIds, PreInterviewSurvey.Status status);

    long deleteByCycleIdIn(Collection<Long> cycleIds);
}
