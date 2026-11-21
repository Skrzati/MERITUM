package pl.gov.kprm.oceny.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gov.kprm.oceny.domain.IprzItem;

import java.util.List;

public interface IprzItemRepository extends JpaRepository<IprzItem, Long> {
    List<IprzItem> findByCycleIdOrderByIdAsc(Long cycleId);
}
