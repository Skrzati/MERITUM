package pl.gov.kprm.oceny.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.gov.kprm.oceny.domain.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findAllByOrderByIdAsc();

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from User u
            where u.officeId = :officeId
              and (lower(u.fullName) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%')))
            """)
    List<User> search(@Param("officeId") Long officeId, @Param("q") String query, Sort sort);

    @Query("""
            select u from User u
            where u.officeId = :officeId
              and (lower(u.fullName) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%')))
            """)
    List<User> search(@Param("officeId") Long officeId, @Param("q") String query, Pageable page);
}
