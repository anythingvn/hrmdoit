package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.LeaveLedgerMath;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;

/**
 * Sổ phép: ghi dòng (khóa dòng hồ sơ trong transaction của người gọi) và tính số dư.
 *
 * <p>Số dư là tổng mọi dòng theo quỹ và ngăn, <b>tính tại hôm nay</b>: phần chuyển năm (01/01) và hết hạn (sau hạn dùng phép năm
 * trước, mặc định 31/03) của năm hiện tại mà việc tự động chưa ghi được cộng vào như đã ghi, để số dư và việc trừ phép không phụ
 * thuộc giờ chạy của việc tự động. Chuyển năm tính trên các dòng trước 01/01; hết hạn tính trên các dòng đến hết ngày hạn.
 */
@Service
@Transactional
public class LeaveLedgerService {

    /** Liên kết của dòng cộng phép tháng: ref_id = yyyymm. */
    public static final String REF_MONTH = "MONTH";
    /** Liên kết của dòng chuyển năm / hết hạn: ref_id = năm. */
    public static final String REF_YEAR = "YEAR";
    /** Liên kết của dòng số dư đầu kỳ. */
    public static final String REF_OPENING = "OPENING";

    static final String SYSTEM = "system";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Các dòng việc tự động sẽ ghi cho một quỹ (chuyển năm hoặc hết hạn). */
    record Pending(LedgerReason reason, int year, LocalDate effectiveDate, List<Entry> entries, String note) {}

    private final LeaveLedgerEntryRepository repository;
    private final EmployeeRepository employeeRepository;
    private final LeaveSettingService settings;
    private final Clock clock;

    public LeaveLedgerService(
        LeaveLedgerEntryRepository repository,
        EmployeeRepository employeeRepository,
        LeaveSettingService settings,
        Clock clock
    ) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.settings = settings;
        this.clock = clock;
    }

    /** Khóa dòng hồ sơ đến hết transaction của người gọi, để kiểm tra số dư rồi ghi không bị chen. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long employeeId) {
        employeeRepository
            .findByIdForUpdate(employeeId)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound"));
    }

    /** Ghi các dòng với ngày hiệu lực hôm nay. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<LeaveLedgerEntry> post(
        Long employeeId,
        Pool pool,
        List<Entry> entries,
        LedgerReason reason,
        String refType,
        Long refId,
        String note
    ) {
        return post(employeeId, pool, entries, reason, refType, refId, note, LocalDate.now(clock));
    }

    /**
     * Ghi các dòng (làm tròn 2 chữ số thập phân) sau khi khóa dòng hồ sơ. Không nhận danh sách rỗng, dòng bằng 0 sau làm tròn hay
     * dòng khác quỹ ({@link IllegalArgumentException}): người gọi kiểm tra trước.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<LeaveLedgerEntry> post(
        Long employeeId,
        Pool pool,
        List<Entry> entries,
        LedgerReason reason,
        String refType,
        Long refId,
        String note,
        LocalDate effectiveDate
    ) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Không có dòng sổ phép để ghi");
        }
        List<Entry> scaled = new ArrayList<>();
        for (Entry e : entries) {
            if (e.pool() != pool) {
                throw new IllegalArgumentException("Dòng thuộc quỹ " + e.pool() + ", không phải " + pool);
            }
            BigDecimal amount = e.amount().setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("Không ghi dòng sổ phép bằng 0");
            }
            scaled.add(new Entry(pool, e.bucket(), amount));
        }
        lock(employeeId);
        String by = SecurityUtils.getCurrentUserLogin().orElse(SYSTEM);
        List<LeaveLedgerEntry> saved = new ArrayList<>();
        for (Entry e : scaled) {
            saved.add(
                repository.save(
                    new LeaveLedgerEntry(
                        employeeId,
                        pool,
                        e.bucket(),
                        e.amount(),
                        effectiveDate,
                        reason,
                        refType,
                        refId,
                        note,
                        by,
                        clock.instant()
                    )
                )
            );
        }
        return saved;
    }

    /** Số dư hiện tại của một quỹ (xem lớp). */
    @Transactional(readOnly = true)
    public Balance balance(Long employeeId, Pool pool) {
        return balanceAsOf(repository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(employeeId), pool, LocalDate.now(clock));
    }

    /** Mọi dòng của một người, theo ngày hiệu lực. */
    @Transactional(readOnly = true)
    public List<LeaveLedgerEntry> entries(Long employeeId) {
        return repository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(employeeId);
    }

    /**
     * Ghi các dòng chuyển năm / hết hạn của năm {@code asOf} còn thiếu, cho mọi quỹ. Khóa hồ sơ trước khi đọc sổ nên chạy đồng thời
     * (nhiều bản) không ghi trùng. Trả số dòng đã ghi.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int settle(Long employeeId, LocalDate asOf) {
        lock(employeeId);
        List<LeaveLedgerEntry> rows = repository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(employeeId);
        int written = 0;
        for (Pool pool : Pool.values()) {
            for (Pending p : pending(rows, pool, asOf)) {
                written += post(employeeId, pool, p.entries(), p.reason(), REF_YEAR, (long) p.year(), p.note(), p.effectiveDate()).size();
            }
        }
        return written;
    }

    /** Số dư của {@code pool} từ các dòng đã đọc, cộng phần chuyển năm / hết hạn chưa ghi tính đến {@code asOf}. */
    @Transactional(readOnly = true)
    public Balance balanceAsOf(List<LeaveLedgerEntry> rows, Pool pool, LocalDate asOf) {
        List<Entry> all = entries(rows, pool, d -> true);
        pending(rows, pool, asOf).forEach(p -> all.addAll(p.entries()));
        return LeaveLedgerMath.balance(pool, all);
    }

    /** Các dòng chuyển năm và hết hạn của năm {@code asOf} mà sổ chưa có (rỗng nếu không có gì thay đổi). */
    List<Pending> pending(List<LeaveLedgerEntry> rows, Pool pool, LocalDate asOf) {
        int year = asOf.getYear();
        List<Pending> out = new ArrayList<>();
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        List<Entry> carry = List.of();
        if (!has(rows, pool, LedgerReason.CHUYEN_NAM, year)) {
            carry = LeaveLedgerMath.yearEndCarry(pool, LeaveLedgerMath.balance(pool, entries(rows, pool, d -> d.isBefore(jan1))));
            if (!carry.isEmpty()) {
                out.add(new Pending(LedgerReason.CHUYEN_NAM, year, jan1, carry, "Chuyển số dư năm " + (year - 1) + " sang năm " + year));
            }
        }
        LocalDate expiry = settings.carryExpiry(year);
        if (asOf.isAfter(expiry) && !has(rows, pool, LedgerReason.HET_HAN, year)) {
            List<Entry> upToExpiry = entries(rows, pool, d -> !d.isAfter(expiry));
            upToExpiry.addAll(carry);
            List<Entry> expired = LeaveLedgerMath.expire(pool, LeaveLedgerMath.balance(pool, upToExpiry));
            if (!expired.isEmpty()) {
                out.add(
                    new Pending(
                        LedgerReason.HET_HAN,
                        year,
                        expiry.plusDays(1),
                        expired,
                        "Hết hạn phần năm " + (year - 1) + " chuyển sang (hạn " + expiry.format(DMY) + ")"
                    )
                );
            }
        }
        return out;
    }

    private static boolean has(List<LeaveLedgerEntry> rows, Pool pool, LedgerReason reason, int year) {
        return rows
            .stream()
            .anyMatch(
                r ->
                    r.getPool() == pool &&
                    r.getReason() == reason &&
                    REF_YEAR.equals(r.getRefType()) &&
                    Long.valueOf(year).equals(r.getRefId())
            );
    }

    private static List<Entry> entries(List<LeaveLedgerEntry> rows, Pool pool, Predicate<LocalDate> effective) {
        List<Entry> out = new ArrayList<>();
        for (LeaveLedgerEntry r : rows) {
            if (r.getPool() == pool && effective.test(r.getEffectiveDate())) {
                out.add(r.toEntry());
            }
        }
        return out;
    }
}
