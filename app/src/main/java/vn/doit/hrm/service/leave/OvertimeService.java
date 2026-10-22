package vn.doit.hrm.service.leave;

import static vn.doit.hrm.service.leave.LeaveSecurity.currentAuthorities;
import static vn.doit.hrm.service.leave.LeaveSecurity.currentLogin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.OvertimeLimitException;
import vn.doit.hr.leave.OvertimeRules;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.LeaveSetting;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.PendingTerminationGuard;
import vn.doit.hrm.service.leave.ApproverResolver.Approver;
import vn.doit.hrm.service.leave.dto.OvertimeCreateDTO;
import vn.doit.hrm.service.leave.dto.OvertimeRequestDTO;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Đơn làm thêm giờ và quỹ nghỉ bù (spec 4.2).
 *
 * <ul>
 *   <li>Đăng ký: giờ, hệ số theo lịch của ngày làm (lễ 300, nghỉ hằng tuần 200, ngày làm việc 150), cờ giờ đêm; chặn khi vượt giới
 *       hạn tháng / năm (mặc định 40 / 200 giờ, cài đặt được) tính trên các đơn đã duyệt cùng tháng / năm của ngày làm (giờ thực
 *       tế khi đã hoàn tất). Người duyệt: trưởng nhóm theo chuỗi ({@link ApproverResolver}).</li>
 *   <li>Duyệt bắt buộc chọn trả tiền hay nghỉ bù; duyệt và duyệt phần chênh kiểm tra lại giới hạn.</li>
 *   <li>Xác nhận giờ thực tế sau khi làm: không vượt giờ đăng ký thì hoàn tất; vượt thì người duyệt xác nhận phần chênh (không duyệt
 *       thì giữ giờ đăng ký). Đến ngày tự xác nhận (mặc định 05) của tháng sau, đơn chưa xong được tính bằng giờ đăng ký.</li>
 *   <li>Quỹ nghỉ bù: đơn hoàn tất với hình thức nghỉ bù có giờ thực tế (không nhân hệ số) trong quỹ {@code NGHI_BU}, ngăn
 *       {@code NAM_NAY}. Đổi hình thức được đến khi kỳ lương của tháng ngày làm được duyệt; đổi khỏi nghỉ bù sau khi đã cộng thì ghi
 *       dòng đảo, số dư nghỉ bù không đủ thì chặn (quỹ nghỉ bù không bao giờ âm).</li>
 * </ul>
 *
 * <p>Sổ: mọi dòng của đơn có lý do {@code LAM_THEM} và ref_id = id đơn; lần cộng đầu ref_type {@code OVERTIME} (R1), các lần cộng
 * lại {@code OVERTIME:<n>}, các lần đảo {@code OVERTIME_REVERSAL:<n>}. Số cần có trong quỹ được so với tổng các dòng đó trong khóa
 * hồ sơ của người gửi, rồi chỉ ghi phần chênh: mỗi sự kiện ghi đúng một lần dù gọi lại.
 */
@Service
@Transactional
public class OvertimeService {

    /** Liên kết dòng cộng giờ nghỉ bù của đơn (ref_id = id đơn). */
    public static final String REF_OVERTIME = "OVERTIME";
    /** Liên kết dòng đảo khi đổi khỏi nghỉ bù: {@code OVERTIME_REVERSAL:<n>}, ref_id = id đơn. */
    public static final String REF_OVERTIME_REVERSAL = "OVERTIME_REVERSAL";

    /** Đơn tính vào giới hạn (đã duyệt). */
    static final List<OvertimeStatus> COUNTED = List.of(OvertimeStatus.DA_DUYET, OvertimeStatus.CHO_XAC_NHAN_THEM, OvertimeStatus.HOAN_TAT);
    /** Đơn chiếm khung giờ (không cho đăng ký trùng). */
    static final List<OvertimeStatus> ACTIVE = List.of(
        OvertimeStatus.CHO_DUYET,
        OvertimeStatus.DA_DUYET,
        OvertimeStatus.CHO_XAC_NHAN_THEM,
        OvertimeStatus.HOAN_TAT
    );
    /** Đơn chờ người duyệt. */
    public static final List<OvertimeStatus> INBOX = List.of(OvertimeStatus.CHO_DUYET, OvertimeStatus.CHO_XAC_NHAN_THEM);
    /** Đơn chưa hoàn tất bị hủy khi nghỉ việc nếu ngày làm sau ngày làm việc cuối (R17, R20). */
    static final List<OvertimeStatus> OPEN = List.of(OvertimeStatus.CHO_DUYET, OvertimeStatus.DA_DUYET, OvertimeStatus.CHO_XAC_NHAN_THEM);
    /** Đơn còn chờ xác nhận giờ (tự xác nhận khi quá hạn). */
    static final List<OvertimeStatus> UNCONFIRMED = List.of(OvertimeStatus.DA_DUYET, OvertimeStatus.CHO_XAC_NHAN_THEM);

    private static final BigDecimal MAX_HOURS = BigDecimal.valueOf(24);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OvertimeRequestRepository repository;
    private final EmployeeRepository employeeRepository;
    private final LeaveLedgerService ledger;
    private final LeaveLedgerEntryRepository entries;
    private final LeaveSettingService settings;
    private final WorkCalendarService calendarService;
    private final PendingTerminationGuard terminationGuard;
    private final ApproverResolver resolver;
    private final LeaveNotifier notifier;
    private final PayrollPeriodRepository periodRepository;
    private final Clock clock;

    private final vn.doit.hrm.service.ArchivedEmployeeGuard archivedGuard;

    public OvertimeService(
        OvertimeRequestRepository repository,
        EmployeeRepository employeeRepository,
        LeaveLedgerService ledger,
        LeaveLedgerEntryRepository entries,
        LeaveSettingService settings,
        WorkCalendarService calendarService,
        PendingTerminationGuard terminationGuard,
        ApproverResolver resolver,
        LeaveNotifier notifier,
        PayrollPeriodRepository periodRepository,
        Clock clock,
        vn.doit.hrm.service.ArchivedEmployeeGuard archivedGuard
    ) {
        this.archivedGuard = archivedGuard;
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.ledger = ledger;
        this.entries = entries;
        this.settings = settings;
        this.calendarService = calendarService;
        this.terminationGuard = terminationGuard;
        this.resolver = resolver;
        this.notifier = notifier;
        this.periodRepository = periodRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ đăng ký

    /** Người đăng nhập (loại NHAN_VIEN, đang làm) đăng ký làm thêm giờ. */
    public OvertimeRequestDTO submit(OvertimeCreateDTO dto) {
        Employee me = currentEmployee();
        archivedGuard.requireActive(me);
        if (me.getPersonType() != PersonType.NHAN_VIEN || me.getStatus() == EmploymentStatus.DA_NGHI) {
            throw new LeaveNotApplicableException();
        }
        LocalDate day = dto.workDate();
        LocalTime start = dto.startTime();
        LocalTime end = dto.endTime();
        if (day == null || start == null || end == null || start.equals(end)) {
            throw rule("Giờ bắt đầu và kết thúc không hợp lệ", "overtimetimeinvalid");
        }
        ledger.lock(me.getId());
        int pastDays = settings.current().getPastRequestDays();
        if (day.isBefore(today().minusDays(pastDays))) {
            throw rule("Chỉ được đăng ký làm thêm cho ngày đã qua trong vòng " + pastDays + " ngày", "leavepasttoofar");
        }
        requireOpenPeriod(day);
        terminationGuard.checkStart(me.getId(), day);
        checkOverlap(me.getId(), day, start, end);
        BigDecimal hours = scale(OvertimeRules.hours(start, end));
        checkLimits(me.getId(), day, hours, null);

        Approver approver = resolver.resolve(me, BigDecimal.ZERO, true);
        OvertimeRequest r = new OvertimeRequest();
        r.setEmployee(me);
        r.setWorkDate(day);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setPlannedHours(hours);
        r.setRate(OvertimeRules.rate(day, calendarService.calendar(day), calendarService.isHoliday(day)));
        r.setNight(OvertimeRules.hasNightHours(start, end));
        r.setContent(trim(dto.content(), 1000));
        r.setStatus(OvertimeStatus.CHO_DUYET);
        r.setApproverKind(approver.kind());
        r.setApproverEmployee(approver.employeeId() == null ? null : employeeRepository.findById(approver.employeeId()).orElseThrow());
        r.setCreatedAt(clock.instant());
        r.setSubmittedBy(currentLogin());
        r = repository.save(r);
        notifier.overtimeSubmitted(r);
        return toDto(r);
    }

    // ------------------------------------------------------------------ các bước

    /** Người gửi rút đơn đang chờ duyệt. */
    public OvertimeRequestDTO withdraw(Long id) {
        OvertimeRequest r = lockedRequest(id);
        requireRequester(r);
        requireStatus(r, OvertimeStatus.CHO_DUYET);
        r.setStatus(OvertimeStatus.DA_RUT);
        return toDto(r);
    }

    /** Người duyệt (hoặc HR / Quản trị duyệt thay) duyệt và chọn hình thức (bắt buộc); kiểm tra lại giới hạn. */
    public OvertimeRequestDTO approve(Long id, OvertimeCompensation compensation, String note) {
        OvertimeRequest r = lockedRequest(id);
        archivedGuard.requireActive(r.getEmployee());
        requireDecider(r);
        requireStatus(r, OvertimeStatus.CHO_DUYET);
        requireCompensation(compensation);
        requireOpenPeriod(r.getWorkDate());
        requireCompensationAllowed(r, compensation);
        checkLimits(r.getEmployee().getId(), r.getWorkDate(), r.getPlannedHours(), r.getId());
        r.setCompensation(compensation);
        r.setStatus(OvertimeStatus.DA_DUYET);
        stamp(r, note);
        notifier.overtimeDecided(r);
        return toDto(r);
    }

    /** Từ chối, bắt buộc lý do. */
    public OvertimeRequestDTO reject(Long id, String note) {
        if (note == null || note.isBlank()) {
            throw rule("Phải ghi lý do", "leavenoterequired");
        }
        OvertimeRequest r = lockedRequest(id);
        archivedGuard.requireActive(r.getEmployee());
        requireDecider(r);
        requireStatus(r, OvertimeStatus.CHO_DUYET);
        r.setStatus(OvertimeStatus.TU_CHOI);
        stamp(r, note);
        notifier.overtimeDecided(r);
        return toDto(r);
    }

    /**
     * Người gửi xác nhận giờ thực tế sau khi làm xong: không vượt giờ đăng ký thì hoàn tất (cộng quỹ nếu nghỉ bù); vượt thì chờ người
     * duyệt xác nhận phần chênh.
     */
    public OvertimeRequestDTO confirm(Long id, BigDecimal actualHours) {
        OvertimeRequest r = lockedRequest(id);
        archivedGuard.requireActive(r.getEmployee());
        requireRequester(r);
        requireStatus(r, OvertimeStatus.DA_DUYET);
        requireOpenPeriod(r.getWorkDate());
        if (LocalDateTime.now(clock).isBefore(endsAt(r.getWorkDate(), r.getStartTime(), r.getEndTime()))) {
            throw rule("Chỉ xác nhận giờ thực tế sau khi làm xong", "overtimeconfirmearly");
        }
        BigDecimal actual = actualHours == null ? null : scale(actualHours);
        if (actual == null || actual.signum() <= 0 || actual.compareTo(MAX_HOURS) > 0) {
            throw rule("Số giờ thực tế phải lớn hơn 0 và không quá 24 giờ", "overtimehoursinvalid");
        }
        r.setActualHours(actual);
        r.setConfirmedAt(clock.instant());
        if (actual.compareTo(r.getPlannedHours()) <= 0) {
            complete(r);
        } else {
            r.setStatus(OvertimeStatus.CHO_XAC_NHAN_THEM);
            notifier.overtimeExtraRequested(r);
        }
        return toDto(r);
    }

    /** Người duyệt đồng ý phần giờ vượt đăng ký: hoàn tất với giờ thực tế; kiểm tra lại giới hạn. */
    public OvertimeRequestDTO approveExtra(Long id, String note) {
        OvertimeRequest r = lockedRequest(id);
        archivedGuard.requireActive(r.getEmployee());
        requireDecider(r);
        requireStatus(r, OvertimeStatus.CHO_XAC_NHAN_THEM);
        requireOpenPeriod(r.getWorkDate());
        checkLimits(r.getEmployee().getId(), r.getWorkDate(), r.getActualHours(), r.getId());
        stamp(r, note);
        complete(r);
        notifier.overtimeDecided(r);
        return toDto(r);
    }

    /** Người duyệt không đồng ý phần giờ vượt: hoàn tất với giờ đăng ký. */
    public OvertimeRequestDTO rejectExtra(Long id, String note) {
        OvertimeRequest r = lockedRequest(id);
        archivedGuard.requireActive(r.getEmployee());
        requireDecider(r);
        requireStatus(r, OvertimeStatus.CHO_XAC_NHAN_THEM);
        requireOpenPeriod(r.getWorkDate());
        r.setActualHours(r.getPlannedHours());
        stamp(r, note);
        complete(r);
        notifier.overtimeDecided(r);
        return toDto(r);
    }

    /**
     * Người duyệt hoặc HR / Quản trị đổi trả tiền ↔ nghỉ bù khi kỳ lương của tháng ngày làm chưa được duyệt. Đổi khỏi nghỉ bù sau khi
     * đã cộng thì ghi dòng đảo; số dư nghỉ bù không đủ để đảo → 400 {@code insufficientbalance}.
     */
    public OvertimeRequestDTO changeCompensation(Long id, OvertimeCompensation compensation, String note) {
        OvertimeRequest r = lockedRequest(id);
        archivedGuard.requireActive(r.getEmployee());
        requireDecider(r);
        if (!COUNTED.contains(r.getStatus())) {
            throw statusError(r);
        }
        requireCompensation(compensation);
        requireOpenPeriod(r.getWorkDate());
        if (compensation == r.getCompensation()) {
            return toDto(r);
        }
        requireCompensationAllowed(r, compensation);
        // Ghi sổ trước khi đổi đơn: bị chặn (quỹ không đủ để đảo) thì đơn giữ nguyên.
        syncLedger(r, target(r.getStatus(), compensation, r.getActualHours()));
        r.setCompensation(compensation);
        stamp(r, note);
        return toDto(r);
    }

    // ------------------------------------------------------------------ tự xác nhận

    /**
     * Đơn cần tự xác nhận hôm nay {@code today}: từ ngày tự xác nhận (cài đặt, mặc định 05; tháng ngắn hơn thì ngày cuối tháng) trở
     * đi, các đơn có ngày làm trước tháng này còn chờ xác nhận giờ. Rỗng trước ngày đó.
     */
    @Transactional(readOnly = true)
    public List<Long> autoConfirmCandidates(LocalDate today) {
        int confirmDay = Math.min(Math.max(settings.current().getOtConfirmDay(), 1), today.lengthOfMonth());
        if (today.getDayOfMonth() < confirmDay) {
            return List.of();
        }
        return repository.findIdsByStatusInAndWorkDateBefore(UNCONFIRMED, today.withDayOfMonth(1));
    }

    /**
     * Tự xác nhận một đơn quá hạn (transaction riêng mỗi đơn): giờ thực tế = giờ đăng ký, kể cả khi phần chênh chưa được duyệt.
     * Trả {@code false} nếu đơn đã được xử lý trong lúc chờ.
     */
    public boolean autoConfirm(Long id) {
        OvertimeRequest r = lockedRequest(id);
        if (!UNCONFIRMED.contains(r.getStatus()) || !r.getWorkDate().isBefore(today().withDayOfMonth(1))) {
            return false;
        }
        r.setActualHours(r.getPlannedHours());
        r.setConfirmedAt(clock.instant());
        r.setAutoConfirmed(true);
        complete(r);
        notifier.overtimeDecided(r);
        return true;
    }

    // ------------------------------------------------------------------ nghỉ việc (R17)

    /**
     * Nghỉ việc được áp dụng (transaction của nghỉ việc, hồ sơ đã khóa): đơn chờ duyệt / đã duyệt / chờ xác nhận phần chênh có ngày
     * làm sau ngày làm việc cuối → đã hủy ("Nhân viên nghỉ việc"; R20: giờ đó không thể làm sau khi nghỉ, tự xác nhận sẽ trả giờ chưa
     * làm). Dòng nghỉ bù của đơn (nếu có — chỉ ghi khi hoàn tất, nên thường không có) được đảo. Đơn khác giữ nguyên; khi hoàn tất sau
     * đó, nghỉ bù được chuyển thành trả tiền ({@link #complete}).
     */
    void cancelOnTermination(Long employeeId, LocalDate lastWorkingDay, String note) {
        for (OvertimeRequest r : repository.findByEmployeeIdAndStatusInAndWorkDateAfter(employeeId, OPEN, lastWorkingDay)) {
            r.setStatus(OvertimeStatus.DA_HUY);
            r.setDecidedBy(currentLogin());
            r.setDecidedAt(clock.instant());
            r.setDecisionNote(note);
            r.setOnBehalf(false);
            syncLedger(r);
        }
    }

    /** Chuyển các đơn đang chờ người duyệt {@code approverId} (nghỉ việc, xóa hẳn hồ sơ) lên cấp kế tiếp. */
    public void reassignFromApprover(Long approverId) {
        for (Long id : repository.findIdsByApproverAndStatusIn(approverId, INBOX)) {
            OvertimeRequest r = lockedRequest(id);
            if (INBOX.contains(r.getStatus()) && r.getApproverEmployee() != null && approverId.equals(r.getApproverEmployee().getId())) {
                reassign(r);
            }
        }
    }

    /** Id các đơn đang chờ có người duyệt là một hồ sơ (việc 00:20 xét từng đơn, {@link #reassignOrphan}). */
    @Transactional(readOnly = true)
    public List<Long> orphanCandidates() {
        return repository.findIdsWithApproverEmployee(INBOX);
    }

    /**
     * Việc 00:20, mỗi đơn một transaction (M4): chuyển đơn {@code id} lên cấp kế tiếp nếu người duyệt đã nghỉ việc hoặc bị khóa tài
     * khoản. Trả {@code true} nếu đã chuyển.
     */
    public boolean reassignOrphan(Long id) {
        OvertimeRequest r = lockedRequest(id);
        if (!INBOX.contains(r.getStatus()) || r.getApproverEmployee() == null || resolver.available(r.getApproverEmployee(), r.getEmployee())) {
            return false;
        }
        reassign(r);
        return true;
    }

    private void reassign(OvertimeRequest r) {
        Approver next = resolver.escalate(r.getEmployee(), r.getApproverKind());
        r.setApproverKind(next.kind());
        r.setApproverEmployee(next.employeeId() == null ? null : employeeRepository.findById(next.employeeId()).orElseThrow());
        notifier.overtimeReassigned(r);
    }

    // ------------------------------------------------------------------ xem

    @Transactional(readOnly = true)
    public List<OvertimeRequestDTO> mine() {
        Employee me = currentEmployee();
        if (me.getPersonType() != PersonType.NHAN_VIEN) {
            throw new LeaveNotApplicableException();
        }
        return repository.findByEmployeeIdOrderByWorkDateDescIdDesc(me.getId()).stream().map(OvertimeService::toDto).toList();
    }

    /** Đơn chờ duyệt / chờ xác nhận phần chênh mà người đăng nhập quyết định được. */
    @Transactional(readOnly = true)
    public List<OvertimeRequestDTO> inbox() {
        String login = currentLogin();
        Set<String> auths = currentAuthorities();
        List<OvertimeRequestDTO> out = new ArrayList<>();
        for (OvertimeRequest r : repository.findByStatusInOrderByCreatedAtAscIdAsc(INBOX)) {
            // Hộp duyệt đơn không hiện đơn của hồ sơ đã lưu trữ.
            if (!r.getEmployee().isArchived() && canDecide(login, auths, r)) {
                out.add(toDto(r));
            }
        }
        return out;
    }

    /** Một đơn: người gửi, người duyệt, HR, Quản trị, BGĐ, trưởng nhóm và trưởng phòng của người gửi. */
    @Transactional(readOnly = true)
    public OvertimeRequestDTO get(Long id) {
        OvertimeRequest r = repository.findById(id).orElseThrow(OvertimeService::notFound);
        if (!canSee(r)) {
            throw new AccessDeniedException("Không có quyền xem đơn này");
        }
        return toDto(r);
    }

    // ------------------------------------------------------------------ nội bộ

    /**
     * Đưa số giờ của đơn trong quỹ nghỉ bù về đúng số cần có: giờ thực tế nếu hoàn tất với hình thức nghỉ bù, không thì 0. Người gọi
     * đã khóa hồ sơ. Thiếu thì cộng (ngăn năm nay), thừa thì đảo — lấy ngăn năm trước trước rồi đến năm nay, không cho quỹ âm.
     */
    private void syncLedger(OvertimeRequest r) {
        syncLedger(r, target(r.getStatus(), r.getCompensation(), r.getActualHours()));
    }

    /** Số giờ nghỉ bù đơn phải có trong quỹ: giờ thực tế khi hoàn tất với hình thức nghỉ bù, không thì 0. */
    private static BigDecimal target(OvertimeStatus status, OvertimeCompensation compensation, BigDecimal actualHours) {
        return status == OvertimeStatus.HOAN_TAT && compensation == OvertimeCompensation.NGHI_BU ? scale(actualHours) : BigDecimal.ZERO;
    }

    private void syncLedger(OvertimeRequest r, BigDecimal target) {
        Long emp = r.getEmployee().getId();
        List<LeaveLedgerEntry> rows = entries.findByEmployeeIdAndReasonAndRefTypeStartingWithAndRefId(emp, LedgerReason.LAM_THEM, REF_OVERTIME, r.getId());
        BigDecimal net = rows.stream().map(LeaveLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = target.subtract(net);
        long reversals = rows.stream().map(LeaveLedgerEntry::getRefType).filter(t -> t.startsWith(REF_OVERTIME_REVERSAL)).distinct().count();
        long credits = rows.stream().map(LeaveLedgerEntry::getRefType).filter(t -> !t.startsWith(REF_OVERTIME_REVERSAL)).distinct().count();
        String what = "đơn làm thêm giờ #" + r.getId() + " ngày " + r.getWorkDate().format(DMY);
        if (diff.signum() > 0) {
            String refType = credits == 0 ? REF_OVERTIME : REF_OVERTIME + ":" + (credits + 1);
            ledger.post(emp, Pool.NGHI_BU, List.of(new Entry(Pool.NGHI_BU, Bucket.NAM_NAY, diff)), LedgerReason.LAM_THEM, refType, r.getId(), "Cộng giờ nghỉ bù " + what);
        } else if (diff.signum() < 0) {
            BigDecimal amount = diff.negate();
            // Ghi các dòng chuyển năm / hết hạn còn thiếu trước, để dòng đảo nằm đúng ngăn đang giữ số giờ.
            ledger.settle(emp, today());
            Balance b = ledger.balance(emp, Pool.NGHI_BU);
            if (b.total().compareTo(amount) < 0) {
                throw new LeaveInsufficientBalanceException(
                    "Không đủ giờ nghỉ bù để đảo: còn " + plain(b.total()) + " giờ, cần đảo " + plain(amount) + " giờ",
                    scale(b.total())
                );
            }
            // Lấy ngăn năm trước trước (giờ cộng năm ngoái đã được chuyển sang đó cuối năm), rồi đến năm nay (R23).
            BigDecimal fromPrevious = b.namTruoc().max(BigDecimal.ZERO).min(amount);
            BigDecimal fromCurrent = amount.subtract(fromPrevious);
            List<Entry> out = new ArrayList<>();
            if (fromPrevious.signum() > 0) {
                out.add(new Entry(Pool.NGHI_BU, Bucket.NAM_TRUOC, fromPrevious.negate()));
            }
            if (fromCurrent.signum() > 0) {
                out.add(new Entry(Pool.NGHI_BU, Bucket.NAM_NAY, fromCurrent.negate()));
            }
            ledger.post(emp, Pool.NGHI_BU, out, LedgerReason.LAM_THEM, REF_OVERTIME_REVERSAL + ":" + (reversals + 1), r.getId(), "Đảo giờ nghỉ bù " + what);
        }
    }

    /**
     * Hoàn tất đơn (giờ thực tế đã đặt) và ghi sổ. Người gửi đã nghỉ việc (R17): sổ đã chốt, nên nghỉ bù được chuyển thành trả tiền
     * (trả trong kỳ lương cuối), không cộng dòng {@code LAM_THEM} nào sau khi chốt.
     */
    private void complete(OvertimeRequest r) {
        r.setStatus(OvertimeStatus.HOAN_TAT);
        if (r.getCompensation() == OvertimeCompensation.NGHI_BU && r.getEmployee().getStatus() == EmploymentStatus.DA_NGHI) {
            r.setCompensation(OvertimeCompensation.TRA_TIEN);
        }
        syncLedger(r);
    }

    /**
     * Kỳ lương của tháng ngày làm {@code workDate} chưa được duyệt (khóa dòng kỳ nếu có để không chen với lúc kỳ được duyệt). Dùng khi
     * đăng ký, duyệt, xác nhận, quyết định phần chênh và đổi hình thức (R18, R22); tự xác nhận không kiểm tra.
     */
    private void requireOpenPeriod(LocalDate workDate) {
        String period = YearMonth.from(workDate).toString();
        if (periodRepository.findForUpdate(period).filter(p -> p.getStatus() == PayrollPeriodStatus.DA_DUYET).isPresent()) {
            throw rule("Kỳ lương tháng " + period + " đã được duyệt, không thay đổi được đơn làm thêm giờ", "overtimeperiodlocked");
        }
    }

    /** Người gửi đã nghỉ việc: sổ đã chốt, không chọn nghỉ bù nữa (R17, R23). */
    private static void requireCompensationAllowed(OvertimeRequest r, OvertimeCompensation compensation) {
        if (compensation == OvertimeCompensation.NGHI_BU && r.getEmployee().getStatus() == EmploymentStatus.DA_NGHI) {
            throw rule("Nhân viên đã nghỉ việc, làm thêm giờ chỉ được trả tiền", "overtimeterminated");
        }
    }

    /** Giới hạn tháng / năm của ngày làm, trên các đơn đã duyệt (trừ đơn {@code excludeId}) cộng {@code added} giờ. */
    private void checkLimits(Long employeeId, LocalDate day, BigDecimal added, Long excludeId) {
        LeaveSetting s = settings.current();
        Long exclude = excludeId == null ? -1L : excludeId;
        YearMonth month = YearMonth.from(day);
        BigDecimal monthHours = repository.sumHours(employeeId, COUNTED, month.atDay(1), month.atEndOfMonth(), exclude);
        BigDecimal yearHours = repository.sumHours(employeeId, COUNTED, LocalDate.of(day.getYear(), 1, 1), LocalDate.of(day.getYear(), 12, 31), exclude);
        try {
            OvertimeRules.checkLimits(monthHours, yearHours, added, s.getOtMonthCap(), s.getOtYearCap());
        } catch (OvertimeLimitException e) {
            throw rule(e.getMessage(), "overtimelimit");
        }
    }

    /** Không trùng khung giờ với đơn khác (chờ duyệt trở lên) của cùng người, kể cả đơn qua nửa đêm. */
    private void checkOverlap(Long employeeId, LocalDate day, LocalTime start, LocalTime end) {
        LocalDateTime s = day.atTime(start);
        LocalDateTime e = endsAt(day, start, end);
        for (OvertimeRequest o : repository.findByEmployeeIdAndWorkDateBetweenAndStatusIn(employeeId, day.minusDays(1), day.plusDays(1), ACTIVE)) {
            LocalDateTime os = o.getWorkDate().atTime(o.getStartTime());
            LocalDateTime oe = endsAt(o.getWorkDate(), o.getStartTime(), o.getEndTime());
            if (s.isBefore(oe) && os.isBefore(e)) {
                throw rule(
                    "Trùng giờ với đơn làm thêm khác ngày " + o.getWorkDate().format(DMY) + " (" + o.getStartTime() + " – " + o.getEndTime() + ")",
                    "overtimeoverlap"
                );
            }
        }
    }

    private static LocalDateTime endsAt(LocalDate day, LocalTime start, LocalTime end) {
        return end.isAfter(start) ? day.atTime(end) : day.plusDays(1).atTime(end);
    }

    /** Khóa hồ sơ người gửi (cùng khóa sổ phép) rồi khóa và đọc đơn. */
    private OvertimeRequest lockedRequest(Long id) {
        Long employeeId = repository.findEmployeeIdById(id).orElseThrow(OvertimeService::notFound);
        ledger.lock(employeeId);
        return repository.findByIdForUpdate(id).orElseThrow(OvertimeService::notFound);
    }

    private boolean canDecide(String login, Set<String> auths, OvertimeRequest r) {
        return resolver.canDecide(login, auths, r.getApproverKind(), r.getApproverEmployee(), r.getEmployee());
    }

    private void requireDecider(OvertimeRequest r) {
        if (!canDecide(currentLogin(), currentAuthorities(), r)) {
            throw new AccessDeniedException("Bạn không phải người duyệt đơn này");
        }
    }

    private static void requireRequester(OvertimeRequest r) {
        if (!ApproverResolver.isSelf(currentLogin(), r.getEmployee())) {
            throw new AccessDeniedException("Chỉ người gửi được làm việc này");
        }
    }

    private static void requireStatus(OvertimeRequest r, OvertimeStatus expected) {
        if (r.getStatus() != expected) {
            throw statusError(r);
        }
    }

    private static void requireCompensation(OvertimeCompensation c) {
        if (c == null) {
            throw rule("Chọn hình thức: trả tiền hoặc nghỉ bù", "overtimecompensationrequired");
        }
    }

    private static EmployeeRuleException statusError(OvertimeRequest r) {
        return rule("Đơn đang ở trạng thái " + r.getStatus() + ", không làm được việc này", "overtimestatus");
    }

    private void stamp(OvertimeRequest r, String note) {
        r.setDecidedBy(currentLogin());
        r.setDecidedAt(clock.instant());
        r.setDecisionNote(trim(note, 500));
        r.setOnBehalf(!resolver.isDesignated(currentLogin(), currentAuthorities(), r.getApproverKind(), r.getApproverEmployee()));
    }

    private boolean canSee(OvertimeRequest r) {
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN, AuthoritiesConstants.BGD)) {
            return true;
        }
        String login = currentLogin();
        Employee e = r.getEmployee();
        return (
            ApproverResolver.isSelf(login, e) ||
            (r.getApproverEmployee() != null && ApproverResolver.isLogin(r.getApproverEmployee().getUser(), login)) ||
            canDecide(login, currentAuthorities(), r) ||
            LeaveSecurity.viaRelation(login, e)
        );
    }

    private Employee currentEmployee() {
        String login = SecurityUtils.getCurrentUserLogin().orElseThrow(LeaveNoProfileException::new);
        return employeeRepository.findOneByUserLogin(login).orElseThrow(LeaveNoProfileException::new);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static OvertimeRequestDTO toDto(OvertimeRequest r) {
        Employee e = r.getEmployee();
        Employee a = r.getApproverEmployee();
        return new OvertimeRequestDTO(
            r.getId(),
            e.getId(),
            e.getCode(),
            e.getFullName(),
            r.getWorkDate(),
            r.getStartTime(),
            r.getEndTime(),
            r.getPlannedHours(),
            r.getActualHours(),
            r.getRate(),
            r.isNight(),
            r.getContent(),
            r.getStatus(),
            r.getCompensation(),
            r.getApproverKind(),
            a == null ? null : a.getId(),
            a == null ? null : a.getFullName(),
            r.getDecidedBy(),
            r.getDecidedAt(),
            r.getDecisionNote(),
            r.isOnBehalf(),
            r.getConfirmedAt(),
            r.isAutoConfirmed(),
            r.getCreatedAt(),
            r.getSubmittedBy()
        );
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
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn làm thêm giờ");
    }
}
