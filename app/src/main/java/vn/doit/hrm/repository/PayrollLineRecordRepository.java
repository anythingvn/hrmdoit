package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollLineRecord;

@Repository
public interface PayrollLineRecordRepository extends JpaRepository<PayrollLineRecord, Long> {
    List<PayrollLineRecord> findByVersionIdOrderByExcelRowAsc(Long versionId);

    List<PayrollLineRecord> findByEmployeeId(Long employeeId);
}
