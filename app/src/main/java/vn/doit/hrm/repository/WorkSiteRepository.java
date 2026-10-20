package vn.doit.hrm.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.WorkSite;

/**
 * Spring Data JPA repository for the WorkSite entity.
 */
@SuppressWarnings("unused")
@Repository
public interface WorkSiteRepository extends JpaRepository<WorkSite, Long> {}
