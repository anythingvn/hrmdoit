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

    /** Khóa dòng tháng đến hết giao dịch: chốt và mở lại cùng tháng chạy lần lượt. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from AttendanceMonth m where m.month = :month")
    Optional<AttendanceMonth> findForUpdate(@Param("month") String month);

    /**
     * Khóa dòng tháng, chưa có thì tạo (đang mở) rồi giữ nó tới hết giao dịch: gõ giờ, chốt và mở lại cùng tháng chạy lần lượt (R23).
     * Hai giao dịch cùng tạo dòng lần đầu thì một bên lỗi ràng buộc duy nhất (đã chấp nhận).
     */
    default AttendanceMonth lockOrCreate(String month) {
        return findForUpdate(month).orElseGet(() -> {
            AttendanceMonth row = new AttendanceMonth();
            row.setMonth(month);
            row.setStatus(AttendanceMonthStatus.DANG_MO);
            return saveAndFlush(row);
        });
    }

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
