package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.OnsiteLead;

/** Trưởng nhóm onsite theo từng khách hàng. */
@Repository
public interface OnsiteLeadRepository extends JpaRepository<OnsiteLead, Long> {
    List<OnsiteLead> findByWorkSiteId(Long workSiteId);

    List<OnsiteLead> findByEmployeeId(Long employeeId);
}
