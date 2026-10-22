package vn.doit.hrm.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.enumeration.LedgerReason;

/**
 * Sổ phép: chỉ thêm và đọc. Cố ý không kế thừa {@code CrudRepository} nên không có cách sửa hay xóa dòng.
 */
@org.springframework.stereotype.Repository
public interface LeaveLedgerEntryRepository extends Repository<LeaveLedgerEntry, Long> {
    LeaveLedgerEntry save(LeaveLedgerEntry entry);

    List<LeaveLedgerEntry> findByEmployeeIdOrderByEffectiveDateAscIdAsc(Long employeeId);

    List<LeaveLedgerEntry> findByEmployeeIdIn(Collection<Long> employeeIds);

    boolean existsByEmployeeIdAndPoolAndReasonAndRefTypeAndRefId(
        Long employeeId,
        Pool pool,
        LedgerReason reason,
        String refType,
        Long refId
    );

    boolean existsByEmployeeIdAndPoolAndReason(Long employeeId, Pool pool, LedgerReason reason);
}
