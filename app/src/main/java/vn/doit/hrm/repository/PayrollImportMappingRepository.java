package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollImportMapping;

@Repository
public interface PayrollImportMappingRepository extends JpaRepository<PayrollImportMapping, Long> {
    List<PayrollImportMapping> findByPayrollImportId(Long importId);

    Optional<PayrollImportMapping> findByPayrollImportIdAndExcelRow(Long importId, int excelRow);
}
