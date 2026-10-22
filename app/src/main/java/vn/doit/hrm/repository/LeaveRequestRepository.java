package vn.doit.hrm.repository;

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
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    /** Id hồ sơ của đơn (không nạp đơn), để khóa hồ sơ trước khi khóa đơn. */
    @Query("select r.employee.id from LeaveRequest r where r.id = :id")
    Optional<Long> findEmployeeIdById(@Param("id") Long id);

    /** Khóa dòng đơn (sau khi đã khóa dòng hồ sơ của người xin). */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = { "employee", "approverEmployee" })
    List<LeaveRequest> findByEmployeeIdAndStatusIn(Long employeeId, Collection<LeaveRequestStatus> statuses);

    @EntityGraph(attributePaths = { "employee", "approverEmployee" })
    List<LeaveRequest> findByEmployeeIdOrderByFromDateDescIdDesc(Long employeeId);

    /** Có đơn ở các trạng thái này mà {@code approverId} là người duyệt (cờ "Duyệt đơn" trên tài khoản). */
    boolean existsByApproverEmployeeIdAndStatusIn(Long approverId, Collection<LeaveRequestStatus> statuses);

    @EntityGraph(attributePaths = { "employee", "approverEmployee" })
    List<LeaveRequest> findByStatusInOrderByCreatedAtAscIdAsc(Collection<LeaveRequestStatus> statuses);

    @Query("select r.id from LeaveRequest r where r.approverEmployee.id = :approverId and r.status in :statuses order by r.id")
    List<Long> findIdsByApproverAndStatusIn(
        @Param("approverId") Long approverId,
        @Param("statuses") Collection<LeaveRequestStatus> statuses
    );

    @Query(
        "select r.id from LeaveRequest r where r.status in :statuses and r.approverEmployee is not null order by r.id"
    )
    List<Long> findIdsWithApproverEmployee(@Param("statuses") Collection<LeaveRequestStatus> statuses);

    /** Tổng số ngày (hoặc giờ với nghỉ bù) của các đơn theo loại và trạng thái. */
    @Query(
        "select coalesce(sum(case when r.type = vn.doit.hrm.domain.enumeration.LeaveRequestType.NGHI_BU then r.hours else r.days end), 0) " +
        "from LeaveRequest r where r.employee.id = :employeeId and r.type = :type and r.status in :statuses"
    )
    BigDecimal sumAmount(
        @Param("employeeId") Long employeeId,
        @Param("type") LeaveRequestType type,
        @Param("statuses") Collection<LeaveRequestStatus> statuses
    );

    /** Tổng số ngày của các đơn theo loại, trạng thái, có ngày bắt đầu trong khoảng. */
    @Query(
        "select coalesce(sum(r.days), 0) from LeaveRequest r where r.employee.id = :employeeId and r.type = :type " +
        "and r.status in :statuses and r.fromDate between :from and :to"
    )
    BigDecimal sumDaysStartingBetween(
        @Param("employeeId") Long employeeId,
        @Param("type") LeaveRequestType type,
        @Param("statuses") Collection<LeaveRequestStatus> statuses,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    long countByEmployeeIdAndStatusAndDecisionNoteAndDecidedAtGreaterThanEqual(
        Long employeeId,
        LeaveRequestStatus status,
        String decisionNote,
        java.time.Instant decidedAt
    );

    /** Đơn giao với khoảng ngày của lịch tháng (kèm hồ sơ và phòng ban, một truy vấn). */
    @Query(
        "select r from LeaveRequest r join fetch r.employee e left join fetch e.department where e.id in :ids and r.status in :statuses" +
        " and r.fromDate <= :to and r.toDate >= :from order by r.fromDate, r.id"
    )
    List<LeaveRequest> findForCalendar(
        @Param("ids") Collection<Long> ids,
        @Param("statuses") Collection<LeaveRequestStatus> statuses,
        @Param("from") java.time.LocalDate from,
        @Param("to") java.time.LocalDate to
    );
}
