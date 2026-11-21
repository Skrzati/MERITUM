package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.Absence;

import java.util.List;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {
    List<Absence> findByUserIdOrderByStartDateAsc(Long userId);

    List<Absence> findByUserIdIn(java.util.Collection<Long> userIds);
}
