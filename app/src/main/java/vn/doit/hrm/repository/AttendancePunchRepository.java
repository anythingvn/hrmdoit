package vn.doit.hrm.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;

/** Các lần chấm vào/ra. */
@Repository
public interface AttendancePunchRepository extends JpaRepository<AttendancePunch, Long> {
    List<AttendancePunch> findByEmployeeIdAndWorkDayOrderByPunchTimeAsc(Long employeeId, LocalDate workDay);

    /** Có lần chấm nào đã đối chiếu với địa điểm {@code siteId} không (không cho xóa địa điểm đó). */
    boolean existsBySiteId(Long siteId);

    List<AttendancePunch> findByWorkDayBetween(LocalDate from, LocalDate to);

    List<AttendancePunch> findByEmployeeIdInAndWorkDayBetween(Collection<Long> employeeIds, LocalDate from, LocalDate to);

    List<AttendancePunch> findByEmployeeIdAndWorkDayAndSource(Long employeeId, LocalDate workDay, AttendancePunchSource source);

    /** Các lần chấm cùng nguồn, cùng loại của một người trong khoảng {@code (after, upTo]}, mới nhất trước (chặn bấm trùng trong 1 phút). */
    @Query(
        "select p from AttendancePunch p where p.employeeId = :employeeId and p.kind = :kind and p.source = :source " +
            "and p.enteredAt > :after and p.enteredAt <= :upTo order by p.enteredAt desc, p.id desc"
    )
    List<AttendancePunch> findRecent(
        @Param("employeeId") Long employeeId,
        @Param("kind") AttendancePunchKind kind,
        @Param("source") AttendancePunchSource source,
        @Param("after") Instant after,
        @Param("upTo") Instant upTo
    );

    /** Lần tự chấm đang chờ duyệt của những người chưa lưu trữ, theo ngày và giờ. */
    @Query(
        "select p from AttendancePunch p where p.source = vn.doit.hrm.domain.enumeration.AttendancePunchSource.APP " +
            "and p.status = vn.doit.hrm.domain.enumeration.AttendancePunchStatus.CHO_DUYET " +
            "and p.employeeId in (select e.id from Employee e where e.archivedAt is null) order by p.workDay, p.punchTime, p.id"
    )
    List<AttendancePunch> findPendingOfActive();
}
