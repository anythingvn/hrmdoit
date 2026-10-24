package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TreeSet;
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
import vn.doit.hrm.repository.EmploymentPeriodRepository;
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

    /**
     * Cộng bù phép tháng sau khi nhận làm lại ({@code CONG_THANG}): ref_id = id lần làm việc trước × 1 000 000 + yyyymm. Tháng của lần
     * làm việc mới đã có dòng cộng từ lần làm việc trước (đã bị thu hồi khi nghỉ) được cộng bù phần còn thiếu, mỗi lần làm việc một lần.
     */
    public static final String REF_ACCRUAL_TOPUP = "ACCRUAL_TOPUP";

    private static final long REVOKE_FACTOR = 1_000_000L;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmployeeRepository employeeRepository;
    private final LeaveLedgerEntryRepository entries;
    private final LeaveLedgerService ledger;
    private final WorkCalendarService calendarService;
    private final Clock clock;
    private final EmploymentPeriodRepository periodRepository;
    private final LeaveSeniorityService seniority;

    public LeaveAccrualService(
        EmployeeRepository employeeRepository,
        LeaveLedgerEntryRepository entries,
        LeaveLedgerService ledger,
        WorkCalendarService calendarService,
        Clock clock,
        EmploymentPeriodRepository periodRepository,
        LeaveSeniorityService seniority
    ) {
        this.periodRepository = periodRepository;
        this.seniority = seniority;
        this.employeeRepository = employeeRepository;
        this.entries = entries;
        this.ledger = ledger;
        this.calendarService = calendarService;
        this.clock = clock;
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
        if (e.isArchived()) {
            // Hồ sơ đã lưu trữ (R19): chỉ chuyển năm / hết hạn trên sổ đã có, không bao giờ cộng phép tháng hay cộng bù.
            return ledger.settle(employeeId, asOf);
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
     * Nhận làm lại (R20): chốt mọi tháng từ {@code fromMonth} (tháng của ngày vào làm mới) đến tháng của {@code asOf}, kể cả cộng bù
     * tháng đã cộng rồi bị thu hồi ở lần nghỉ trước. Các tháng của năm trước năm {@code asOf} được ghi trước bước chuyển năm / hết hạn
     * (như {@link #settle}); các tháng còn lại ghi sau. Chạy lại không ghi thêm (mỗi dòng có liên kết riêng). Trả số dòng đã ghi.
     */
    public int settleFrom(Long employeeId, YearMonth fromMonth, LocalDate asOf) {
        Employee e = employeeRepository.findByIdForUpdate(employeeId).orElse(null);
        if (e == null || e.getPersonType() != PersonType.NHAN_VIEN) {
            return 0;
        }
        if (e.isArchived()) {
            return ledger.settle(employeeId, asOf);
        }
        YearMonth to = YearMonth.from(asOf);
        int written = 0;
        YearMonth m = fromMonth;
        for (; !m.isAfter(to) && m.getYear() < asOf.getYear(); m = m.plusMonths(1)) {
            written += accrue(e, m);
        }
        written += ledger.settle(employeeId, asOf);
        for (; !m.isAfter(to); m = m.plusMonths(1)) {
            written += accrue(e, m);
        }
        return written;
    }

    /**
     * Nghỉ việc (spec 3.2 "Tháng nghỉ việc: cộng hoặc thu hồi theo cùng quy tắc 50%", I1): với mỗi tháng từ tháng của ngày làm việc
     * cuối trở đi đã được cộng phép ({@code CONG_THANG}) — việc tự động cộng cả tháng khi chưa biết ngày nghỉ — mà theo quy tắc 50% thì
     * không được cộng (thời gian làm trong tháng dưới 50% ngày làm việc, hoặc tháng sau ngày làm việc cuối), ghi dòng {@code DIEU_CHINH}
     * đảo phần còn đang cộng của tháng đó (liên kết {@link #REF_ACCRUAL_REVOKE}, ngày hiệu lực ngày 01 của tháng). Không tính lại mức
     * năm (không áp dụng ngược thay đổi mức). Ghi một lần cho mỗi lần cho nghỉ và mỗi tháng. Người gọi đã khóa hồ sơ. Trả số dòng đã ghi.
     *
     * <p>Ngăn thu hồi (F4):
     * <ul>
     *   <li>tháng cùng năm với ngày ghi: thu hồi từ phép năm nay, ngày hiệu lực ngày 01 của tháng;</li>
     *   <li>tháng của năm trước năm ghi, khi dòng chuyển năm của năm ghi đã có (phần cộng của tháng đó đã sang phép năm trước): thu hồi
     *       từ phép năm trước, ngày hiệu lực là ngày ghi, nhưng không quá số dương của phép năm trước lúc ghi (tính cả phần hết hạn chưa
     *       ghi). Ngày đã hết hạn không bị thu hồi lần nữa; phép năm nay không bị đụng tới;</li>
     *   <li>tháng của năm trước khi chưa có dòng chuyển năm: như cũ, thu hồi từ phép năm nay ngày 01 của tháng, rồi bước chốt
     *       ({@code settle}) tính phần chuyển năm trên số đã thu hồi.</li>
     * </ul>
     * Hoàn tác ghi dòng {@code HOAN_TAC} cùng ngăn, cùng ngày hiệu lực.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAfterLastWorkingDay(Employee e, LocalDate lastWorkingDay, Long terminationId) {
        if (e.getJoinDate() == null || lastWorkingDay == null || terminationId == null) {
            return 0;
        }
        List<LeaveLedgerEntry> rows = entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId());
        YearMonth first = YearMonth.from(lastWorkingDay);
        LocalDate today = LocalDate.now(clock);
        boolean carried = rows
            .stream()
            .anyMatch(
                r ->
                    r.getPool() == Pool.PHEP_NAM &&
                    r.getReason() == LedgerReason.CHUYEN_NAM &&
                    LeaveLedgerService.REF_YEAR.equals(r.getRefType()) &&
                    Long.valueOf(today.getYear()).equals(r.getRefId())
            );
        // Phần phép năm trước còn thu hồi được (chỉ đọc khi cần).
        BigDecimal previousYearLeft = null;
        int written = 0;
        TreeSet<Long> creditedMonths = new TreeSet<>();
        for (LeaveLedgerEntry r : rows) {
            Long yyyymm = creditedMonth(r);
            if (yyyymm != null) {
                creditedMonths.add(yyyymm);
            }
        }
        for (long yyyymm : creditedMonths) {
            YearMonth month = YearMonth.of((int) (yyyymm / 100), (int) (yyyymm % 100));
            if (month.isBefore(first)) {
                continue;
            }
            boolean earned =
                AnnualAccrual.monthly(12, e.getJoinDate(), lastWorkingDay, month, calendarService.calendar(month.atDay(15))).signum() > 0;
            long ref = terminationId * REVOKE_FACTOR + yyyymm;
            if (
                earned || entries.existsByEmployeeIdAndReasonAndRefTypeAndRefId(e.getId(), LedgerReason.DIEU_CHINH, REF_ACCRUAL_REVOKE, ref)
            ) {
                continue;
            }
            // Phần còn đang cộng của tháng: dòng cộng (kể cả cộng bù sau khi nhận làm lại) + các lần thu hồi trước (lần cho nghỉ khác)
            // + các lần hoàn tác chúng.
            BigDecimal net = netCredited(rows, yyyymm);
            if (net.signum() <= 0) {
                continue;
            }
            Bucket bucket = Bucket.NAM_NAY;
            LocalDate effective = month.atDay(1);
            if (month.getYear() < today.getYear() && carried) {
                if (previousYearLeft == null) {
                    previousYearLeft = ledger.balance(e.getId(), Pool.PHEP_NAM).namTruoc().max(BigDecimal.ZERO);
                }
                net = net.min(previousYearLeft);
                if (net.signum() <= 0) {
                    continue;
                }
                previousYearLeft = previousYearLeft.subtract(net);
                bucket = Bucket.NAM_TRUOC;
                effective = today;
            }
            written += ledger
                .post(
                    e.getId(),
                    Pool.PHEP_NAM,
                    List.of(new Entry(Pool.PHEP_NAM, bucket, net.negate())),
                    LedgerReason.DIEU_CHINH,
                    REF_ACCRUAL_REVOKE,
                    ref,
                    "Thu hồi phép tháng %02d/%d do nghỉ việc (ngày làm việc cuối %s)".formatted(
                        month.getMonthValue(),
                        month.getYear(),
                        lastWorkingDay.format(DMY)
                    ),
                    effective
                )
                .size();
        }
        return written;
    }

    /** Id lần cho nghỉ việc của một dòng thu hồi phép tháng. */
    static long terminationOfRevoke(long refId) {
        return refId / REVOKE_FACTOR;
    }

    /**
     * Nhận làm lại (R8): tháng {@code month} (từ tháng vào làm mới) đã có dòng cộng của lần làm việc trước. So phần còn đang cộng của
     * tháng (dòng cộng, cộng bù, thu hồi và hoàn tác thu hồi) với mức lần làm việc hiện tại được hưởng (ngày vào làm mới, chưa nghỉ);
     * thiếu thì ghi một dòng cộng bù, liên kết theo lần làm việc trước và tháng (mỗi lần làm việc mỗi tháng một lần). Không bao giờ ghi
     * số âm. Hồ sơ chưa từng nhận làm lại không bị ảnh hưởng.
     *
     * <p>Chỉ cộng bù tháng có phần đã cộng bị thu hồi mà chưa bù lại (phần còn đang cộng nhỏ hơn dòng cộng tháng – R21): tháng đã cộng
     * đủ của lần làm việc mới không bao giờ bị tính lại theo mức phép năm hiện tại (thay đổi mức không áp dụng ngược).
     */
    private int topUp(Employee e, YearMonth month, int annual) {
        LocalDate join = e.getJoinDate();
        if (e.getLastWorkingDay() != null || e.getStatus() == EmploymentStatus.DA_NGHI || month.isBefore(YearMonth.from(join))) {
            return 0;
        }
        var period = periodRepository.findFirstByEmployeeIdOrderByIdDesc(e.getId()).orElse(null);
        if (period == null) {
            return 0;
        }
        long yyyymm = month.getYear() * 100L + month.getMonthValue();
        long ref = period.getId() * REVOKE_FACTOR + yyyymm;
        if (
            entries.existsByEmployeeIdAndPoolAndReasonAndRefTypeAndRefId(
                e.getId(),
                Pool.PHEP_NAM,
                LedgerReason.CONG_THANG,
                REF_ACCRUAL_TOPUP,
                ref
            )
        ) {
            return 0;
        }
        List<LeaveLedgerEntry> rows = entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId());
        BigDecimal net = netCredited(rows, yyyymm);
        if (net.compareTo(monthCredit(rows, yyyymm)) >= 0) {
            return 0;
        }
        BigDecimal entitled = AnnualAccrual.monthly(annual, join, null, month, calendarService.calendar(month.atDay(15)));
        BigDecimal shortfall = entitled.subtract(net);
        if (shortfall.signum() <= 0) {
            return 0;
        }
        return ledger
            .post(
                e.getId(),
                Pool.PHEP_NAM,
                List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, shortfall)),
                LedgerReason.CONG_THANG,
                REF_ACCRUAL_TOPUP,
                ref,
                "Cộng phép tháng %02d/%d sau khi nhận làm lại (mức %d ngày/năm)".formatted(month.getMonthValue(), month.getYear(), annual),
                month.atDay(1)
            )
            .size();
    }

    /** Tháng (yyyymm) của một dòng cộng phép tháng – cộng thường hoặc cộng bù; null nếu không phải. */
    private static Long creditedMonth(LeaveLedgerEntry r) {
        if (r.getReason() != LedgerReason.CONG_THANG || r.getPool() != Pool.PHEP_NAM || r.getRefId() == null) {
            return null;
        }
        if (LeaveLedgerService.REF_MONTH.equals(r.getRefType())) {
            return r.getRefId();
        }
        if (REF_ACCRUAL_TOPUP.equals(r.getRefType())) {
            return r.getRefId() % REVOKE_FACTOR;
        }
        return null;
    }

    /** Tổng các dòng cộng phép thường ({@code REF_MONTH}) của tháng yyyymm. */
    private static BigDecimal monthCredit(List<LeaveLedgerEntry> rows, long yyyymm) {
        BigDecimal sum = BigDecimal.ZERO;
        for (LeaveLedgerEntry r : rows) {
            if (
                r.getPool() == Pool.PHEP_NAM &&
                r.getReason() == LedgerReason.CONG_THANG &&
                LeaveLedgerService.REF_MONTH.equals(r.getRefType()) &&
                Long.valueOf(yyyymm).equals(r.getRefId())
            ) {
                sum = sum.add(r.getAmount());
            }
        }
        return sum;
    }

    /** Phần còn đang cộng của tháng yyyymm: dòng cộng, cộng bù, các dòng thu hồi khi nghỉ việc và hoàn tác chúng. */
    private static BigDecimal netCredited(List<LeaveLedgerEntry> rows, long yyyymm) {
        BigDecimal net = BigDecimal.ZERO;
        for (LeaveLedgerEntry r : rows) {
            if (r.getPool() != Pool.PHEP_NAM || r.getRefId() == null) {
                continue;
            }
            Long credited = creditedMonth(r);
            boolean revoke = REF_ACCRUAL_REVOKE.equals(r.getRefType()) && r.getRefId() % REVOKE_FACTOR == yyyymm;
            if ((credited != null && credited == yyyymm) || revoke) {
                net = net.add(r.getAmount());
            }
        }
        return net;
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
        // Mức năm theo thâm niên đến ngày cuối tháng và cài đặt đang có hiệu lực; dòng đã ghi không bao giờ tính lại (cộng bù chỉ bù
        // phần đã bị thu hồi, theo mức hiện tại).
        int annual = seniority.rate(e, month).annualDays();
        if (
            entries.existsByEmployeeIdAndPoolAndReasonAndRefTypeAndRefId(
                e.getId(),
                Pool.PHEP_NAM,
                LedgerReason.CONG_THANG,
                LeaveLedgerService.REF_MONTH,
                ref
            )
        ) {
            return topUp(e, month, annual);
        }
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
