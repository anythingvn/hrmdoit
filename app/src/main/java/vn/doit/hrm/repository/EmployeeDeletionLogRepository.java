package vn.doit.hrm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.EmployeeDeletionLog;

/** Nhật ký xóa hẳn nhân viên (chỉ thêm và đọc). */
@Repository
public interface EmployeeDeletionLogRepository extends JpaRepository<EmployeeDeletionLog, Long> {
    Page<EmployeeDeletionLog> findAllByOrderByDeletedAtDescIdDesc(Pageable pageable);
}
