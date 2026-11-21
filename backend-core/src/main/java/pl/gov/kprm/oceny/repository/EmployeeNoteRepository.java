package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.gov.kprm.oceny.domain.EmployeeNote;

import java.util.Collection;
import java.util.List;

public interface EmployeeNoteRepository extends JpaRepository<EmployeeNote, Long> {
    List<EmployeeNote> findByCycleIdOrderByCreatedAtAsc(Long cycleId);

    List<EmployeeNote> findByCycleIdInOrAuthorIdOrderByCreatedAtAsc(Collection<Long> cycleIds, Long authorId);

    long countByCycleIdIn(Collection<Long> cycleIds);

    boolean existsByAuthorId(Long authorId);

    List<EmployeeNote> findByCycleIdInOrderByCreatedAtAsc(Collection<Long> cycleIds);

    void deleteByCycleIdIn(Collection<Long> cycleIds);

    /** Data ostatniej notatki per cykl — jedno zapytanie dla przypomnień kwartalnych. */
    @Query("select n.cycleId, max(n.createdAt) from EmployeeNote n where n.cycleId in :cycleIds group by n.cycleId")
    List<Object[]> lastNoteAt(@Param("cycleIds") Collection<Long> cycleIds);
}
