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

    List<User> findAllByActiveTrueOrderByIdAsc();

    List<User> findByOfficeIdAndDepartmentIgnoreCaseAndActiveTrue(Long officeId, String department);

    boolean existsBySupervisorId(Long supervisorId);

    List<User> findBySupervisorIdAndActiveTrueOrderByFullNameAsc(Long supervisorId);

    /** Czy rekord jest wskazywany przez dane procesu (wtedy anonimizacja zamiast usunięcia). */
    @Query(nativeQuery = true, value = """
            select exists(select 1 from evaluation_cycles where employee_id = :id or evaluator_id = :id)
                or exists(select 1 from evaluation_history where user_id = :id or entered_by = :id)
                or exists(select 1 from employee_notes where author_id = :id)
                or exists(select 1 from audit_logs where actor_id = :id)
                or exists(select 1 from evaluator_substitutions where evaluator_id = :id or deputy_id = :id or created_by = :id)
                or exists(select 1 from absences where created_by = :id)
                or exists(select 1 from scope_changes where user_id = :id or recorded_by = :id)
                or exists(select 1 from reschedule_requests where requested_by = :id or decided_by = :id)
                or exists(select 1 from schema_versions where created_by = :id)
            """)
    boolean isReferenced(@Param("id") Long id);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from User u
            where u.officeId = :officeId and u.active = true
              and (lower(u.fullName) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%')))
            """)
    List<User> search(@Param("officeId") Long officeId, @Param("q") String query, Sort sort);

    @Query("""
            select u from User u
            where u.officeId = :officeId and u.active = true
              and (lower(u.fullName) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%')))
            """)
    List<User> search(@Param("officeId") Long officeId, @Param("q") String query, Pageable page);
}
