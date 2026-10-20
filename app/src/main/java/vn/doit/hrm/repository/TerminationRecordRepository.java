package vn.doit.hrm.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;

/** Các lần cho nghỉ việc (hẹn, áp dụng, hủy, hoàn tác). */
@Repository
public interface TerminationRecordRepository extends JpaRepository<TerminationRecord, Long> {
    List<TerminationRecord> findByEmployeeIdOrderByIdDesc(Long employeeId);

    Optional<TerminationRecord> findFirstByEmployeeIdAndStatusOrderByIdDesc(Long employeeId, TerminationRecordStatus status);

    List<TerminationRecord> findByEmployeeIdInAndStatus(Collection<Long> employeeIds, TerminationRecordStatus status);

    boolean existsByEmployeeIdAndStatus(Long employeeId, TerminationRecordStatus status);

    /** Lịch hẹn đến hạn: ngày làm việc cuối đã qua (trước {@code today}). */
    @Query(
        "select r.id from TerminationRecord r where r.status = vn.doit.hrm.domain.enumeration.TerminationRecordStatus.SCHEDULED" +
        " and r.lastWorkingDay < :today order by r.lastWorkingDay, r.id"
    )
    List<Long> findDueIds(@Param("today") LocalDate today);

    /** Chỉ lấy id nhân viên (không nạp thực thể vào persistence context, để lần khóa sau đọc trạng thái mới nhất). */
    @Query("select r.employeeId from TerminationRecord r where r.id = :id")
    Optional<Long> findEmployeeIdById(@Param("id") Long id);

    /**
     * Khóa dòng (SELECT … FOR UPDATE): job chạy trên nhiều máy, hủy lịch và áp dụng đồng thời được xếp hàng; bên sau đọc
     * trạng thái mới nhất đã commit.
     */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TerminationRecord r where r.id = :id")
    Optional<TerminationRecord> findByIdForUpdate(@Param("id") Long id);
}
