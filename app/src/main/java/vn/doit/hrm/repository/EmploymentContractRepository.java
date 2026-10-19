package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.EmploymentContract;

/**
 * Spring Data JPA repository for the EmploymentContract entity.
 */
@Repository
public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {
    default Optional<EmploymentContract> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<EmploymentContract> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<EmploymentContract> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select employmentContract from EmploymentContract employmentContract left join fetch employmentContract.employee left join fetch employmentContract.department",
        countQuery = "select count(employmentContract) from EmploymentContract employmentContract"
    )
    Page<EmploymentContract> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select employmentContract from EmploymentContract employmentContract left join fetch employmentContract.employee left join fetch employmentContract.department"
    )
    List<EmploymentContract> findAllWithToOneRelationships();

    @Query(
        "select employmentContract from EmploymentContract employmentContract left join fetch employmentContract.employee left join fetch employmentContract.department where employmentContract.id =:id"
    )
    Optional<EmploymentContract> findOneWithToOneRelationships(@Param("id") Long id);
}
