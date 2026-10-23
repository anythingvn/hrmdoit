package vn.doit.hrm.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;

/** Bảng công theo tháng (yyyy-MM). */
@Repository
public interface AttendanceMonthRepository extends JpaRepository<AttendanceMonth, Long> {
    Optional<AttendanceMonth> findByMonth(String month);

    boolean existsByMonthAndStatus(String month, AttendanceMonthStatus status);
}
