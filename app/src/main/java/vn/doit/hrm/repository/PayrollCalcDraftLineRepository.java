package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollCalcDraftLine;

@Repository
public interface PayrollCalcDraftLineRepository extends JpaRepository<PayrollCalcDraftLine, Long> {
    List<PayrollCalcDraftLine> findByDraftIdOrderByCodeAscIdAsc(Long draftId);
}
