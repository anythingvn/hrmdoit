package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.AnnualAccrual;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
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

    /**
     * Liên kết dòng thu hồi phép tháng khi nghỉ việc ({@code DIEU_CHINH}) và dòng hoàn tác nó ({@code HOAN_TAC}): ref_id = id lần cho
     * nghỉ × 1 000 000 + yyyymm.
     */
    public static final String REF_ACCRUAL_REVOKE = "ACCRUAL_REVOKE";

    private static final long REVOKE_FACTOR = 1_000_000L;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    /**
     * Nghỉ việc (spec 3.2 "Tháng nghỉ việc: cộng hoặc thu hồi theo cùng quy tắc 50%", I1): với mỗi tháng từ tháng của ngày làm việc
     * cuối trở đi đã được cộng phép ({@code CONG_THANG}) — việc tự động cộng cả tháng khi chưa biết ngày nghỉ — mà theo quy tắc 50% thì
     * không được cộng (thời gian làm trong tháng dưới 50% ngày làm việc, hoặc tháng sau ngày làm việc cuối), ghi dòng {@code DIEU_CHINH}
     * đảo phần còn đang cộng của tháng đó (liên kết {@link #REF_ACCRUAL_REVOKE}, ngày hiệu lực ngày 01 của tháng). Không tính lại mức
     * năm (không áp dụng ngược thay đổi mức). Ghi một lần cho mỗi lần cho nghỉ và mỗi tháng. Người gọi đã khóa hồ sơ. Trả số dòng đã ghi.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAfterLastWorkingDay(Employee e, LocalDate lastWorkingDay, Long terminationId) {
        if (e.getJoinDate() == null || lastWorkingDay == null || terminationId == null) {
            return 0;
        }
        List<LeaveLedgerEntry> rows = entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId());
        YearMonth first = YearMonth.from(lastWorkingDay);
        int written = 0;
        for (LeaveLedgerEntry credit : rows) {
            if (
                credit.getReason() != LedgerReason.CONG_THANG ||
                credit.getPool() != Pool.PHEP_NAM ||
                !LeaveLedgerService.REF_MONTH.equals(credit.getRefType()) ||
                credit.getRefId() == null
            ) {
                continue;
            }
            long yyyymm = credit.getRefId();
            YearMonth month = YearMonth.of((int) (yyyymm / 100), (int) (yyyymm % 100));
            if (month.isBefore(first)) {
                continue;
            }
            boolean earned = AnnualAccrual.monthly(12, e.getJoinDate(), lastWorkingDay, month, calendarService.calendar(month.atDay(15))).signum() > 0;
            long ref = terminationId * REVOKE_FACTOR + yyyymm;
            if (earned || entries.existsByEmployeeIdAndReasonAndRefTypeAndRefId(e.getId(), LedgerReason.DIEU_CHINH, REF_ACCRUAL_REVOKE, ref)) {
                continue;
            }
            // Phần còn đang cộng của tháng: dòng cộng + các lần thu hồi trước (lần cho nghỉ khác) + các lần hoàn tác chúng.
            BigDecimal net = credit.getAmount();
            for (LeaveLedgerEntry r : rows) {
                if (REF_ACCRUAL_REVOKE.equals(r.getRefType()) && r.getRefId() != null && r.getRefId() % REVOKE_FACTOR == yyyymm) {
                    net = net.add(r.getAmount());
                }
            }
            if (net.signum() <= 0) {
                continue;
            }
            written += ledger
                .post(
                    e.getId(),
                    Pool.PHEP_NAM,
                    List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, net.negate())),
                    LedgerReason.DIEU_CHINH,
                    REF_ACCRUAL_REVOKE,
                    ref,
                    "Thu hồi phép tháng %02d/%d do nghỉ việc (ngày làm việc cuối %s)".formatted(
                        month.getMonthValue(),
                        month.getYear(),
                        lastWorkingDay.format(DMY)
                    ),
                    month.atDay(1)
                )
                .size();
        }
        return written;
    }

    /** Id lần cho nghỉ việc của một dòng thu hồi phép tháng. */
    static long terminationOfRevoke(long refId) {
        return refId / REVOKE_FACTOR;
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
