package vn.doit.hrm.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.Department;

/**
 * Spring Data JPA repository for the Department entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    /** Người này đang là trưởng phòng của một phòng đang hoạt động. */
    boolean existsByHeadIdAndActiveTrue(Long headId);

    java.util.List<Department> findByHeadIdAndActiveTrueOrderByName(Long headId);

    /** Mọi phòng (kể cả ngừng hoạt động) người này là trưởng phòng: phạm vi xem bảng công. */
    java.util.List<Department> findByHeadId(Long headId);
}
