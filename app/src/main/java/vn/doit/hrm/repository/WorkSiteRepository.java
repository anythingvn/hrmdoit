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

    /** Nơi làm việc đang dùng của một loại (vd. các văn phòng công ty), theo id. */
    java.util.List<WorkSite> findByTypeAndActiveTrueOrderByIdAsc(vn.doit.hrm.domain.enumeration.WorkLocationType type);
}
