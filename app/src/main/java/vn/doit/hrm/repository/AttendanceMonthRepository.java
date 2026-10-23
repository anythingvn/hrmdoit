package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;

/** Bảng công theo tháng (yyyy-MM). */
@Repository
public interface AttendanceMonthRepository extends JpaRepository<AttendanceMonth, Long> {
    Optional<AttendanceMonth> findByMonth(String month);

    boolean existsByMonthAndStatus(String month, AttendanceMonthStatus status);

    /** Tháng có trạng thái {@code status} đầu tiên trong [{@code from}, {@code to}) (yyyy-MM; {@code to} null là không giới hạn). */
    @Query(
        "select m.month from AttendanceMonth m where m.status = :status and m.month >= :from " +
        "and (:to is null or m.month < :to) order by m.month"
    )
    List<String> findMonthsInRange(
        @Param("status") AttendanceMonthStatus status,
        @Param("from") String from,
        @Param("to") String to
    );
}
