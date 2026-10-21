package vn.doit.hrm.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollImport;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;

@Repository
public interface PayrollImportRepository extends JpaRepository<PayrollImport, Long> {
    List<PayrollImport> findByStatusOrderByIdDesc(PayrollImportStatus status);

    List<PayrollImport> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PayrollImport i where i.id = :id")
    Optional<PayrollImport> findForUpdate(@Param("id") Long id);
}
