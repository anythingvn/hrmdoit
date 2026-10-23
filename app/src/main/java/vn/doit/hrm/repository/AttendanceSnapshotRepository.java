package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendanceSnapshot;

/** Ảnh chụp số công lúc chốt tháng. */
@Repository
public interface AttendanceSnapshotRepository extends JpaRepository<AttendanceSnapshot, Long> {
    List<AttendanceSnapshot> findByMonth(String month);
}
