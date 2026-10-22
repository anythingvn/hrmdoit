package vn.doit.hrm.repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;

@Repository
public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {
    /** Id hồ sơ của đơn (không nạp đơn), để khóa hồ sơ trước khi khóa đơn. */
    @Query("select r.employee.id from OvertimeRequest r where r.id = :id")
    Optional<Long> findEmployeeIdById(@Param("id") Long id);

    /** Khóa dòng đơn (sau khi đã khóa dòng hồ sơ của người gửi). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OvertimeRequest r where r.id = :id")
    Optional<OvertimeRequest> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = { "employee", "approverEmployee" })
    List<OvertimeRequest> findByEmployeeIdOrderByWorkDateDescIdDesc(Long employeeId);

    /** Có đơn ở các trạng thái này mà {@code approverId} là người duyệt (cờ "Duyệt đơn" trên tài khoản). */
    boolean existsByApproverEmployeeIdAndStatusIn(Long approverId, Collection<OvertimeStatus> statuses);

    @EntityGraph(attributePaths = { "employee", "approverEmployee" })
    List<OvertimeRequest> findByStatusInOrderByCreatedAtAscIdAsc(Collection<OvertimeStatus> statuses);

    List<OvertimeRequest> findByEmployeeIdAndWorkDateBetweenAndStatusIn(
        Long employeeId,
        LocalDate from,
        LocalDate to,
        Collection<OvertimeStatus> statuses
    );

    /**
     * Tổng giờ tính giới hạn của các đơn có ngày làm trong khoảng: giờ thực tế với đơn hoàn tất, giờ đăng ký với đơn đã duyệt / chờ
     * xác nhận phần chênh. Bỏ qua đơn {@code excludeId}.
     */
    @Query(
        "select coalesce(sum(case when r.status = vn.doit.hrm.domain.enumeration.OvertimeStatus.HOAN_TAT and r.actualHours is not null " +
        "then r.actualHours else r.plannedHours end), 0) from OvertimeRequest r where r.employee.id = :employeeId " +
        "and r.status in :statuses and r.workDate between :from and :to and r.id <> :excludeId"
    )
    BigDecimal sumHours(
        @Param("employeeId") Long employeeId,
        @Param("statuses") Collection<OvertimeStatus> statuses,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("excludeId") Long excludeId
    );

    /** Đơn ở các trạng thái {@code statuses} có ngày làm trước {@code before} (tự xác nhận). */
    @Query("select r.id from OvertimeRequest r where r.status in :statuses and r.workDate < :before order by r.id")
    List<Long> findIdsByStatusInAndWorkDateBefore(
        @Param("statuses") Collection<OvertimeStatus> statuses,
        @Param("before") LocalDate before
    );

    /** Đơn của người {@code employeeId} ở các trạng thái {@code statuses} có ngày làm sau {@code after} (nghỉ việc). */
    List<OvertimeRequest> findByEmployeeIdAndStatusInAndWorkDateAfter(Long employeeId, Collection<OvertimeStatus> statuses, LocalDate after);

    /** Đơn đang chờ người duyệt {@code approverId}. */
    @Query("select r.id from OvertimeRequest r where r.approverEmployee.id = :approverId and r.status in :statuses order by r.id")
    List<Long> findIdsByApproverAndStatusIn(@Param("approverId") Long approverId, @Param("statuses") Collection<OvertimeStatus> statuses);

    /** Đơn đang chờ có người duyệt là một hồ sơ (việc 00:20). */
    @Query("select r.id from OvertimeRequest r where r.status in :statuses and r.approverEmployee is not null order by r.id")
    List<Long> findIdsWithApproverEmployee(@Param("statuses") Collection<OvertimeStatus> statuses);

    /** Đơn có trạng thái, hình thức bù cho trước và ngày làm trong khoảng (tổng hợp cho kế toán: hoàn tất, trả tiền). */
    List<OvertimeRequest> findByStatusAndCompensationAndWorkDateBetween(
        OvertimeStatus status,
        vn.doit.hrm.domain.enumeration.OvertimeCompensation compensation,
        LocalDate from,
        LocalDate to
    );
}
