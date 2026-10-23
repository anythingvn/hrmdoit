package vn.doit.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendancePunch;

/** Các lần chấm vào/ra. */
@Repository
public interface AttendancePunchRepository extends JpaRepository<AttendancePunch, Long> {
    List<AttendancePunch> findByEmployeeIdAndWorkDayOrderByPunchTimeAsc(Long employeeId, LocalDate workDay);

    List<AttendancePunch> findByWorkDayBetween(LocalDate from, LocalDate to);

    List<AttendancePunch> findByEmployeeIdInAndWorkDayBetween(java.util.Collection<Long> employeeIds, LocalDate from, LocalDate to);

    List<AttendancePunch> findByEmployeeIdAndWorkDayAndSource(
        Long employeeId,
        LocalDate workDay,
        vn.doit.hrm.domain.enumeration.AttendancePunchSource source
    );
}
