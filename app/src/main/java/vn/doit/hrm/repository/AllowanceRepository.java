package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.Allowance;

/**
 * Spring Data JPA repository for the Allowance entity.
 */
@Repository
public interface AllowanceRepository extends JpaRepository<Allowance, Long> {
    default Optional<Allowance> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<Allowance> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<Allowance> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select allowance from Allowance allowance left join fetch allowance.employee",
        countQuery = "select count(allowance) from Allowance allowance"
    )
    Page<Allowance> findAllWithToOneRelationships(Pageable pageable);

    @Query("select allowance from Allowance allowance left join fetch allowance.employee")
    List<Allowance> findAllWithToOneRelationships();

    @Query("select allowance from Allowance allowance left join fetch allowance.employee where allowance.id =:id")
    Optional<Allowance> findOneWithToOneRelationships(@Param("id") Long id);

    List<Allowance> findByEmployeeId(Long employeeId);
}
