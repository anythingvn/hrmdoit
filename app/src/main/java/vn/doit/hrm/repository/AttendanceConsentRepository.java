package vn.doit.hrm.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendanceConsent;

/** Đồng ý thông báo thu thập vị trí khi tự chấm công. */
@Repository
public interface AttendanceConsentRepository extends JpaRepository<AttendanceConsent, Long> {
    Optional<AttendanceConsent> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);
}
