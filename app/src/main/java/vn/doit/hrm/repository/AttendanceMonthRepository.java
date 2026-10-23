package vn.doit.hrm.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from AttendanceMonth m where m.month = :month")
    Optional<AttendanceMonth> findForUpdate(@Param("month") String month);

    /**
     * Tạo dòng tháng (đang mở) nếu chưa có; giao dịch khác đang tạo cùng tháng thì chờ nó rồi không làm gì (không lỗi khóa duy nhất).
     * Id lấy bằng {@code nextval('sequence_generator')}, cùng sequence với {@code @SequenceGenerator(name = "sequenceGenerator")} của
     * entity (tên vật lý theo CamelCaseToUnderscores, tăng 50). Hibernate cấp id theo khối của từng giá trị nó tự lấy từ sequence, nên giá
     * trị lấy ở đây không rơi vào khối nào của Hibernate.
     */
    @Modifying
    @Query(
        value = "insert into attendance_month (id, month, status) values (nextval('sequence_generator'), :month, 'DANG_MO') " +
        "on conflict (month) do nothing",
        nativeQuery = true
    )
    void insertIfMissing(@Param("month") String month);

    /**
     * Khóa dòng tháng, chưa có thì tạo (đang mở) rồi giữ nó tới hết giao dịch: gõ giờ, chốt và mở lại cùng tháng chạy lần lượt (R23).
     * Hai giao dịch cùng tạo dòng lần đầu không lỗi: một bên tạo, bên kia chờ rồi khóa đúng dòng đó (R28).
     */
    default AttendanceMonth lockOrCreate(String month) {
        insertIfMissing(month);
        return findForUpdate(month).orElseThrow();
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
