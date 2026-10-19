package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.EmployeeDocument;

/**
 * Spring Data JPA repository for the EmployeeDocument entity.
 */
@Repository
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    default Optional<EmployeeDocument> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<EmployeeDocument> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<EmployeeDocument> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select employeeDocument from EmployeeDocument employeeDocument left join fetch employeeDocument.employee",
        countQuery = "select count(employeeDocument) from EmployeeDocument employeeDocument"
    )
    Page<EmployeeDocument> findAllWithToOneRelationships(Pageable pageable);

    @Query("select employeeDocument from EmployeeDocument employeeDocument left join fetch employeeDocument.employee")
    List<EmployeeDocument> findAllWithToOneRelationships();

    @Query(
        "select employeeDocument from EmployeeDocument employeeDocument left join fetch employeeDocument.employee where employeeDocument.id =:id"
    )
    Optional<EmployeeDocument> findOneWithToOneRelationships(@Param("id") Long id);
}
