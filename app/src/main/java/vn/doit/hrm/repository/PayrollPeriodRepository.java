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
import vn.doit.hrm.domain.PayrollPeriod;

@Repository
public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {
    /** Tạo kỳ (Nháp) nếu chưa có; nhiều giao dịch gọi cùng lúc thì chỉ một dòng được tạo. */
    @Modifying
    @Query(value = "insert into payroll_period (period, status) values (:period, 'NHAP') on conflict (period) do nothing", nativeQuery = true)
    void insertIfMissing(@Param("period") String period);

    /** Khóa dòng kỳ đến hết giao dịch: xác nhận, duyệt, mở lại cùng kỳ chạy lần lượt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayrollPeriod p where p.period = :period")
    Optional<PayrollPeriod> findForUpdate(@Param("period") String period);

    Optional<PayrollPeriod> findByPeriod(String period);

    /** Các kỳ (yyyy-MM) đang ở trạng thái {@code status}. */
    @Query("select p.period from PayrollPeriod p where p.status = :status")
    List<String> findPeriodsByStatus(@Param("status") vn.doit.hrm.domain.enumeration.PayrollPeriodStatus status);

    List<PayrollPeriod> findAllByOrderByPeriodDesc();

    /** Kỳ đã duyệt lớn nhất (yyyy-MM), null nếu chưa có. */
    @Query("select max(p.period) from PayrollPeriod p where p.status = vn.doit.hrm.domain.enumeration.PayrollPeriodStatus.DA_DUYET")
    String latestApproved();
}
