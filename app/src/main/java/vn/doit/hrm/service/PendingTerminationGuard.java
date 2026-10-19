package vn.doit.hrm.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.repository.TerminationRecordRepository;

/**
 * Khi nhân viên có lịch nghỉ việc đang chờ, không cho tạo giai đoạn làm việc hoặc HĐ đã ký bắt đầu sau ngày làm việc cuối
 * (nếu không, job sẽ không áp dụng được lịch đó). Lỗi 400 {@code pendingtermination}.
 */
@Component
public class PendingTerminationGuard {

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TerminationRecordRepository recordRepository;

    public PendingTerminationGuard(TerminationRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public void checkStart(Long employeeId, LocalDate start) {
        if (employeeId == null || start == null) {
            return;
        }
        recordRepository
            .findFirstByEmployeeIdAndStatusOrderByIdDesc(employeeId, TerminationRecordStatus.SCHEDULED)
            .filter(r -> start.isAfter(r.getLastWorkingDay()))
            .ifPresent(r -> {
                throw new EmployeeRuleException(
                    "Nhân viên có lịch nghỉ việc ngày " + r.getLastWorkingDay().format(VN_DATE) + " — hủy lịch trước",
                    "pendingtermination"
                );
            });
    }
}
