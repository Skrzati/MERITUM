package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.EmployeeSelfPrep;

public interface EmployeeSelfPrepRepository extends JpaRepository<EmployeeSelfPrep, Long> {
}
