package vn.doit.hrm.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.WorkAssignment;

/**
 * Spring Data JPA repository for the WorkAssignment entity.
 */
@Repository
public interface WorkAssignmentRepository extends JpaRepository<WorkAssignment, Long> {
    default Optional<WorkAssignment> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<WorkAssignment> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<WorkAssignment> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select workAssignment from WorkAssignment workAssignment left join fetch workAssignment.employee left join fetch workAssignment.site",
        countQuery = "select count(workAssignment) from WorkAssignment workAssignment"
    )
    Page<WorkAssignment> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select workAssignment from WorkAssignment workAssignment left join fetch workAssignment.employee left join fetch workAssignment.site"
    )
    List<WorkAssignment> findAllWithToOneRelationships();

    @Query(
        "select workAssignment from WorkAssignment workAssignment left join fetch workAssignment.employee left join fetch workAssignment.site where workAssignment.id =:id"
    )
    Optional<WorkAssignment> findOneWithToOneRelationships(@Param("id") Long id);

    List<WorkAssignment> findByEmployeeId(Long employeeId);

    List<WorkAssignment> findByEmployeeIdIn(Collection<Long> employeeIds);

    /** Đợt công tác giao với khoảng ngày (ngày kết thúc để trống = chưa kết thúc). */
    @Query(
        "select w from WorkAssignment w join fetch w.employee e left join fetch e.department where e.id in :ids" +
        " and w.type = vn.doit.hrm.domain.enumeration.WorkLocationType.CONG_TAC and w.fromDate <= :to and (w.toDate is null or w.toDate >= :from)" +
        " order by w.fromDate, w.id"
    )
    List<WorkAssignment> findBusinessTripsForCalendar(
        @Param("ids") Collection<Long> ids,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    /** Nơi làm (kèm nơi làm việc) của các người {@code ids} giao với khoảng ngày (ngày bắt đầu/kết thúc để trống = không giới hạn). */
    @Query(
        "select w from WorkAssignment w left join fetch w.site where w.employee.id in :ids" +
        " and (w.fromDate is null or w.fromDate <= :to) and (w.toDate is null or w.toDate >= :from) order by w.fromDate, w.id"
    )
    List<WorkAssignment> findOverlapping(
        @Param("ids") Collection<Long> ids,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    /** Phân công onsite ({@code KHACH_HANG}) ở các khách hàng {@code siteIds} còn hiệu lực ngày {@code day}, kèm hồ sơ. */
    @Query(
        "select w from WorkAssignment w join fetch w.employee e join fetch w.site s where s.id in :siteIds" +
        " and w.type = vn.doit.hrm.domain.enumeration.WorkLocationType.KHACH_HANG" +
        " and (w.fromDate is null or w.fromDate <= :day) and (w.toDate is null or w.toDate >= :day)"
    )
    List<WorkAssignment> findOnsiteOn(@Param("siteIds") Collection<Long> siteIds, @Param("day") LocalDate day);

    /** Phân công onsite ({@code KHACH_HANG}) ở các khách hàng {@code siteIds} đã bắt đầu từ {@code day} trở về trước, kèm hồ sơ. */
    @Query(
        "select w from WorkAssignment w join fetch w.employee e join fetch w.site s where s.id in :siteIds" +
        " and w.type = vn.doit.hrm.domain.enumeration.WorkLocationType.KHACH_HANG and (w.fromDate is null or w.fromDate <= :day)"
    )
    List<WorkAssignment> findOnsiteStartedBy(@Param("siteIds") Collection<Long> siteIds, @Param("day") LocalDate day);

    /** Phân công onsite ({@code KHACH_HANG}) ở các khách hàng {@code siteIds} giao với khoảng ngày, kèm hồ sơ. */
    @Query(
        "select w from WorkAssignment w join fetch w.employee e join fetch w.site s where s.id in :siteIds" +
        " and w.type = vn.doit.hrm.domain.enumeration.WorkLocationType.KHACH_HANG" +
        " and (w.fromDate is null or w.fromDate <= :to) and (w.toDate is null or w.toDate >= :from)"
    )
    List<WorkAssignment> findOnsiteOverlapping(
        @Param("siteIds") Collection<Long> siteIds,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
