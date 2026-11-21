package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.SchemaVersion;

import java.util.List;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersion, Long> {
    boolean existsByVersionCode(String versionCode);

    List<SchemaVersion> findAllByOrderByEffectiveFromDesc();
}
