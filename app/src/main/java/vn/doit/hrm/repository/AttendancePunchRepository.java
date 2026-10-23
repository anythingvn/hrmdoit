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

    /** Các lần chấm cùng nguồn, cùng loại của một người trong khoảng {@code (after, upTo]}, mới nhất trước (chặn bấm trùng trong 1 phút). */
    @org.springframework.data.jpa.repository.Query(
        "select p from AttendancePunch p where p.employeeId = :employeeId and p.kind = :kind and p.source = :source " +
            "and p.enteredAt > :after and p.enteredAt <= :upTo order by p.enteredAt desc, p.id desc"
    )
    List<AttendancePunch> findRecent(
        @org.springframework.data.repository.query.Param("employeeId") Long employeeId,
        @org.springframework.data.repository.query.Param("kind") vn.doit.hrm.domain.enumeration.AttendancePunchKind kind,
        @org.springframework.data.repository.query.Param("source") vn.doit.hrm.domain.enumeration.AttendancePunchSource source,
        @org.springframework.data.repository.query.Param("after") java.time.Instant after,
        @org.springframework.data.repository.query.Param("upTo") java.time.Instant upTo
    );

    /** Lần tự chấm đang chờ duyệt của những người chưa lưu trữ, theo ngày và giờ. */
    @org.springframework.data.jpa.repository.Query(
        "select p from AttendancePunch p where p.source = vn.doit.hrm.domain.enumeration.AttendancePunchSource.APP " +
            "and p.status = vn.doit.hrm.domain.enumeration.AttendancePunchStatus.CHO_DUYET " +
            "and p.employeeId in (select e.id from Employee e where e.archivedAt is null) order by p.workDay, p.punchTime, p.id"
    )
    List<AttendancePunch> findPendingOfActive();
}
