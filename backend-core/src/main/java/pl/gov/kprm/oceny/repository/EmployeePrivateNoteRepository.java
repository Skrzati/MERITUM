package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.EmployeePrivateNote;

import java.util.List;

public interface EmployeePrivateNoteRepository extends JpaRepository<EmployeePrivateNote, Long> {
    List<EmployeePrivateNote> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    List<EmployeePrivateNote> findByOwnerIdAndCycleIdOrderByCreatedAtDescIdDesc(Long ownerId, Long cycleId);

    void deleteByOwnerId(Long ownerId);
}
