package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hr.leave.SickLeaveLimit;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.dto.LeaveAdjustmentDTO;
import vn.doit.hrm.service.leave.dto.LeaveBalanceDTO;
import vn.doit.hrm.service.leave.dto.LeaveLedgerEntryDTO;

/** Số dư và sổ phép để xem; HR điều chỉnh tay. Quyền xem sổ kiểm tra theo dữ liệu (chính mình, trưởng nhóm, trưởng phòng). */
@Service
@Transactional
public class LeaveBalanceService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999.99");

    private final EmployeeRepository employeeRepository;
    private final LeaveLedgerEntryRepository entries;
    private final LeaveLedgerService ledger;
    private final LeaveSettingService settings;
    private final LeaveRequestRepository requests;
    private final OvertimeRequestRepository overtime;
    private final Clock clock;

    public LeaveBalanceService(
        EmployeeRepository employeeRepository,
        LeaveLedgerEntryRepository entries,
        LeaveLedgerService ledger,
        LeaveSettingService settings,
        LeaveRequestRepository requests,
        OvertimeRequestRepository overtime,
        Clock clock
    ) {
        this.employeeRepository = employeeRepository;
        this.entries = entries;
        this.ledger = ledger;
        this.settings = settings;
        this.requests = requests;
        this.overtime = overtime;
        this.clock = clock;
    }

    /** Số dư của người đang đăng nhập; không gắn hồ sơ → 404 leavenoprofile; không phải NHAN_VIEN → 403 leavenotapplicable. */
    @Transactional(readOnly = true)
    public LeaveBalanceDTO mine() {
        String login = SecurityUtils.getCurrentUserLogin().orElseThrow(LeaveNoProfileException::new);
        Employee me = employeeRepository.findOneByUserLogin(login).orElseThrow(LeaveNoProfileException::new);
        if (me.getPersonType() != PersonType.NHAN_VIEN) {
            throw new LeaveNotApplicableException();
        }
        return personal(me);
    }

    /**
     * Số dư đầy đủ của một người NHAN_VIEN (ngày ốm đã nghỉ / mức, giờ làm thêm tháng này / giới hạn). Người gọi đã kiểm tra quyền xem.
     */
    @Transactional(readOnly = true)
    public LeaveBalanceDTO personal(Employee e) {
        LocalDate today = LocalDate.now(clock);
        List<LeaveLedgerEntry> rows = entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId());
        int sickLimit = SickLeaveLimit.daysPerYear(insuranceYears(e, today));
        // Ngày ốm đã nghỉ trong năm: đơn nghỉ ốm đã duyệt (kể cả đang chờ hủy) bắt đầu trong năm nay.
        BigDecimal sickUsed = requests.sumDaysStartingBetween(
            e.getId(),
            LeaveRequestType.OM,
            List.of(LeaveRequestStatus.DA_DUYET, LeaveRequestStatus.CHO_HUY),
            LocalDate.of(today.getYear(), 1, 1),
            LocalDate.of(today.getYear(), 12, 31)
        );
        // Giờ làm thêm tháng này, cách tính như giới hạn tháng (giờ thực tế khi đã hoàn tất, giờ đăng ký khi đã duyệt).
        YearMonth month = YearMonth.from(today);
        BigDecimal otHours = overtime.sumHours(e.getId(), OvertimeService.COUNTED, month.atDay(1), month.atEndOfMonth(), -1L);
        return toDto(
            e,
            rows,
            today,
            new LeaveBalanceDTO.Sick(scale(sickUsed), sickLimit),
            new LeaveBalanceDTO.Overtime(scale(otHours), settings.current().getOtMonthCap())
        );
    }

    /** Số dư của mọi nhân viên đang làm (loại NHAN_VIEN), lọc theo phòng nếu có; sắp theo mã. */
    @Transactional(readOnly = true)
    public List<LeaveBalanceDTO> company(Long departmentId) {
        LocalDate today = LocalDate.now(clock);
        List<Employee> people = employeeRepository
            .findByPersonType(PersonType.NHAN_VIEN)
            .stream()
            .filter(e -> e.getStatus() == EmploymentStatus.DANG_LAM_VIEC)
            .filter(e -> departmentId == null || (e.getDepartment() != null && departmentId.equals(e.getDepartment().getId())))
            .sorted(Comparator.comparing(Employee::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        Map<Long, List<LeaveLedgerEntry>> byEmployee = people.isEmpty()
            ? Map.of()
            : entries
                  .findByEmployeeIdIn(people.stream().map(Employee::getId).toList())
                  .stream()
                  .collect(Collectors.groupingBy(LeaveLedgerEntry::getEmployeeId));
        return people
            .stream()
            .map(e -> toDto(e, byEmployee.getOrDefault(e.getId(), List.of()), today, null, null))
            .toList();
    }

    /** Sổ phép của một người: chính mình, HR, Quản trị, trưởng nhóm hoặc trưởng phòng của người đó. */
    @Transactional(readOnly = true)
    public List<LeaveLedgerEntryDTO> ledgerOf(Long employeeId) {
        Employee target = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound"));
        if (!canView(target)) {
            throw new AccessDeniedException("Không có quyền xem sổ phép của người này");
        }
        if (target.getPersonType() != PersonType.NHAN_VIEN) {
            throw new LeaveNotApplicableException();
        }
        return entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(employeeId).stream().map(LeaveLedgerEntryDTO::of).toList();
    }

    /** HR / Quản trị cộng hoặc trừ tay, bắt buộc ghi chú → dòng {@code DIEU_CHINH}. */
    public List<LeaveLedgerEntryDTO> adjust(LeaveAdjustmentDTO dto) {
        String note = dto.note() == null ? "" : dto.note().trim();
        if (note.isEmpty()) {
            throw new EmployeeRuleException("Phải ghi lý do điều chỉnh", "leavenoterequired");
        }
        BigDecimal amount = dto.amount();
        if (amount.signum() == 0 || amount.stripTrailingZeros().scale() > 2 || amount.abs().compareTo(MAX_AMOUNT) > 0) {
            throw new EmployeeRuleException("Số điều chỉnh phải khác 0 và có tối đa 2 chữ số thập phân", "leaveamountinvalid");
        }
        Employee target = employeeRepository
            .findById(dto.employeeId())
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound"));
        if (ApproverResolver.isSelf(SecurityUtils.getCurrentUserLogin().orElse(null), target)) {
            throw new LeaveForbiddenException("Không tự điều chỉnh sổ phép của chính mình", "leaveselfadjust");
        }
        if (target.getPersonType() != PersonType.NHAN_VIEN) {
            throw new EmployeeRuleException("Người này không dùng phần nghỉ phép", "leavenotapplicable");
        }
        if (target.getStatus() == EmploymentStatus.DA_NGHI) {
            // Sổ đã chốt khi nghỉ việc: một dòng điều chỉnh sau đó sẽ không bao giờ được trả / trừ.
            throw new EmployeeRuleException("Nhân viên đã nghỉ việc, sổ phép đã chốt", "leaveemployeeterminated");
        }
        Bucket bucket = dto.bucket() == null ? Bucket.NAM_NAY : dto.bucket();
        LocalDate today = LocalDate.now(clock);
        // Dòng NAM_TRUOC ghi sau hạn sẽ không bao giờ hết hạn trong năm: chỉ nhận đến hết ngày hạn.
        if (bucket == Bucket.NAM_TRUOC && today.isAfter(settings.carryExpiry(today.getYear()))) {
            throw new EmployeeRuleException("Phép năm trước đã hết hạn; điều chỉnh vào phép năm nay", "leavebucketexpired");
        }
        ledger.lock(target.getId());
        if (dto.pool() == Pool.NGHI_BU && amount.signum() < 0) {
            // Quỹ nghỉ bù không bao giờ âm (không có giờ ứng).
            BigDecimal left = ledger.balance(target.getId(), Pool.NGHI_BU).total();
            if (left.add(amount).signum() < 0) {
                throw new LeaveInsufficientBalanceException(
                    "Không đủ giờ nghỉ bù để trừ: còn " + left.stripTrailingZeros().toPlainString() + " giờ",
                    scale(left)
                );
            }
        }
        return ledger
            .post(target.getId(), dto.pool(), List.of(new Entry(dto.pool(), bucket, amount)), LedgerReason.DIEU_CHINH, null, null, note)
            .stream()
            .map(LeaveLedgerEntryDTO::of)
            .toList();
    }

    private LeaveBalanceDTO toDto(
        Employee e,
        List<LeaveLedgerEntry> rows,
        LocalDate today,
        LeaveBalanceDTO.Sick sick,
        LeaveBalanceDTO.Overtime ot
    ) {
        Balance annual = ledger.balanceAsOf(rows, Pool.PHEP_NAM, today);
        Balance comp = ledger.balanceAsOf(rows, Pool.NGHI_BU, today);
        BigDecimal advance = BigDecimal.valueOf(settings.current().getAdvanceDays());
        LocalDate expiry = settings.carryExpiry(today.getYear());
        return new LeaveBalanceDTO(
            e.getId(),
            e.getCode(),
            e.getFullName(),
            e.getDepartment() == null ? null : e.getDepartment().getName(),
            new LeaveBalanceDTO.Annual(
                scale(annual.namTruoc()),
                scale(annual.namNay()),
                scale(annual.total()),
                scale(requestable(annual.total(), advance)),
                annual.namTruoc().signum() > 0 && !today.isAfter(expiry) ? expiry : null
            ),
            new LeaveBalanceDTO.Compensatory(scale(comp.namTruoc()), scale(comp.namNay()), scale(comp.total())),
            sick,
            ot
        );
    }

    /** Tổng làm tròn xuống bội số 0,5, cộng số ngày được ứng (theo cài đặt). */
    static BigDecimal requestable(BigDecimal total, BigDecimal advance) {
        BigDecimal floorHalf = total.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.FLOOR).divide(BigDecimal.valueOf(2));
        return floorHalf.add(advance);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Số năm đóng BHXH: trước khi vào công ty + số năm tròn từ tháng bắt đầu đóng ở công ty (hoặc ngày vào). */
    static int insuranceYears(Employee e, LocalDate today) {
        int prior = e.getPriorSocialInsuranceYears() == null ? 0 : e.getPriorSocialInsuranceYears();
        LocalDate start = e.getSocialInsuranceStart() != null ? e.getSocialInsuranceStart() : e.getJoinDate();
        if (start == null || start.isAfter(today)) {
            return prior;
        }
        return prior + Period.between(start, today).getYears();
    }

    /** Chính mình, HR, Quản trị, hoặc theo quan hệ trưởng nhóm / trưởng phòng ({@link LeaveSecurity#viaRelation}). */
    private static boolean canView(Employee target) {
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR)) {
            return true;
        }
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        return ApproverResolver.isSelf(login, target) || LeaveSecurity.viaRelation(login, target);
    }
}
