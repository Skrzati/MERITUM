package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.EmployeeNote;

import java.util.List;

public interface EmployeeNoteRepository extends JpaRepository<EmployeeNote, Long> {
    List<EmployeeNote> findByCycleIdOrderByCreatedAtAsc(Long cycleId);
}
