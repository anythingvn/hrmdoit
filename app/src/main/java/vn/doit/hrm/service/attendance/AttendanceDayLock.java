package vn.doit.hrm.service.attendance;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.service.EmployeeRuleException;

/**
 * Khóa trước khi đổi chấm công của một người trong một ngày (duyệt lần chấm, gửi / duyệt giải trình), cùng thứ tự với lưu giờ gõ tay:
 * hồ sơ người đó ({@code findByIdForUpdate}, cùng khóa với tự chấm), dòng tháng (tạo nếu chưa có), dòng kỳ lương. Giữ tới hết giao dịch
 * của người gọi. Tháng đã chốt → 400 {@code attendancemonthclosed}; kỳ lương tháng đó đã duyệt → 400 {@code attendancepayrollapproved}.
 */
@Component
public class AttendanceDayLock {

    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM/yyyy");

    private final EmployeeRepository employeeRepository;
    private final AttendanceMonthRepository monthRepository;
    private final PayrollPeriodRepository periodRepository;

    public AttendanceDayLock(
        EmployeeRepository employeeRepository,
        AttendanceMonthRepository monthRepository,
        PayrollPeriodRepository periodRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.monthRepository = monthRepository;
        this.periodRepository = periodRepository;
    }

    /**
     * Khóa hồ sơ {@code employeeId}, tháng và kỳ lương của {@code day}; kiểm tháng còn mở.
     *
     * @param blocked phần cuối câu lỗi tháng đã chốt, vd "không duyệt được lần chấm"
     * @param missing lỗi khi hồ sơ không còn
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockOpen(Long employeeId, LocalDate day, String blocked, Supplier<? extends RuntimeException> missing) {
        employeeRepository.findByIdForUpdate(employeeId).orElseThrow(missing);
        YearMonth month = YearMonth.from(day);
        String key = month.toString();
        AttendanceMonth monthRow = monthRepository.lockOrCreate(key);
        if (monthRow.getStatus() == AttendanceMonthStatus.DA_CHOT) {
            throw new EmployeeRuleException("Bảng công tháng " + month.format(MM_YYYY) + " đã chốt, " + blocked, "attendancemonthclosed");
        }
        if (periodRepository.findForUpdate(key).filter(p -> p.getStatus() == PayrollPeriodStatus.DA_DUYET).isPresent()) {
            throw new EmployeeRuleException(
                "Kỳ lương tháng " + month.format(MM_YYYY) + " đã duyệt, bảng công tháng này khóa; điều chỉnh ở kỳ sau",
                "attendancepayrollapproved"
            );
        }
    }
}
