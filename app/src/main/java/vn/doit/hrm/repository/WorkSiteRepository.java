package vn.doit.hrm.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.WorkSite;

/**
 * Spring Data JPA repository for the WorkSite entity.
 */
@SuppressWarnings("unused")
@Repository
public interface WorkSiteRepository extends JpaRepository<WorkSite, Long> {
    /** Nơi làm việc trùng tên, không phân biệt hoa thường (tên là duy nhất trong danh mục, không theo loại). */
    java.util.List<WorkSite> findByNameIgnoreCaseOrderByIdAsc(String name);
}
