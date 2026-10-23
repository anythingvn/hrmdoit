package vn.doit.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendanceSchedule;

/** Bộ giờ làm của công ty ({@code workSiteId} null) và của từng khách hàng, theo ngày hiệu lực. */
@Repository
public interface AttendanceScheduleRepository extends JpaRepository<AttendanceSchedule, Long> {
    /** Bộ giờ công ty có hiệu lực vào {@code day}. */
    Optional<AttendanceSchedule> findFirstByWorkSiteIdIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate day);

    /** Bộ giờ riêng của khách hàng có hiệu lực vào {@code day}. */
    Optional<AttendanceSchedule> findFirstByWorkSiteIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(Long workSiteId, LocalDate day);

    /** Bộ giờ công ty kế tiếp sau {@code day}. */
    Optional<AttendanceSchedule> findFirstByWorkSiteIdIsNullAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(LocalDate day);

    /** Bộ giờ riêng kế tiếp của khách hàng sau {@code day}. */
    Optional<AttendanceSchedule> findFirstByWorkSiteIdAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(Long workSiteId, LocalDate day);

    List<AttendanceSchedule> findByWorkSiteIdIsNullOrderByEffectiveFromDesc();

    List<AttendanceSchedule> findByWorkSiteIdOrderByEffectiveFromDesc(Long workSiteId);

    Optional<AttendanceSchedule> findByWorkSiteIdIsNullAndEffectiveFrom(LocalDate effectiveFrom);

    Optional<AttendanceSchedule> findByWorkSiteIdAndEffectiveFrom(Long workSiteId, LocalDate effectiveFrom);
}
