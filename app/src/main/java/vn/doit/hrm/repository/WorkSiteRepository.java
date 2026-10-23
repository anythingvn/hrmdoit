package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/**
 * Spring Data JPA repository for the WorkSite entity.
 */
@SuppressWarnings("unused")
@Repository
public interface WorkSiteRepository extends JpaRepository<WorkSite, Long> {
    /** Nơi làm việc trùng tên, không phân biệt hoa thường (tên là duy nhất trong danh mục, không theo loại). */
    List<WorkSite> findByNameIgnoreCaseOrderByIdAsc(String name);

    /** Nơi làm việc đang dùng của một loại (vd. các văn phòng công ty), theo id. */
    List<WorkSite> findByTypeAndActiveTrueOrderByIdAsc(WorkLocationType type);
}
