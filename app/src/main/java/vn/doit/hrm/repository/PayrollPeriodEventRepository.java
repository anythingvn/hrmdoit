package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollPeriodEvent;

@Repository
public interface PayrollPeriodEventRepository extends JpaRepository<PayrollPeriodEvent, Long> {
    List<PayrollPeriodEvent> findByPeriodIdOrderByIdAsc(Long periodId);
}
