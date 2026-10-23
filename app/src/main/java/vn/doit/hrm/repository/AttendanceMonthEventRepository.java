package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.AttendanceMonthEvent;

/** Lịch sử chốt / mở lại bảng công. */
@Repository
public interface AttendanceMonthEventRepository extends JpaRepository<AttendanceMonthEvent, Long> {
    List<AttendanceMonthEvent> findByMonthOrderByAtAscIdAsc(String month);
}
