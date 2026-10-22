package vn.doit.hrm.service.leave;

import static vn.doit.hrm.service.leave.LeaveSecurity.currentAuthorities;
import static vn.doit.hrm.service.leave.LeaveSecurity.currentLogin;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.DayPart;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.InsufficientLeaveException;
import vn.doit.hr.leave.LeaveDays;
import vn.doit.hr.leave.LeaveLedgerMath;
import vn.doit.hr.leave.PersonalLeaveReason;
import vn.doit.hr.leave.Pool;
import vn.doit.hr.leave.SickLeaveLimit;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.LeaveRequestTerminationChange;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.LeaveRequestTerminationChangeRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeDocumentService;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.PendingTerminationGuard;
import vn.doit.hrm.service.dto.EmployeeDocumentDTO;
import vn.doit.hrm.service.leave.ApproverResolver.Approver;
import vn.doit.hrm.service.leave.dto.LeaveRequestCreateDTO;
import vn.doit.hrm.service.leave.dto.LeaveRequestDTO;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Đơn nghỉ và luồng duyệt (spec 4.1, 5).
 *
 * <p>Mọi bước đổi trạng thái khóa dòng hồ sơ của người xin (cùng khóa của sổ phép) rồi mới khóa và đọc lại đơn, nên hai lần duyệt
 * song song cho hai đơn của cùng người chạy lần lượt: lần sau đọc số dư đã trừ của lần trước và bị chặn nếu xuống dưới mức ứng.
 * Trong khóa còn kiểm tra trạng thái và các dòng sổ đã có theo liên kết ({@code LEAVE_REQUEST}:id), nên một đơn chỉ bị trừ một
 * lần ({@code DUYET_DON}) và chỉ được hoàn một lần ({@code HUY_DON}).
 *
 * <p>Hoàn quỹ: phần đã trừ vào phép năm trước về lại ngăn đó; nếu đã quá hạn dùng (mặc định 31/03) hoặc đã sang năm khác thì phần đó
 * hết hạn ngay bằng dòng {@code HET_HAN} cùng liên kết đơn (I3) — không thành phép năm nay dùng mãi. Phần đã trừ vào phép năm nay về
 * phép năm nay.
 *
 * <p>Kỳ lương đã duyệt (I2): duyệt, đồng ý hủy và HR hủy đơn đã duyệt bị chặn (400 {@code leaveperiodlocked}) nếu kỳ lương của một tháng
 * mà đơn chạm tới đã duyệt. Nghỉ việc vẫn rút ngắn / hủy đơn; tổng hợp kế toán giữ nguyên tháng đã duyệt và đưa phần thay đổi vào tháng
 * ghi.
 */
@Service
@Transactional
public class LeaveRequestService {

    /** Liên kết dòng sổ của đơn: trừ ({@code DUYET_DON}) và hoàn ({@code HUY_DON}), ref_id = id đơn (R1). */
    public static final String REF_LEAVE_REQUEST = "LEAVE_REQUEST";
    /**
     * Hoàn phần đơn sau ngày làm việc cuối khi rút ngắn lúc nghỉ việc: ref_type = {@code LEAVE_TRIM:<id lần cho nghỉ>}, ref_id = id
     * đơn — mỗi lần rút ngắn (mỗi lần cho nghỉ) một liên kết riêng.
     */
    public static final String REF_LEAVE_TRIM_PREFIX = "LEAVE_TRIM:";

    static final List<LeaveRequestStatus> ACTIVE = List.of(LeaveRequestStatus.CHO_DUYET, LeaveRequestStatus.DA_DUYET, LeaveRequestStatus.CHO_HUY);
    static final List<LeaveRequestStatus> PENDING = List.of(LeaveRequestStatus.CHO_DUYET, LeaveRequestStatus.CHO_HUY);

    private static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(8);
    private static final int MAX_RANGE_DAYS = 366;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LeaveRequestRepository repository;
    private final EmployeeRepository employeeRepository;
    private final LeaveLedgerService ledger;
    private final LeaveLedgerEntryRepository entries;
    private final LeaveSettingService settings;
    private final WorkCalendarService calendarService;
    private final PendingTerminationGuard terminationGuard;
    private final ApproverResolver resolver;
    private final LeaveNotifier notifier;
    private final EmployeeDocumentService documentService;
    private final PayrollPeriodRepository periodRepository;
    private final LeaveRequestTerminationChangeRepository terminationChanges;
    private final Clock clock;

    public LeaveRequestService(
        LeaveRequestRepository repository,
        EmployeeRepository employeeRepository,
        LeaveLedgerService ledger,
        LeaveLedgerEntryRepository entries,
        LeaveSettingService settings,
        WorkCalendarService calendarService,
        PendingTerminationGuard terminationGuard,
        ApproverResolver resolver,
        LeaveNotifier notifier,
        EmployeeDocumentService documentService,
        PayrollPeriodRepository periodRepository,
        LeaveRequestTerminationChangeRepository terminationChanges,
        Clock clock
    ) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.ledger = ledger;
        this.entries = entries;
        this.settings = settings;
        this.calendarService = calendarService;
        this.terminationGuard = terminationGuard;
        this.resolver = resolver;
        this.notifier = notifier;
        this.documentService = documentService;
        this.periodRepository = periodRepository;
        this.terminationChanges = terminationChanges;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ gửi đơn

    /** Người đăng nhập (loại NHAN_VIEN, đang làm) gửi đơn; quỹ chưa bị trừ. */
    public LeaveRequestDTO submit(LeaveRequestCreateDTO dto) {
        Employee me = currentEmployee();
        if (me.getPersonType() != PersonType.NHAN_VIEN || me.getStatus() == EmploymentStatus.DA_NGHI) {
            throw new LeaveNotApplicableException();
        }
        if (dto.type() == null) {
            throw rule("Chọn loại đơn", "leavetypeinvalid");
        }
        LocalDate from = dto.fromDate();
        LocalDate to = dto.toDate();
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw rule("Khoảng ngày nghỉ không hợp lệ", "leavedatesinvalid");
        }
        DayPart first = dto.firstPart() == null ? DayPart.CA_NGAY : dto.firstPart();
        DayPart last = from.equals(to) ? first : dto.lastPart() == null ? DayPart.CA_NGAY : dto.lastPart();
        BigDecimal days = countDays(from, to, first, last);
        if (days.signum() <= 0) {
            throw rule("Khoảng ngày đã chọn không có ngày làm việc nào", "leavenoworkingdays");
        }
        ledger.lock(me.getId());
        LocalDate today = today();
        int pastDays = settings.current().getPastRequestDays();
        if (from.isBefore(today.minusDays(pastDays))) {
            throw rule("Chỉ được xin nghỉ cho ngày đã qua trong vòng " + pastDays + " ngày", "leavepasttoofar");
        }
        terminationGuard.checkStart(me.getId(), to);
        checkOverlap(me.getId(), from, to, first, last);

        BigDecimal hours = null;
        Boolean sickOverLimit = null;
        PersonalLeaveReason personal = null;
        switch (dto.type()) {
            case PHEP_NAM -> {
                Balance b = ledger.balance(me.getId(), Pool.PHEP_NAM);
                BigDecimal advance = BigDecimal.valueOf(settings.current().getAdvanceDays());
                BigDecimal left = LeaveBalanceService.requestable(b.total(), advance).subtract(pending(me.getId(), LeaveRequestType.PHEP_NAM));
                if (days.compareTo(left) > 0) {
                    throw new LeaveInsufficientBalanceException(
                        "Không đủ phép năm: còn được xin " + plain(left) + " ngày (đã trừ các đơn đang chờ), đơn cần " + plain(days) + " ngày",
                        scale(left)
                    );
                }
            }
            case NGHI_BU -> {
                hours = scale(days.multiply(HOURS_PER_DAY));
                BigDecimal left = ledger.balance(me.getId(), Pool.NGHI_BU).total().subtract(pending(me.getId(), LeaveRequestType.NGHI_BU));
                if (hours.compareTo(left) > 0) {
                    throw new LeaveInsufficientBalanceException(
                        "Không đủ giờ nghỉ bù: còn " + plain(left) + " giờ (đã trừ các đơn đang chờ), đơn cần " + plain(hours) + " giờ",
                        scale(left)
                    );
                }
            }
            case VIEC_RIENG -> {
                personal = dto.personalReason();
                if (personal == null) {
                    throw rule("Chọn lý do nghỉ việc riêng", "personalreasonrequired");
                }
                if (days.compareTo(BigDecimal.valueOf(personal.days())) > 0) {
                    throw rule(personal.label() + " được nghỉ tối đa " + personal.days() + " ngày", "personalleaveexceeded");
                }
            }
            case OM -> {
                int limit = SickLeaveLimit.daysPerYear(LeaveBalanceService.insuranceYears(me, today));
                BigDecimal used = repository.sumDaysStartingBetween(
                    me.getId(),
                    LeaveRequestType.OM,
                    ACTIVE,
                    LocalDate.of(from.getYear(), 1, 1),
                    LocalDate.of(from.getYear(), 12, 31)
                );
                sickOverLimit = used.add(days).compareTo(BigDecimal.valueOf(limit)) > 0;
            }
            case KHONG_LUONG -> {
                // Không trừ quỹ, không giới hạn.
            }
        }

        Approver approver = resolver.resolve(me, days, false);
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(me);
        r.setType(dto.type());
        r.setPersonalReason(personal);
        r.setFromDate(from);
        r.setToDate(to);
        r.setFirstPart(first);
        r.setLastPart(last);
        r.setDays(days);
        r.setHours(hours);
        r.setReasonText(trim(dto.reasonText(), 1000));
        r.setStatus(LeaveRequestStatus.CHO_DUYET);
        apply(r, approver);
        r.setCreatedAt(clock.instant());
        r.setSubmittedBy(currentLogin());
        r = repository.save(r);
        notifier.submitted(r);
        LeaveRequestDTO out = toDto(r, true);
        return sickOverLimit == null ? out : out.withSickOverLimit(sickOverLimit);
    }

    // ------------------------------------------------------------------ các bước

    /** Người gửi rút đơn đang chờ. */
    public LeaveRequestDTO withdraw(Long id) {
        LeaveRequest r = lockedRequest(id);
        requireRequester(r);
        requireStatus(r, LeaveRequestStatus.CHO_DUYET);
        r.setStatus(LeaveRequestStatus.DA_RUT);
        stamp(r, null, false);
        return toDto(r, true);
    }

    /** Người duyệt (hoặc HR / Quản trị duyệt thay) duyệt: trừ quỹ nếu loại đơn có quỹ. */
    public LeaveRequestDTO approve(Long id, String note) {
        LeaveRequest r = lockedRequest(id);
        requireDecider(r);
        requireStatus(r, LeaveRequestStatus.CHO_DUYET);
        requireOpenPeriods(r);
        deduct(r);
        r.setStatus(LeaveRequestStatus.DA_DUYET);
        stamp(r, note, onBehalf(r));
        notifier.decided(r);
        return toDto(r, true);
    }

    /** Từ chối, bắt buộc lý do. */
    public LeaveRequestDTO reject(Long id, String note) {
        requireNote(note);
        LeaveRequest r = lockedRequest(id);
        requireDecider(r);
        requireStatus(r, LeaveRequestStatus.CHO_DUYET);
        r.setStatus(LeaveRequestStatus.TU_CHOI);
        stamp(r, note, onBehalf(r));
        notifier.decided(r);
        return toDto(r, true);
    }

    /** Người gửi yêu cầu hủy đơn đã duyệt, khi ngày nghỉ chưa bắt đầu. */
    public LeaveRequestDTO cancelRequest(Long id) {
        LeaveRequest r = lockedRequest(id);
        requireRequester(r);
        requireStatus(r, LeaveRequestStatus.DA_DUYET);
        if (r.getFromDate().isBefore(today())) {
            throw rule("Ngày nghỉ đã bắt đầu hoặc đã qua; liên hệ HR để hủy đơn", "leavecancelpast");
        }
        r.setStatus(LeaveRequestStatus.CHO_HUY);
        notifier.cancelRequested(r);
        return toDto(r, true);
    }

    /** Người duyệt đồng ý hủy: hoàn quỹ. */
    public LeaveRequestDTO cancelApprove(Long id, String note) {
        LeaveRequest r = lockedRequest(id);
        requireDecider(r);
        requireStatus(r, LeaveRequestStatus.CHO_HUY);
        requireOpenPeriods(r);
        refund(r, "Hủy đơn #" + r.getId());
        r.setStatus(LeaveRequestStatus.DA_HUY);
        stamp(r, note, onBehalf(r));
        notifier.decided(r);
        return toDto(r, true);
    }

    /** Người duyệt không đồng ý hủy: đơn về đã duyệt, sổ giữ nguyên. */
    public LeaveRequestDTO cancelReject(Long id, String note) {
        LeaveRequest r = lockedRequest(id);
        requireDecider(r);
        requireStatus(r, LeaveRequestStatus.CHO_HUY);
        r.setStatus(LeaveRequestStatus.DA_DUYET);
        stamp(r, note, onBehalf(r));
        notifier.decided(r);
        return toDto(r, true);
    }

    /** HR / Quản trị hủy đơn (kể cả ngày đã qua); hoàn quỹ nếu đã trừ. Không hủy được đơn của chính mình. */
    public LeaveRequestDTO cancelByHr(Long id, String note) {
        LeaveRequest r = lockedRequest(id);
        if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN) || ApproverResolver.isSelf(currentLogin(), r.getEmployee())) {
            throw new AccessDeniedException("Không có quyền hủy đơn này");
        }
        if (!ACTIVE.contains(r.getStatus())) {
            throw statusError(r);
        }
        if (r.getStatus() != LeaveRequestStatus.CHO_DUYET) {
            // Đơn chờ duyệt chưa vào số liệu kỳ lương nào: hủy được cả khi kỳ đã duyệt.
            requireOpenPeriods(r);
            refund(r, "Hủy đơn #" + r.getId());
        }
        r.setStatus(LeaveRequestStatus.DA_HUY);
        stamp(r, note, onBehalf(r));
        notifier.decided(r);
        return toDto(r, true);
    }

    /** Giấy chứng nhận nghỉ hưởng BHXH (PDF / ảnh) cho đơn nghỉ ốm: người gửi hoặc HR / Quản trị. */
    public LeaveRequestDTO attachSickDocument(Long id, String fileName, String contentType, InputStream in) {
        LeaveRequest r = lockedRequest(id);
        boolean hr = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN);
        if (!hr && !ApproverResolver.isSelf(currentLogin(), r.getEmployee())) {
            throw new AccessDeniedException("Chỉ người gửi hoặc HR được đính kèm giấy nghỉ ốm");
        }
        if (r.getType() != LeaveRequestType.OM) {
            throw rule("Chỉ đơn nghỉ ốm có giấy chứng nhận nghỉ hưởng BHXH", "leavesickdocnotallowed");
        }
        if (r.getStatus() == LeaveRequestStatus.DA_RUT || r.getStatus() == LeaveRequestStatus.TU_CHOI) {
            throw statusError(r);
        }
        EmployeeDocumentDTO doc = documentService
            .uploadSickCertificate(r.getEmployee().getId(), fileName, contentType, in)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));
        r.setSickDocId(doc.getId());
        return toDto(r, true);
    }

    // ------------------------------------------------------------------ xem

    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> mine() {
        Employee me = currentEmployee();
        if (me.getPersonType() != PersonType.NHAN_VIEN) {
            throw new LeaveNotApplicableException();
        }
        return repository.findByEmployeeIdOrderByFromDateDescIdDesc(me.getId()).stream().map(r -> toDto(r, true)).toList();
    }

    /**
     * Mọi đơn nghỉ của một người (tab "Nghỉ phép" trên hồ sơ), mới trước. Người gọi đã kiểm tra quyền xem; lý do và giấy ốm vẫn chỉ hiện
     * với người gửi, người duyệt của đơn, HR và Quản trị.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> ofEmployee(Long employeeId) {
        return repository.findByEmployeeIdOrderByFromDateDescIdDesc(employeeId).stream().map(r -> toDto(r, canSeeReason(r))).toList();
    }

    /** Đơn chờ duyệt / chờ hủy mà người đăng nhập quyết định được, kèm số dư người xin. */
    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> inbox() {
        String login = currentLogin();
        Set<String> auths = currentAuthorities();
        BigDecimal advance = BigDecimal.valueOf(settings.current().getAdvanceDays());
        List<LeaveRequestDTO> out = new ArrayList<>();
        for (LeaveRequest r : repository.findByStatusInOrderByCreatedAtAscIdAsc(PENDING)) {
            if (resolver.canDecide(login, auths, r)) {
                Long emp = r.getEmployee().getId();
                BigDecimal annual = ledger.balance(emp, Pool.PHEP_NAM).total();
                out.add(
                    toDto(r, true).withBalance(
                        new LeaveRequestDTO.RequesterBalance(
                            scale(annual),
                            scale(LeaveBalanceService.requestable(annual, advance)),
                            scale(ledger.balance(emp, Pool.NGHI_BU).total())
                        )
                    )
                );
            }
        }
        return out;
    }

    /**
     * Một đơn: người gửi, người duyệt, HR, Quản trị, BGĐ, trưởng nhóm và trưởng phòng của người gửi xem được; lý do và giấy ốm chỉ
     * người gửi, người duyệt của đơn, HR và Quản trị.
     */
    @Transactional(readOnly = true)
    public LeaveRequestDTO get(Long id) {
        LeaveRequest r = find(id);
        if (!canSee(r)) {
            throw new AccessDeniedException("Không có quyền xem đơn này");
        }
        return toDto(r, canSeeReason(r));
    }

    /** Giấy ốm của đơn: như lý do nghỉ. */
    @Transactional(readOnly = true)
    public EmployeeDocumentService.StoredFile sickDocument(Long id) {
        LeaveRequest r = find(id);
        if (!canSeeReason(r)) {
            throw new AccessDeniedException("Không có quyền xem giấy nghỉ ốm của đơn này");
        }
        if (r.getSickDocId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Đơn chưa có giấy nghỉ ốm");
        }
        return documentService
            .downloadSickDocument(r.getSickDocId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giấy nghỉ ốm"));
    }

    // ------------------------------------------------------------------ việc tự động, nghỉ việc

    /** Id các đơn đang chờ có người duyệt là một hồ sơ (việc 00:20 xét từng đơn, {@link #reassignOrphan}). */
    @Transactional(readOnly = true)
    public List<Long> orphanCandidates() {
        return repository.findIdsWithApproverEmployee(PENDING);
    }

    /**
     * Chuyển đơn {@code id} lên cấp kế tiếp nếu người duyệt đã nghỉ việc hoặc bị khóa tài khoản (việc 00:20, mỗi đơn một transaction).
     * Trả {@code true} nếu đã chuyển.
     */
    public boolean reassignOrphan(Long id) {
        // Khóa như mọi bước khác rồi đọc lại: không ghi đè một quyết định vừa có.
        LeaveRequest r = lockedRequest(id);
        if (!PENDING.contains(r.getStatus()) || r.getApproverEmployee() == null || resolver.available(r.getApproverEmployee(), r.getEmployee())) {
            return false;
        }
        reassign(r, r.getApproverEmployee().getFullName() + " đã nghỉ việc hoặc tài khoản bị khóa");
        return true;
    }

    /** Chuyển đơn {@code id} lên cấp kế tiếp nếu nó vẫn đang chờ người duyệt {@code approverId} (nghỉ việc). */
    void reassignFrom(Long id, Long approverId, String why) {
        LeaveRequest r = lockedRequest(id);
        if (PENDING.contains(r.getStatus()) && r.getApproverEmployee() != null && approverId.equals(r.getApproverEmployee().getId())) {
            reassign(r, why);
        }
    }

    /** Chuyển đơn (đã khóa) lên cấp duyệt kế tiếp, ghi lý do và báo người duyệt mới. */
    private void reassign(LeaveRequest r, String why) {
        Approver next = resolver.escalate(r.getEmployee(), r.getApproverKind());
        apply(r, next);
        r.setSystemNote(trim("Chuyển người duyệt ngày " + today().format(DMY) + ": " + why, 500));
        notifier.reassigned(r);
    }

    /** Hệ thống hủy đơn (nghỉ việc). */
    void cancelBySystem(LeaveRequest r, String note) {
        r.setStatus(LeaveRequestStatus.DA_HUY);
        stamp(r, note, false);
    }

    /**
     * Rút ngắn đơn đã duyệt về ngày làm việc cuối (lần cho nghỉ {@code terminationId}), hoàn phần đã trừ mà chưa hoàn, vượt quá số của
     * đơn sau khi rút ngắn, về đúng ngăn đã trừ ({@link #refundToBuckets}, liên kết {@code LEAVE_TRIM:<terminationId>}, mỗi lần cho
     * nghỉ một lần — R15). Không còn ngày làm việc nào thì hủy và hoàn cả đơn.
     */
    void trimTo(LeaveRequest r, LocalDate lastDay, String cancelNote, Long terminationId) {
        markChangedByTermination(r, terminationId);
        DayPart newLast = r.getFromDate().equals(lastDay) ? r.getFirstPart() : DayPart.CA_NGAY;
        BigDecimal newDays = countDays(r.getFromDate(), lastDay, r.getFirstPart(), newLast);
        if (newDays.signum() <= 0) {
            refund(r, "Hủy đơn #" + r.getId() + " khi nghỉ việc");
            cancelBySystem(r, cancelNote);
            return;
        }
        LocalDate oldTo = r.getToDate();
        r.setToDate(lastDay);
        r.setLastPart(newLast);
        r.setDays(newDays);
        if (r.getType() == LeaveRequestType.NGHI_BU) {
            r.setHours(scale(newDays.multiply(HOURS_PER_DAY)));
        }
        Pool pool = r.getType().pool();
        Long emp = r.getEmployee().getId();
        String refType = REF_LEAVE_TRIM_PREFIX + terminationId;
        if (pool != null && !entries.existsByEmployeeIdAndReasonAndRefTypeAndRefId(emp, LedgerReason.HUY_DON, refType, r.getId())) {
            // Hoàn phần đang bị trừ vượt số của đơn sau khi rút ngắn.
            refundToBuckets(r, pool, r.poolAmount(), refType, "Hoàn phần đơn #" + r.getId() + " sau ngày làm việc cuối " + lastDay.format(DMY));
        }
        r.setSystemNote(
            trim("Rút ngắn đến ngày làm việc cuối " + lastDay.format(DMY) + " khi nghỉ việc (trước đó đến " + oldTo.format(DMY) + ")", 500)
        );
    }

    /**
     * Ghi lại khoảng ngày của đơn (đã duyệt / chờ hủy) ngay trước khi lần cho nghỉ {@code terminationId} rút ngắn hoặc hủy nó, cùng
     * thời điểm (F3): mỗi lần cho nghỉ một dòng {@code leave_request_termination_change}, gọi lại trong cùng lần thì bỏ qua. Tổng hợp
     * kế toán dùng các dòng này để giữ mỗi tháng đã duyệt lương theo khoảng ngày lúc duyệt và đưa phần chênh của từng lần đổi vào tháng
     * ghi của lần đó. Các cột {@code termination_orig_*} trên đơn chỉ giữ lần đầu (dữ liệu cũ của I2, không còn được đọc).
     */
    void markChangedByTermination(LeaveRequest r, Long terminationId) {
        if (terminationId != null && terminationChanges.existsByLeaveRequestIdAndTerminationRecordId(r.getId(), terminationId)) {
            return;
        }
        Instant now = clock.instant();
        terminationChanges.save(new LeaveRequestTerminationChange(r.getId(), terminationId, now, r.getToDate(), r.getLastPart(), r.getDays()));
        if (r.getTerminationChangedAt() == null) {
            r.setTerminationOrigToDate(r.getToDate());
            r.setTerminationOrigLastPart(r.getLastPart());
            r.setTerminationOrigDays(r.getDays());
            r.setTerminationChangedAt(now);
        }
    }

    /** Các dòng hoàn khi rút ngắn đơn lúc nghỉ việc (mọi lần). */
    private List<LeaveLedgerEntry> trimRefunds(Long employeeId, Long requestId) {
        return entries.findByEmployeeIdAndReasonAndRefTypeStartingWithAndRefId(employeeId, LedgerReason.HUY_DON, REF_LEAVE_TRIM_PREFIX, requestId);
    }

    /**
     * Hoàn quỹ của đơn đã trừ, một lần (dòng {@code HUY_DON}, liên kết {@code LEAVE_REQUEST}). Người gọi đã khóa hồ sơ. Hoàn phần còn
     * đang bị trừ (đã trừ − các lần rút ngắn đã hoàn) về đúng ngăn đã trừ ({@link #refundToBuckets}).
     */
    void refund(LeaveRequest r, String note) {
        Pool pool = r.getType().pool();
        if (pool == null) {
            return;
        }
        Long emp = r.getEmployee().getId();
        if (entries.existsByEmployeeIdAndReasonAndRefTypeAndRefId(emp, LedgerReason.HUY_DON, REF_LEAVE_REQUEST, r.getId())) {
            throw rule("Đơn đã được hoàn quỹ", "leavealreadyrefunded");
        }
        refundToBuckets(r, pool, BigDecimal.ZERO, REF_LEAVE_REQUEST, note);
    }

    /**
     * Hoàn phần đang bị trừ của đơn vượt quá {@code keep} (0 = hoàn hết) về đúng ngăn đã trừ, dùng chung cho hủy đơn và rút ngắn khi
     * nghỉ việc (I3, F2):
     * <ul>
     *   <li>số đang bị trừ ở mỗi ngăn = phần dòng {@code DUYET_DON} của ngăn đó − các lần rút ngắn trước đã hoàn vào ngăn đó;</li>
     *   <li>hoàn một phần (rút ngắn): ngày bị bỏ là các ngày cuối của đơn, còn khi trừ thì phép năm trước dùng trước, nên phần hoàn lấy
     *       từ phép năm nay trước rồi mới đến phép năm trước;</li>
     *   <li>phần về phép năm trước mà đã quá hạn dùng (mặc định 31/03) hoặc đã sang năm khác thì hết hạn ngay: cặp dòng {@code HUY_DON +x}
     *       và {@code HET_HAN −x} cùng ngăn, cùng liên kết, nên không dùng được và không vào số chốt khi nghỉ việc.</li>
     * </ul>
     * Ghi dòng {@code HUY_DON} (và {@code HET_HAN}) với liên kết {@code refType}:id đơn. Không có gì để hoàn thì không ghi.
     */
    private void refundToBuckets(LeaveRequest r, Pool pool, BigDecimal keep, String refType, String note) {
        Long emp = r.getEmployee().getId();
        List<LeaveLedgerEntry> taken = entries.findByEmployeeIdAndReasonAndRefTypeAndRefId(emp, LedgerReason.DUYET_DON, REF_LEAVE_REQUEST, r.getId());
        if (taken.isEmpty()) {
            return;
        }
        Map<Bucket, BigDecimal> outstanding = new EnumMap<>(Bucket.class);
        boolean previousYearUsable = false;
        LocalDate today = today();
        LocalDate expiry = settings.carryExpiry(today.getYear());
        for (LeaveLedgerEntry row : taken) {
            outstanding.merge(row.getBucket(), row.getAmount().negate(), BigDecimal::add);
            if (row.getBucket() == Bucket.NAM_TRUOC && row.getYear() == today.getYear() && !today.isAfter(expiry)) {
                previousYearUsable = true;
            }
        }
        for (LeaveLedgerEntry row : trimRefunds(emp, r.getId())) {
            outstanding.merge(row.getBucket(), row.getAmount().negate(), BigDecimal::add);
        }
        BigDecimal total = outstanding.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal toRefund = total.subtract(keep);
        Map<Bucket, BigDecimal> back = new EnumMap<>(Bucket.class);
        for (Bucket bucket : List.of(Bucket.NAM_NAY, Bucket.NAM_TRUOC)) {
            BigDecimal part = toRefund.min(outstanding.getOrDefault(bucket, BigDecimal.ZERO).max(BigDecimal.ZERO));
            if (part.signum() > 0) {
                back.put(bucket, part);
                toRefund = toRefund.subtract(part);
            }
        }
        if (back.isEmpty()) {
            return;
        }
        List<Entry> rows = new ArrayList<>();
        back.forEach((bucket, amount) -> rows.add(new Entry(pool, bucket, amount)));
        BigDecimal expiredAgain = previousYearUsable ? BigDecimal.ZERO : back.getOrDefault(Bucket.NAM_TRUOC, BigDecimal.ZERO);
        if (expiredAgain.signum() > 0) {
            // Ghi các dòng chuyển năm / hết hạn còn thiếu trước, để phần hoàn rồi hết hạn ngay nằm sau dòng hết hạn của năm.
            ledger.settle(emp, today);
        }
        ledger.post(emp, pool, rows, LedgerReason.HUY_DON, refType, r.getId(), note);
        if (expiredAgain.signum() > 0) {
            ledger.post(
                emp,
                pool,
                List.of(new Entry(pool, Bucket.NAM_TRUOC, expiredAgain.negate())),
                LedgerReason.HET_HAN,
                refType,
                r.getId(),
                "Hết hạn phần phép năm trước hoàn từ đơn #" + r.getId() + " (đã quá hạn dùng)"
            );
        }
    }

    // ------------------------------------------------------------------ nội bộ

    /** Trừ quỹ khi duyệt (một lần): kiểm tra lại số dư trong khóa, không cho xuống dưới mức ứng. */
    private void deduct(LeaveRequest r) {
        Pool pool = r.getType().pool();
        if (pool == null) {
            return;
        }
        Long emp = r.getEmployee().getId();
        if (entries.existsByEmployeeIdAndReasonAndRefTypeAndRefId(emp, LedgerReason.DUYET_DON, REF_LEAVE_REQUEST, r.getId())) {
            throw rule("Đơn đã được trừ quỹ", "leavealreadyposted");
        }
        BigDecimal amount = scale(r.poolAmount());
        if (amount.signum() <= 0) {
            throw rule("Đơn không có ngày làm việc nào", "leavenoworkingdays");
        }
        BigDecimal maxAdvance = pool == Pool.PHEP_NAM ? BigDecimal.valueOf(settings.current().getAdvanceDays()) : BigDecimal.ZERO;
        Balance b = ledger.balance(emp, pool);
        List<Entry> rows;
        try {
            rows = LeaveLedgerMath.deduct(pool, b, amount, maxAdvance);
        } catch (InsufficientLeaveException e) {
            String unit = pool == Pool.PHEP_NAM ? " ngày phép năm" : " giờ nghỉ bù";
            throw new LeaveInsufficientBalanceException(
                "Không đủ số dư để duyệt: còn " + plain(b.total()) + unit + ", đơn cần " + plain(amount) + unit,
                scale(b.total())
            );
        }
        ledger.post(
            emp,
            pool,
            rows,
            LedgerReason.DUYET_DON,
            REF_LEAVE_REQUEST,
            r.getId(),
            "Duyệt đơn #" + r.getId() + " (" + r.getFromDate().format(DMY) + " – " + r.getToDate().format(DMY) + ")"
        );
    }

    /**
     * Mọi tháng mà đơn chạm tới chưa được duyệt kỳ lương (khóa dòng kỳ nếu có, theo thứ tự tháng, sau khóa hồ sơ và đơn). Như
     * {@code OvertimeService} (R18, R22).
     */
    private void requireOpenPeriods(LeaveRequest r) {
        YearMonth last = YearMonth.from(r.getToDate());
        for (YearMonth m = YearMonth.from(r.getFromDate()); !m.isAfter(last); m = m.plusMonths(1)) {
            String period = m.toString();
            if (periodRepository.findForUpdate(period).filter(p -> p.getStatus() == PayrollPeriodStatus.DA_DUYET).isPresent()) {
                throw rule("Kỳ lương tháng " + period + " đã được duyệt, không thay đổi được đơn nghỉ", "leaveperiodlocked");
            }
        }
    }

    /** Không trùng ngày (theo buổi) với đơn chờ / đã duyệt / chờ hủy khác của cùng người. */
    private void checkOverlap(Long employeeId, LocalDate from, LocalDate to, DayPart first, DayPart last) {
        for (LeaveRequest o : repository.findByEmployeeIdAndStatusIn(employeeId, ACTIVE)) {
            LocalDate a = from.isAfter(o.getFromDate()) ? from : o.getFromDate();
            LocalDate b = to.isBefore(o.getToDate()) ? to : o.getToDate();
            for (LocalDate d = a; !d.isAfter(b); d = d.plusDays(1)) {
                if ((halves(from, to, first, last, d) & halves(o.getFromDate(), o.getToDate(), o.getFirstPart(), o.getLastPart(), d)) != 0) {
                    throw rule(
                        "Trùng ngày " + d.format(DMY) + " với đơn khác (" + o.getFromDate().format(DMY) + " – " + o.getToDate().format(DMY) + ")",
                        "overlap"
                    );
                }
            }
        }
    }

    /** Buổi bị chiếm ngày {@code d}: 1 = sáng, 2 = chiều, 3 = cả ngày. */
    private static int halves(LocalDate from, LocalDate to, DayPart first, DayPart last, LocalDate d) {
        if (from.equals(to)) {
            return mask(first);
        }
        if (d.equals(from)) {
            return first == DayPart.CHIEU ? 2 : 3;
        }
        if (d.equals(to)) {
            return last == DayPart.SANG ? 1 : 3;
        }
        return 3;
    }

    private static int mask(DayPart p) {
        return switch (p) {
            case SANG -> 1;
            case CHIEU -> 2;
            case CA_NGAY -> 3;
        };
    }

    private BigDecimal countDays(LocalDate from, LocalDate to, DayPart first, DayPart last) {
        try {
            return scale(LeaveDays.count(from, to, first, last, calendarService.calendar(from)));
        } catch (IllegalArgumentException e) {
            throw rule(e.getMessage(), "leavepartinvalid");
        }
    }

    private BigDecimal pending(Long employeeId, LeaveRequestType type) {
        return repository.sumAmount(employeeId, type, List.of(LeaveRequestStatus.CHO_DUYET));
    }

    private void apply(LeaveRequest r, Approver a) {
        r.setApproverKind(a.kind());
        r.setApproverEmployee(a.employeeId() == null ? null : employeeRepository.findById(a.employeeId()).orElseThrow());
    }

    /** Khóa hồ sơ người xin (cùng khóa sổ phép) rồi khóa và đọc đơn. */
    private LeaveRequest lockedRequest(Long id) {
        Long employeeId = repository.findEmployeeIdById(id).orElseThrow(LeaveRequestService::notFound);
        ledger.lock(employeeId);
        return repository.findByIdForUpdate(id).orElseThrow(LeaveRequestService::notFound);
    }

    private LeaveRequest find(Long id) {
        return repository.findById(id).orElseThrow(LeaveRequestService::notFound);
    }

    private void requireRequester(LeaveRequest r) {
        if (!ApproverResolver.isSelf(currentLogin(), r.getEmployee())) {
            throw new AccessDeniedException("Chỉ người gửi được làm việc này");
        }
    }

    private void requireDecider(LeaveRequest r) {
        if (!resolver.canDecide(currentLogin(), currentAuthorities(), r)) {
            throw new AccessDeniedException("Bạn không phải người duyệt đơn này");
        }
    }

    private static void requireStatus(LeaveRequest r, LeaveRequestStatus expected) {
        if (r.getStatus() != expected) {
            throw statusError(r);
        }
    }

    private static EmployeeRuleException statusError(LeaveRequest r) {
        return new EmployeeRuleException("Đơn đang ở trạng thái " + r.getStatus() + ", không làm được việc này", "leaverequeststatus");
    }

    private static void requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new EmployeeRuleException("Phải ghi lý do", "leavenoterequired");
        }
    }

    private boolean onBehalf(LeaveRequest r) {
        return !resolver.isDesignated(currentLogin(), currentAuthorities(), r.getApproverKind(), r.getApproverEmployee());
    }

    private void stamp(LeaveRequest r, String note, boolean onBehalf) {
        r.setDecidedBy(currentLogin());
        r.setDecidedAt(clock.instant());
        r.setDecisionNote(trim(note, 500));
        r.setOnBehalf(onBehalf);
    }

    private boolean canSee(LeaveRequest r) {
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN, AuthoritiesConstants.BGD)) {
            return true;
        }
        if (canSeeReason(r)) {
            return true;
        }
        return LeaveSecurity.viaRelation(currentLogin(), r.getEmployee());
    }

    private boolean canSeeReason(LeaveRequest r) {
        String login = currentLogin();
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN)) {
            return true;
        }
        return (
            ApproverResolver.isSelf(login, r.getEmployee()) ||
            (r.getApproverEmployee() != null && ApproverResolver.isLogin(r.getApproverEmployee().getUser(), login)) ||
            resolver.canDecide(login, currentAuthorities(), r)
        );
    }

    private Employee currentEmployee() {
        String login = SecurityUtils.getCurrentUserLogin().orElseThrow(LeaveNoProfileException::new);
        return employeeRepository.findOneByUserLogin(login).orElseThrow(LeaveNoProfileException::new);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static LeaveRequestDTO toDto(LeaveRequest r, boolean withReason) {
        Employee e = r.getEmployee();
        Employee a = r.getApproverEmployee();
        return new LeaveRequestDTO(
            r.getId(),
            e.getId(),
            e.getCode(),
            e.getFullName(),
            r.getType(),
            withReason ? r.getPersonalReason() : null,
            r.getFromDate(),
            r.getToDate(),
            r.getFirstPart(),
            r.getLastPart(),
            r.getDays(),
            r.getHours(),
            withReason ? r.getReasonText() : null,
            r.getStatus(),
            r.getApproverKind(),
            a == null ? null : a.getId(),
            a == null ? null : a.getFullName(),
            r.getDecidedBy(),
            r.getDecidedAt(),
            withReason ? r.getDecisionNote() : null,
            r.isOnBehalf(),
            withReason ? r.getSickDocId() : null,
            r.getSystemNote(),
            r.getCreatedAt(),
            r.getSubmittedBy(),
            null,
            null
        );
    }

    private static BigDecimal sum(List<LeaveLedgerEntry> rows) {
        return rows.stream().map(LeaveLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal v) {
        return scale(v).stripTrailingZeros().toPlainString();
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static EmployeeRuleException rule(String message, String key) {
        return new EmployeeRuleException(message, key);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn");
    }
}
