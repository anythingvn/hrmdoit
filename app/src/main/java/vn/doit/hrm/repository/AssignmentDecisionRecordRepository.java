package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AssignmentDecisionRecord;

@Repository
public interface AssignmentDecisionRecordRepository extends JpaRepository<AssignmentDecisionRecord, Long> {
    List<AssignmentDecisionRecord> findByEmployeeIdOrderByIdDesc(Long employeeId);

    boolean existsByDecisionNo(String decisionNo);
}
