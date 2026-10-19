package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.SalaryChange;

/**
 * Spring Data JPA repository for the SalaryChange entity.
 */
@Repository
public interface SalaryChangeRepository extends JpaRepository<SalaryChange, Long> {
    default Optional<SalaryChange> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<SalaryChange> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<SalaryChange> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select salaryChange from SalaryChange salaryChange left join fetch salaryChange.employee",
        countQuery = "select count(salaryChange) from SalaryChange salaryChange"
    )
    Page<SalaryChange> findAllWithToOneRelationships(Pageable pageable);

    @Query("select salaryChange from SalaryChange salaryChange left join fetch salaryChange.employee")
    List<SalaryChange> findAllWithToOneRelationships();

    @Query("select salaryChange from SalaryChange salaryChange left join fetch salaryChange.employee where salaryChange.id =:id")
    Optional<SalaryChange> findOneWithToOneRelationships(@Param("id") Long id);

    List<SalaryChange> findByEmployeeId(Long employeeId);
}
