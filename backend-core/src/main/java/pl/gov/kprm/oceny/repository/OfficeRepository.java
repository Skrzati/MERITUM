package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.Office;

public interface OfficeRepository extends JpaRepository<Office, Long> {
}
