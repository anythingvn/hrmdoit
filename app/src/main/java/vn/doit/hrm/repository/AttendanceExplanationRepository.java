package vn.doit.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendanceExplanation;
import vn.doit.hrm.domain.enumeration.ExplanationStatus;

/** Giải trình chấm công. */
@Repository
public interface AttendanceExplanationRepository extends JpaRepository<AttendanceExplanation, Long> {
    boolean existsByEmployeeIdAndWorkDayAndStatus(Long employeeId, LocalDate workDay, ExplanationStatus status);

    List<AttendanceExplanation> findByEmployeeIdAndWorkDayBetweenOrderByWorkDayAscCreatedAtAsc(Long employeeId, LocalDate from, LocalDate to);

    /** Giải trình ở trạng thái {@code status} có ngày trong khoảng, kể cả hồ sơ đã lưu trữ. */
    List<AttendanceExplanation> findByStatusAndWorkDayBetween(ExplanationStatus status, LocalDate from, LocalDate to);

    /** Giải trình đang chờ của những người chưa lưu trữ, theo ngày. */
    @Query(
        "select x from AttendanceExplanation x where x.status = vn.doit.hrm.domain.enumeration.ExplanationStatus.DANG_CHO " +
        "and x.employeeId in (select e.id from Employee e where e.archivedAt is null) order by x.workDay, x.id"
    )
    List<AttendanceExplanation> findPendingOfActive();
}
