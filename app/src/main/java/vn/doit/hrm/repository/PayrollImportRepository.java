package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollImport;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;

@Repository
public interface PayrollImportRepository extends JpaRepository<PayrollImport, Long> {
    List<PayrollImport> findByStatusOrderByIdDesc(PayrollImportStatus status);

    List<PayrollImport> findAllByOrderByIdDesc();
}
