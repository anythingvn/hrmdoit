package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollVersion;

@Repository
public interface PayrollVersionRepository extends JpaRepository<PayrollVersion, Long> {
    @Query("select max(v.version) from PayrollVersion v where v.period.id = :periodId")
    Integer maxVersion(@Param("periodId") Long periodId);

    /** Tháng hiệu lực (yyyy-MM) của các mốc luật mà ít nhất một phiên bản bảng lương (kỳ nào, trạng thái nào) đã dùng. */
    @Query(
        value = "select distinct cast(rules_snapshot as jsonb) ->> 'effectiveFrom' from payroll_version where rules_snapshot is not null",
        nativeQuery = true
    )
    List<String> usedMilestoneMonths();

    List<PayrollVersion> findByPeriodIdOrderByVersionDesc(Long periodId);

    Optional<PayrollVersion> findByPeriodIdAndVersion(Long periodId, int version);
}
