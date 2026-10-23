package vn.doit.hrm.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.enumeration.LedgerReason;

/**
 * Sổ phép: chỉ thêm và đọc. Cố ý không kế thừa {@code CrudRepository} nên không có cách sửa hay xóa dòng. Ngoại lệ duy nhất:
 * {@link #purgeByEmployeeId} khi login admin xóa hẳn một nhân viên đã lưu trữ (EmployeeDeletionService).
 */
@org.springframework.stereotype.Repository
public interface LeaveLedgerEntryRepository extends Repository<LeaveLedgerEntry, Long> {
    LeaveLedgerEntry save(LeaveLedgerEntry entry);

    /** Chỉ dùng khi xóa hẳn nhân viên: bỏ toàn bộ sổ phép của người đó. */
    @Modifying
    @Query("delete from LeaveLedgerEntry e where e.employeeId = :employeeId")
    int purgeByEmployeeId(@Param("employeeId") Long employeeId);

    List<LeaveLedgerEntry> findByEmployeeIdOrderByEffectiveDateAscIdAsc(Long employeeId);

    List<LeaveLedgerEntry> findByEmployeeIdIn(Collection<Long> employeeIds);

    boolean existsByEmployeeIdAndPoolAndReasonAndRefTypeAndRefId(
        Long employeeId,
        Pool pool,
        LedgerReason reason,
        String refType,
        Long refId
    );

    List<LeaveLedgerEntry> findByEmployeeIdAndReasonAndRefTypeAndRefId(Long employeeId, LedgerReason reason, String refType, Long refId);

    List<LeaveLedgerEntry> findByEmployeeIdAndReasonAndRefTypeStartingWithAndRefId(
        Long employeeId,
        LedgerReason reason,
        String refTypePrefix,
        Long refId
    );

    boolean existsByEmployeeIdAndReasonAndRefTypeAndRefId(Long employeeId, LedgerReason reason, String refType, Long refId);

    boolean existsByEmployeeIdAndPoolAndReason(Long employeeId, Pool pool, LedgerReason reason);

    /** Các dòng có lý do trong {@code reasons} và liên kết loại {@code refType} (vd chốt / hoàn tác khi nghỉ việc), mọi người. */
    List<LeaveLedgerEntry> findByReasonInAndRefType(Collection<LedgerReason> reasons, String refType);
}
