package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.EmploymentPeriod;

/** Các lần làm việc trước của hồ sơ (ghi khi nhận làm lại). */
@Repository
public interface EmploymentPeriodRepository extends JpaRepository<EmploymentPeriod, Long> {
    List<EmploymentPeriod> findByEmployeeIdOrderByJoinDateAscIdAsc(Long employeeId);

    boolean existsByTerminationRecordId(Long terminationRecordId);
}
