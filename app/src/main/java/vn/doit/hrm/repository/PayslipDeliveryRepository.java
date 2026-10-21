package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayslipDelivery;
import vn.doit.hrm.domain.enumeration.PayslipDeliveryStatus;

@Repository
public interface PayslipDeliveryRepository extends JpaRepository<PayslipDelivery, Long> {
    List<PayslipDelivery> findByPeriodIdOrderByIdAsc(Long periodId);

    List<PayslipDelivery> findByPeriodIdAndStatusOrderByIdAsc(Long periodId, PayslipDeliveryStatus status);

    List<PayslipDelivery> findByEmployeeIdAndStatusOrderByIdDesc(Long employeeId, PayslipDeliveryStatus status);

    /** Chuyển mọi lượt còn chờ của kỳ thành lỗi (vd kỳ bị mở lại); {@code periodId == null} = mọi kỳ. */
    @Modifying
    @Query(
        "update PayslipDelivery d set d.status = vn.doit.hrm.domain.enumeration.PayslipDeliveryStatus.FAILED, d.error = :error " +
        "where d.status = vn.doit.hrm.domain.enumeration.PayslipDeliveryStatus.PENDING and (:periodId is null or d.period.id = :periodId)"
    )
    int failPending(@Param("periodId") Long periodId, @Param("error") String error);
}
