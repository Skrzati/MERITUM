package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.SchemaVersion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersion, Long> {
    boolean existsByVersionCode(String versionCode);

    List<SchemaVersion> findAllByOrderByEffectiveFromDesc();

    /** Najnowsza obowiązująca wersja schematu (data wejścia w życie <= dziś). */
    Optional<SchemaVersion> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(LocalDate date);
}
