package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;

/**
 * Nghỉ phép khi cho nghỉ việc (Review Focus 4), chạy trong transaction của {@code TerminationService} (đã khóa hồ sơ).
 *
 * <p>Áp dụng nghỉ việc:
 * <ol>
 *   <li>đơn chờ duyệt / chờ hủy của người đó → đã hủy ("Nhân viên nghỉ việc"), đơn chờ hủy được hoàn quỹ;</li>
 *   <li>đơn đã duyệt bắt đầu sau ngày làm việc cuối → đã hủy và hoàn quỹ; đơn bắc qua ngày đó → rút ngắn về ngày làm việc cuối và
 *       hoàn phần sau;</li>
 *   <li>ghi các dòng chuyển năm / hết hạn còn thiếu, rồi chốt số dư bằng dòng {@code NGHI_VIEC} đưa từng ngăn của từng quỹ về 0
 *       (liên kết {@code TERMINATION}:id lần cho nghỉ). Các dòng này là số "phép chưa dùng" đã chốt cho kế toán (số dương đã chốt =
 *       âm của dòng). Vì sổ về 0, việc hết hạn 01/04 sau đó không lấy mất phần phải trả;</li>
 *   <li>các đơn đang chờ mà người đó là người duyệt → chuyển lên cấp kế tiếp.</li>
 * </ol>
 * Hoàn tác: ghi dòng {@code HOAN_TAC} đảo đúng từng dòng {@code NGHI_VIEC}; đơn đã tự hủy không tự khôi phục (báo lại cho HR).
 */
@Component
public class LeaveTerminationHook {

    /** Liên kết dòng chốt / hoàn tác: ref_id = id lần cho nghỉ việc. */
    public static final String REF_TERMINATION = "TERMINATION";
    static final String AUTO_NOTE = "Nhân viên nghỉ việc";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LeaveRequestRepository requests;
    private final LeaveRequestService requestService;
    private final LeaveLedgerService ledger;
    private final LeaveLedgerEntryRepository entries;
    private final Clock clock;

    public LeaveTerminationHook(
        LeaveRequestRepository requests,
        LeaveRequestService requestService,
        LeaveLedgerService ledger,
        LeaveLedgerEntryRepository entries,
        Clock clock
    ) {
        this.requests = requests;
        this.requestService = requestService;
        this.ledger = ledger;
        this.entries = entries;
        this.clock = clock;
    }

    /** Gọi khi nghỉ việc được áp dụng (sau khi hồ sơ đã chuyển sang đã nghỉ). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onApplied(Employee e, LocalDate lastWorkingDay, Long terminationId) {
        if (e.getPersonType() == PersonType.NHAN_VIEN) {
            ledger.lock(e.getId());
            for (LeaveRequest r : requests.findByEmployeeIdAndStatusIn(e.getId(), LeaveRequestService.ACTIVE)) {
                switch (r.getStatus()) {
                    case CHO_DUYET -> requestService.cancelBySystem(r, AUTO_NOTE);
                    case CHO_HUY -> {
                        requestService.refund(r, "Hủy đơn #" + r.getId() + " khi nghỉ việc");
                        requestService.cancelBySystem(r, AUTO_NOTE);
                    }
                    case DA_DUYET -> {
                        if (r.getFromDate().isAfter(lastWorkingDay)) {
                            requestService.refund(r, "Hủy đơn #" + r.getId() + " khi nghỉ việc");
                            requestService.cancelBySystem(r, AUTO_NOTE);
                        } else if (r.getToDate().isAfter(lastWorkingDay)) {
                            requestService.trimTo(r, lastWorkingDay, AUTO_NOTE);
                        }
                    }
                    default -> {
                        // Trạng thái đã kết thúc: không đổi.
                    }
                }
            }
            settle(e, lastWorkingDay, terminationId);
        }
        for (Long id : requests.findIdsByApproverAndStatusIn(e.getId(), LeaveRequestService.PENDING)) {
            requestService.reassignFrom(id, e.getId(), e.getFullName() + " nghỉ việc");
        }
    }

    /** Gọi khi hoàn tác nghỉ việc (sau khi hồ sơ đã trở lại). Trả ghi chú cho HR, hoặc null. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String onUndone(Employee e, TerminationRecord record) {
        Long ref = record.getId();
        List<LeaveLedgerEntry> settled = entries.findByEmployeeIdAndReasonAndRefTypeAndRefId(e.getId(), LedgerReason.NGHI_VIEC, REF_TERMINATION, ref);
        if (!settled.isEmpty() && !entries.existsByEmployeeIdAndReasonAndRefTypeAndRefId(e.getId(), LedgerReason.HOAN_TAC, REF_TERMINATION, ref)) {
            Map<Pool, List<Entry>> byPool = new EnumMap<>(Pool.class);
            for (LeaveLedgerEntry row : settled) {
                byPool.computeIfAbsent(row.getPool(), p -> new ArrayList<>()).add(new Entry(row.getPool(), row.getBucket(), row.getAmount().negate()));
            }
            byPool.forEach((pool, rows) ->
                ledger.post(e.getId(), pool, rows, LedgerReason.HOAN_TAC, REF_TERMINATION, ref, "Hoàn tác chốt số dư khi nghỉ việc")
            );
        }
        if (record.getAppliedAt() == null) {
            return null;
        }
        long cancelled = requests.countByEmployeeIdAndStatusAndDecisionNoteAndDecidedAtGreaterThanEqual(
            e.getId(),
            LeaveRequestStatus.DA_HUY,
            AUTO_NOTE,
            record.getAppliedAt()
        );
        return cancelled == 0
            ? null
            : cancelled + " đơn nghỉ đã tự hủy khi cho nghỉ việc không được khôi phục — nhân viên cần gửi lại nếu vẫn nghỉ.";
    }

    private void settle(Employee e, LocalDate lastWorkingDay, Long terminationId) {
        if (entries.existsByEmployeeIdAndReasonAndRefTypeAndRefId(e.getId(), LedgerReason.NGHI_VIEC, REF_TERMINATION, terminationId)) {
            return;
        }
        ledger.settle(e.getId(), LocalDate.now(clock));
        for (Pool pool : Pool.values()) {
            Balance b = ledger.balance(e.getId(), pool);
            List<Entry> rows = new ArrayList<>();
            if (b.namTruoc().signum() != 0) {
                rows.add(new Entry(pool, Bucket.NAM_TRUOC, b.namTruoc().negate()));
            }
            if (b.namNay().signum() != 0) {
                rows.add(new Entry(pool, Bucket.NAM_NAY, b.namNay().negate()));
            }
            if (!rows.isEmpty()) {
                BigDecimal total = b.total();
                String unit = pool == Pool.PHEP_NAM ? " ngày" : " giờ";
                ledger.post(
                    e.getId(),
                    pool,
                    rows,
                    LedgerReason.NGHI_VIEC,
                    REF_TERMINATION,
                    terminationId,
                    "Chốt số dư khi nghỉ việc (ngày làm việc cuối " +
                    lastWorkingDay.format(DMY) +
                    "): " +
                    total.stripTrailingZeros().toPlainString() +
                    unit
                );
            }
        }
    }
}
