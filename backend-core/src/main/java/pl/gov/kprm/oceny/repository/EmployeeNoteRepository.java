package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.EmployeeNote;

import java.util.Collection;
import java.util.List;

public interface EmployeeNoteRepository extends JpaRepository<EmployeeNote, Long> {
    List<EmployeeNote> findByCycleIdOrderByCreatedAtAsc(Long cycleId);

    List<EmployeeNote> findByCycleIdInOrAuthorIdOrderByCreatedAtAsc(Collection<Long> cycleIds, Long authorId);

    long countByCycleIdIn(Collection<Long> cycleIds);
}
