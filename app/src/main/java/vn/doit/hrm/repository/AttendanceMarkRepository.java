package vn.doit.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendanceMark;

/** Đánh dấu ngày (vắng). */
@Repository
public interface AttendanceMarkRepository extends JpaRepository<AttendanceMark, Long> {
    Optional<AttendanceMark> findByEmployeeIdAndWorkDay(Long employeeId, LocalDate workDay);

    List<AttendanceMark> findByWorkDayBetween(LocalDate from, LocalDate to);

    List<AttendanceMark> findByEmployeeIdInAndWorkDayBetween(java.util.Collection<Long> employeeIds, LocalDate from, LocalDate to);
}
