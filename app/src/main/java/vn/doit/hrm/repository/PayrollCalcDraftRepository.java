package vn.doit.hrm.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollCalcDraft;
import vn.doit.hrm.domain.enumeration.PayrollCalcStatus;

@Repository
public interface PayrollCalcDraftRepository extends JpaRepository<PayrollCalcDraft, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PayrollCalcDraft d where d.id = :id")
    Optional<PayrollCalcDraft> findForUpdate(@Param("id") Long id);

    @Query("select d from PayrollCalcDraft d where d.period.id = :periodId and d.status = :status")
    Optional<PayrollCalcDraft> findByPeriodAndStatus(@Param("periodId") Long periodId, @Param("status") PayrollCalcStatus status);
}
