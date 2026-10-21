package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollLineRecord;

@Repository
public interface PayrollLineRecordRepository extends JpaRepository<PayrollLineRecord, Long> {
    List<PayrollLineRecord> findByVersionIdOrderByExcelRowAsc(Long versionId);

    List<PayrollLineRecord> findByEmployeeId(Long employeeId);

    /** Số dòng, tổng thực nhận, tổng chi phí của một phiên bản. */
    record Totals(long headcount, java.math.BigDecimal totalNet, java.math.BigDecimal totalCost) {}

    @org.springframework.data.jpa.repository.Query(
        "select new vn.doit.hrm.repository.PayrollLineRecordRepository$Totals(count(l), coalesce(sum(l.thucNhan), 0), coalesce(sum(l.tongChiPhi), 0)) " +
        "from PayrollLineRecord l where l.version.id = :versionId"
    )
    Totals totals(@org.springframework.data.repository.query.Param("versionId") Long versionId);
}
