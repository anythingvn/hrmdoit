package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
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
 * Làm thêm giờ (R17, R20): đơn chờ duyệt / đã duyệt / chờ xác nhận phần chênh có ngày làm sau ngày làm việc cuối → đã hủy; đơn làm thêm đang chờ
 * người đó duyệt → chuyển lên cấp kế tiếp; đơn hoàn tất sau khi đã nghỉ với hình thức nghỉ bù được trả tiền ({@link OvertimeService}).
 *
 * <p>Trước khi chốt, phép tháng đã cộng cho tháng nghỉ (dưới 50% ngày làm việc) và các tháng sau ngày làm việc cuối được thu hồi
 * ({@link LeaveAccrualService#revokeAfterLastWorkingDay}, I1).
 *
 * <p>Hoàn tác: ghi dòng {@code HOAN_TAC} đảo đúng từng dòng {@code NGHI_VIEC} và từng dòng thu hồi phép tháng; đơn đã tự hủy (nghỉ và làm thêm) không tự khôi phục
 * (báo lại cho HR).
 */
@Component
public class LeaveTerminationHook {

    /** Liên kết dòng chốt / hoàn tác: ref_id = id lần cho nghỉ việc. */
    public static final String REF_TERMINATION = "TERMINATION";
    static final String AUTO_NOTE = "Nhân viên nghỉ việc";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LeaveRequestRepository requests;
    private final LeaveRequestService requestService;
    private final OvertimeService overtimeService;
    private final LeaveLedgerService ledger;
    private final LeaveLedgerEntryRepository entries;
    private final LeaveAccrualService accrualService;
    private final Clock clock;

    public LeaveTerminationHook(
        LeaveRequestRepository requests,
        LeaveRequestService requestService,
        OvertimeService overtimeService,
        LeaveLedgerService ledger,
        LeaveLedgerEntryRepository entries,
        LeaveAccrualService accrualService,
        Clock clock
    ) {
        this.requests = requests;
        this.requestService = requestService;
        this.overtimeService = overtimeService;
        this.ledger = ledger;
        this.entries = entries;
        this.accrualService = accrualService;
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
                        requestService.markChangedByTermination(r, terminationId);
                        requestService.refund(r, "Hủy đơn #" + r.getId() + " khi nghỉ việc");
                        requestService.cancelBySystem(r, AUTO_NOTE);
                    }
                    case DA_DUYET -> {
                        if (r.getFromDate().isAfter(lastWorkingDay)) {
                            requestService.markChangedByTermination(r, terminationId);
                            requestService.refund(r, "Hủy đơn #" + r.getId() + " khi nghỉ việc");
                            requestService.cancelBySystem(r, AUTO_NOTE);
                        } else if (r.getToDate().isAfter(lastWorkingDay)) {
                            requestService.trimTo(r, lastWorkingDay, AUTO_NOTE, terminationId);
                        }
                    }
                    default -> {
                        // Trạng thái đã kết thúc: không đổi.
                    }
                }
            }
            overtimeService.cancelOnTermination(e.getId(), lastWorkingDay, AUTO_NOTE);
            // Phép tháng đã cộng cho tháng nghỉ (dưới 50%) và các tháng sau ngày làm việc cuối: thu hồi trước khi chốt (I1).
            accrualService.revokeAfterLastWorkingDay(e, lastWorkingDay, terminationId);
            settle(e, lastWorkingDay, terminationId);
        }
        for (Long id : requests.findIdsByApproverAndStatusIn(e.getId(), LeaveRequestService.PENDING)) {
            requestService.reassignFrom(id, e.getId(), e.getFullName() + " nghỉ việc");
        }
        overtimeService.reassignFromApprover(e.getId());
    }

    /** Gọi khi hoàn tác nghỉ việc (sau khi hồ sơ đã trở lại). Trả ghi chú cho HR, hoặc null. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String onUndone(Employee e, TerminationRecord record) {
        Long ref = record.getId();
        List<LeaveLedgerEntry> settled = entries.findByEmployeeIdAndReasonAndRefTypeAndRefId(e.getId(), LedgerReason.NGHI_VIEC, REF_TERMINATION, ref);
        if (!settled.isEmpty() && !entries.existsByEmployeeIdAndReasonAndRefTypeAndRefId(e.getId(), LedgerReason.HOAN_TAC, REF_TERMINATION, ref)) {
            // Mỗi dòng đảo ngược mang đúng ngày hiệu lực của dòng chốt (R14): phần năm trước khôi phục vẫn hết hạn đúng hạn, phần năm
            // cũ khôi phục vẫn được chuyển năm như thể chưa từng chốt.
            Map<Pool, Map<LocalDate, List<Entry>>> byPoolAndDate = new EnumMap<>(Pool.class);
            for (LeaveLedgerEntry row : settled) {
                byPoolAndDate
                    .computeIfAbsent(row.getPool(), p -> new TreeMap<>())
                    .computeIfAbsent(row.getEffectiveDate(), d -> new ArrayList<>())
                    .add(new Entry(row.getPool(), row.getBucket(), row.getAmount().negate()));
            }
            byPoolAndDate.forEach((pool, byDate) ->
                byDate.forEach((date, rows) ->
                    ledger.post(e.getId(), pool, rows, LedgerReason.HOAN_TAC, REF_TERMINATION, ref, "Hoàn tác chốt số dư khi nghỉ việc", date)
                )
            );
        }
        undoRevokes(e, ref);
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

    /** Hoàn tác các dòng thu hồi phép tháng của lần cho nghỉ {@code terminationId} (I1): dòng {@code HOAN_TAC} cùng liên kết và ngày. */
    private void undoRevokes(Employee e, Long terminationId) {
        for (LeaveLedgerEntry row : entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId())) {
            if (
                row.getReason() != LedgerReason.DIEU_CHINH ||
                !LeaveAccrualService.REF_ACCRUAL_REVOKE.equals(row.getRefType()) ||
                row.getRefId() == null ||
                LeaveAccrualService.terminationOfRevoke(row.getRefId()) != terminationId.longValue() ||
                entries.existsByEmployeeIdAndReasonAndRefTypeAndRefId(e.getId(), LedgerReason.HOAN_TAC, row.getRefType(), row.getRefId())
            ) {
                continue;
            }
            ledger.post(
                e.getId(),
                row.getPool(),
                List.of(new Entry(row.getPool(), row.getBucket(), row.getAmount().negate())),
                LedgerReason.HOAN_TAC,
                row.getRefType(),
                row.getRefId(),
                "Hoàn tác thu hồi phép tháng khi nghỉ việc",
                row.getEffectiveDate()
            );
        }
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
