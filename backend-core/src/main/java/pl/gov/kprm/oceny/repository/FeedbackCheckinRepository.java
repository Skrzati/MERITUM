package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.gov.kprm.oceny.domain.FeedbackCheckin;

import java.util.Collection;
import java.util.List;

public interface FeedbackCheckinRepository extends JpaRepository<FeedbackCheckin, Long> {
    List<FeedbackCheckin> findByCycleIdOrderByConductedOnDescIdDesc(Long cycleId);

    /** Data ostatniego check-inu per cykl (reset licznika przypomnień kwartalnych). */
    @Query("select c.cycleId, max(c.conductedOn) from FeedbackCheckin c where c.cycleId in :cycleIds group by c.cycleId")
    List<Object[]> lastCheckinOn(@Param("cycleIds") Collection<Long> cycleIds);
}
