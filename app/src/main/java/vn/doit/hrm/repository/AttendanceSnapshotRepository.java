package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendanceSnapshot;

/** Ảnh chụp số công lúc chốt tháng. */
@Repository
public interface AttendanceSnapshotRepository extends JpaRepository<AttendanceSnapshot, Long> {
    List<AttendanceSnapshot> findByMonth(String month);

    /** Xóa ảnh chụp cũ của tháng trước khi chốt lại. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = false)
    @org.springframework.data.jpa.repository.Query("delete from AttendanceSnapshot s where s.month = :month")
    void deleteByMonthInBulk(@org.springframework.data.repository.query.Param("month") String month);
}
