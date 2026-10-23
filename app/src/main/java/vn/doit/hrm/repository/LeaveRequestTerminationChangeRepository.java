package vn.doit.hrm.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.LeaveRequestTerminationChange;

/** Các lần nghỉ việc rút ngắn / hủy đơn nghỉ (F3). */
@Repository
public interface LeaveRequestTerminationChangeRepository extends JpaRepository<LeaveRequestTerminationChange, Long> {
    boolean existsByLeaveRequestIdAndTerminationRecordId(Long leaveRequestId, Long terminationRecordId);

    /** Các lần đổi của những đơn này, theo thứ tự thời gian. */
    List<LeaveRequestTerminationChange> findByLeaveRequestIdInOrderByChangedAtAscIdAsc(Collection<Long> leaveRequestIds);
}
