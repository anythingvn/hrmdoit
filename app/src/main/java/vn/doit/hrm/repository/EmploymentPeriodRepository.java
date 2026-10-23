package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.EmploymentPeriod;

/** Các lần làm việc trước của hồ sơ (ghi khi nhận làm lại). */
@Repository
public interface EmploymentPeriodRepository extends JpaRepository<EmploymentPeriod, Long> {
    List<EmploymentPeriod> findByEmployeeIdOrderByJoinDateAscIdAsc(Long employeeId);

    /** Lần làm việc trước gần nhất (ghi ở lần nhận làm lại gần nhất). */
    Optional<EmploymentPeriod> findFirstByEmployeeIdOrderByIdDesc(Long employeeId);

    boolean existsByTerminationRecordId(Long terminationRecordId);

    /** Các lần làm việc trước của nhiều hồ sơ (chấm công: khoảng làm việc). */
    List<EmploymentPeriod> findByEmployeeIdIn(java.util.Collection<Long> employeeIds);
}
