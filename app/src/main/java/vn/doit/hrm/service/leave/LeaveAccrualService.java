package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.AnnualAccrual;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/** Việc chốt tháng (chạy mỗi ngày) cho một người, trong một transaction: chuyển năm, hết hạn, rồi cộng phép tháng. */
@Service
@Transactional
public class LeaveAccrualService {

    private final EmployeeRepository employeeRepository;
    private final LeaveLedgerEntryRepository entries;
    private final LeaveLedgerService ledger;
    private final WorkCalendarService calendarService;

    public LeaveAccrualService(
        EmployeeRepository employeeRepository,
        LeaveLedgerEntryRepository entries,
        LeaveLedgerService ledger,
        WorkCalendarService calendarService
    ) {
        this.employeeRepository = employeeRepository;
        this.entries = entries;
        this.ledger = ledger;
        this.calendarService = calendarService;
    }

    /**
     * Chốt tháng {@code month} cho một người. Nếu ngày vào thuộc tháng liền trước và tháng đó chưa được cộng (hồ sơ tạo sau khi tháng
     * vào đã qua, ví dụ nhập từ Excel đầu tháng sau), cộng luôn tháng vào (vẫn theo quy tắc ≥ 50% ngày làm việc). Ngày vào sớm hơn
     * thì không cộng bù: HR nhập số dư đầu kỳ. Tháng vào được ghi trước bước chuyển năm / hết hạn để phần chuyển năm tính cả nó.
     * Trả số dòng đã ghi (0 nếu không có gì phải làm hoặc đã làm rồi).
     */
    public int settle(Long employeeId, YearMonth month, LocalDate asOf) {
        Employee e = employeeRepository.findByIdForUpdate(employeeId).orElse(null);
        if (e == null || e.getPersonType() != PersonType.NHAN_VIEN) {
            return 0;
        }
        int written = 0;
        YearMonth previous = month.minusMonths(1);
        if (e.getJoinDate() != null && YearMonth.from(e.getJoinDate()).equals(previous)) {
            written += accrue(e, previous);
        }
        written += ledger.settle(employeeId, asOf);
        return written + accrue(e, month);
    }

    private int accrue(Employee e, YearMonth month) {
        LocalDate join = e.getJoinDate();
        LocalDate last = e.getLastWorkingDay();
        if (join == null || join.isAfter(month.atEndOfMonth())) {
            return 0;
        }
        if (last == null && e.getStatus() == EmploymentStatus.DA_NGHI) {
            return 0;
        }
        if (last != null && last.isBefore(month.atDay(1))) {
            return 0;
        }
        long ref = month.getYear() * 100L + month.getMonthValue();
        if (
            entries.existsByEmployeeIdAndPoolAndReasonAndRefTypeAndRefId(
                e.getId(),
                Pool.PHEP_NAM,
                LedgerReason.CONG_THANG,
                LeaveLedgerService.REF_MONTH,
                ref
            )
        ) {
            return 0;
        }
        int base = e.getAnnualLeaveDays() == null ? 12 : e.getAnnualLeaveDays();
        int annual = AnnualAccrual.annualDays(base, join, month);
        BigDecimal amount = AnnualAccrual.monthly(annual, join, last, month, calendarService.calendar(month.atDay(15)));
        if (amount.signum() <= 0) {
            return 0;
        }
        return ledger
            .post(
                e.getId(),
                Pool.PHEP_NAM,
                List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, amount)),
                LedgerReason.CONG_THANG,
                LeaveLedgerService.REF_MONTH,
                ref,
                "Cộng phép tháng %02d/%d (mức %d ngày/năm)".formatted(month.getMonthValue(), month.getYear(), annual),
                month.atDay(1)
            )
            .size();
    }
}
