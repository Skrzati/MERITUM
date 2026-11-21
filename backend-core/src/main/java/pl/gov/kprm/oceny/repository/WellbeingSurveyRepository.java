package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.WellbeingSurvey;

import java.util.List;

public interface WellbeingSurveyRepository extends JpaRepository<WellbeingSurvey, Long> {
    List<WellbeingSurvey> findByOfficeId(Long officeId);

    List<WellbeingSurvey> findByUserIdOrderBySubmittedAtAsc(Long userId);
}
