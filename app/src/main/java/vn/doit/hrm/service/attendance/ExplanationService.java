package vn.doit.hrm.service.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hrm.domain.AttendanceExplanation;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.AttendancePunchStatus;
import vn.doit.hrm.domain.enumeration.ExplanationStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AttendanceExplanationRepository;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.service.ArchivedEmployeeGuard;
import vn.doit.hrm.service.EmployeeRuleException;

/**
 * Giải trình chấm công (spec 3.7) và danh sách chờ duyệt.
 * <ul>
 *   <li><b>Gửi</b>: chính nhân viên ({@code NHAN_VIEN} chưa lưu trữ, ngày thuộc một lần làm việc), cho ngày đã qua hoặc hôm nay với giờ
 *   đã qua, tháng chưa chốt; giờ vào và/hoặc giờ ra, lý do bắt buộc; mỗi người mỗi ngày tối đa một giải trình {@code DANG_CHO}.</li>
 *   <li><b>Rút</b>: chính người gửi, khi còn {@code DANG_CHO}.</li>
 *   <li><b>Duyệt / từ chối</b>: như {@link PunchReviewService} — người được gõ giờ cho người đó ngày đó ({@link AttendanceAccess#canEnter}),
 *   không tự duyệt, từ chối phải có lý do, tháng đã chốt thì không được. Duyệt sinh lần chấm {@code GIAI_TRINH} cho mỗi giờ đề xuất, giờ
 *   bỏ trống giữ nguyên lần chấm đã có.</li>
 *   <li><b>Danh sách chờ duyệt</b>: lần tự chấm {@code CHO_DUYET} và giải trình {@code DANG_CHO} mà người gọi duyệt được (cùng phạm vi
 *   với {@code canEnter}), bỏ hồ sơ đã lưu trữ và tháng đã khóa; không có tọa độ, IP.</li>
 * </ul>
 */
@Service
@Transactional
public class ExplanationService {

    /** Một giải trình. */
    public record ExplanationDTO(
        Long id,
        LocalDate workDay,
        @JsonFormat(pattern = "HH:mm") LocalTime proposedIn,
        @JsonFormat(pattern = "HH:mm") LocalTime proposedOut,
        String reason,
        String status,
        String decidedBy,
        Instant decidedAt,
        String decisionNote
    ) {}

    /** Một mục chờ duyệt: lần tự chấm ({@code PUNCH}) hoặc giải trình ({@code EXPLANATION}). Không có tọa độ, IP. */
    public record PendingItemDTO(
        String type,
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        LocalDate day,
        String summary,
        String detail
    ) {}

    static final String PUNCH = "PUNCH";
    static final String EXPLANATION = "EXPLANATION";
    private static final int REASON_MAX = 500;

    private final AttendanceExplanationRepository repository;
    private final AttendancePunchRepository punchRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceAccess access;
    private final AttendanceDayLock dayLock;
    private final ArchivedEmployeeGuard archivedGuard;
    private final EmploymentWindow window;
    private final AttendanceNotifier notifier;
    private final EntityManager entityManager;
    private final Clock clock;

    public ExplanationService(
        AttendanceExplanationRepository repository,
        AttendancePunchRepository punchRepository,
        EmployeeRepository employeeRepository,
        AttendanceAccess access,
        AttendanceDayLock dayLock,
        ArchivedEmployeeGuard archivedGuard,
        EmploymentWindow window,
        AttendanceNotifier notifier,
        EntityManager entityManager,
        Clock clock
    ) {
        this.repository = repository;
        this.punchRepository = punchRepository;
        this.employeeRepository = employeeRepository;
        this.access = access;
        this.dayLock = dayLock;
        this.archivedGuard = archivedGuard;
        this.window = window;
        this.notifier = notifier;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ nhân viên

    /**
     * Gửi giải trình. Thứ tự: hồ sơ (404), lưu trữ, ngày, giờ, lý do, áp dụng; rồi khóa hồ sơ, tháng, kỳ lương (tháng đã chốt → 400), kiểm
     * chưa có giải trình đang chờ cho ngày đó, lưu, báo người duyệt.
     */
    public ExplanationDTO submit(String login, LocalDate workDay, LocalTime proposedIn, LocalTime proposedOut, String reason) {
        Employee me = profile(login);
        archivedGuard.requireActive(me);
        if (workDay == null) {
            throw new EmployeeRuleException("Chọn ngày giải trình", "explanationdayrequired");
        }
        ZonedDateTime now = clock.instant().atZone(SelfPunchService.ZONE);
        LocalDate today = now.toLocalDate();
        LocalTime in = proposedIn == null ? null : proposedIn.truncatedTo(ChronoUnit.MINUTES);
        LocalTime out = proposedOut == null ? null : proposedOut.truncatedTo(ChronoUnit.MINUTES);
        LocalTime nowTime = now.toLocalTime();
        if (
            workDay.isAfter(today) ||
            (workDay.equals(today) && ((in != null && in.isAfter(nowTime)) || (out != null && out.isAfter(nowTime))))
        ) {
            throw new EmployeeRuleException("Chỉ giải trình cho ngày đã qua, hoặc hôm nay với giờ đã qua", "explanationfuture");
        }
        if (in == null && out == null) {
            throw new EmployeeRuleException("Nhập giờ vào hoặc giờ ra đề xuất", "explanationtimerequired");
        }
        if (in != null && out != null && !in.isBefore(out)) {
            throw new EmployeeRuleException("Giờ vào phải trước giờ ra", "explanationtimeorder");
        }
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty()) {
            throw new EmployeeRuleException("Cần nhập lý do giải trình", "explanationreasonrequired");
        }
        if (why.length() > REASON_MAX) {
            throw new EmployeeRuleException("Lý do tối đa 500 ký tự", "explanationreasontoolong");
        }
        if (me.getPersonType() != PersonType.NHAN_VIEN || !window.covers(me, workDay)) {
            throw new EmployeeRuleException("Chỉ nhân viên đang làm việc mới tự chấm công", "attendancenotapplicable");
        }

        dayLock.lockOpen(me.getId(), workDay, "không gửi được giải trình", ExplanationService::noProfile);
        if (repository.existsByEmployeeIdAndWorkDayAndStatus(me.getId(), workDay, ExplanationStatus.DANG_CHO)) {
            throw new EmployeeRuleException("Ngày này đã có giải trình đang chờ duyệt; rút giải trình đó trước khi gửi lại", "explanationpending");
        }
        AttendanceExplanation x = new AttendanceExplanation();
        x.setEmployeeId(me.getId());
        x.setWorkDay(workDay);
        x.setProposedIn(in);
        x.setProposedOut(out);
        x.setReason(why);
        x.setStatus(ExplanationStatus.DANG_CHO);
        x.setCreatedAt(clock.instant());
        x = repository.saveAndFlush(x);
        notifier.explanationSubmitted(x, me);
        return toDto(x);
    }

    /** Rút giải trình đang chờ của chính mình. Không có hoặc của người khác: 404 (không dò được id). */
    public ExplanationDTO withdraw(String login, Long id) {
        Employee me = profile(login);
        AttendanceExplanation x = id == null ? null : repository.findById(id).orElse(null);
        if (x == null || !Objects.equals(x.getEmployeeId(), me.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giải trình");
        }
        employeeRepository.findByIdForUpdate(me.getId()).orElseThrow(ExplanationService::noProfile);
        entityManager.refresh(x, LockModeType.PESSIMISTIC_WRITE);
        requirePending(x);
        x.setStatus(ExplanationStatus.DA_RUT);
        x.setDecidedBy(PunchReviewService.cutLogin(login));
        x.setDecidedAt(clock.instant());
        return toDto(repository.saveAndFlush(x));
    }

    /** Giải trình của chính mình trong tháng {@code month}, theo ngày. */
    @Transactional(readOnly = true)
    public List<ExplanationDTO> mine(String login, YearMonth month) {
        Employee me = profile(login);
        return repository
            .findByEmployeeIdAndWorkDayBetweenOrderByWorkDayAscCreatedAtAsc(me.getId(), month.atDay(1), month.atEndOfMonth())
            .stream()
            .map(ExplanationService::toDto)
            .toList();
    }

    // ------------------------------------------------------------------ người duyệt

    /** Đồng ý giải trình: sinh lần chấm {@code GIAI_TRINH} cho mỗi giờ đề xuất. */
    public ExplanationDTO approve(String login, Long id) {
        return decide(login, id, ExplanationStatus.DA_DUYET, null);
    }

    /** Từ chối giải trình, bắt buộc lý do {@code note}. */
    public ExplanationDTO reject(String login, Long id, String note) {
        return decide(login, id, ExplanationStatus.TU_CHOI, note);
    }

    private ExplanationDTO decide(String login, Long id, ExplanationStatus outcome, String note) {
        AttendanceExplanation x = id == null ? null : repository.findById(id).orElse(null);
        Employee target = x == null ? null : employeeRepository.findById(x.getEmployeeId()).orElse(null);
        if (x == null || target == null) {
            // Như duyệt lần chấm: Quản trị/HR thấy lỗi cụ thể; người khác 403, không dò được id ngoài phạm vi.
            if (access.isCompanyWide(login)) {
                throw new EmployeeRuleException("Không tìm thấy giải trình", "idnotfound");
            }
            throw forbidden();
        }
        if (AttendanceAccess.isSelf(login, target)) {
            throw new EmployeeRuleException("Không ai tự duyệt giải trình của chính mình", "explanationselfreview");
        }
        if (!access.canEnter(login, target, x.getWorkDay())) {
            throw forbidden();
        }
        String text = PunchReviewService.decisionNote(outcome == ExplanationStatus.TU_CHOI, note);

        dayLock.lockOpen(target.getId(), x.getWorkDay(), "không duyệt được giải trình", ExplanationService::forbidden);
        // Đọc lại và khóa dòng giải trình sau khi giữ các khóa trên: người duyệt khác hoặc nhân viên (rút) có thể vừa đổi.
        entityManager.refresh(x, LockModeType.PESSIMISTIC_WRITE);
        requirePending(x);
        Instant now = clock.instant();
        String by = PunchReviewService.cutLogin(login);
        if (outcome == ExplanationStatus.DA_DUYET) {
            if (x.getProposedIn() != null) {
                punchRepository.save(punch(x, AttendancePunchKind.VAO, x.getProposedIn(), by, now));
            }
            if (x.getProposedOut() != null) {
                punchRepository.save(punch(x, AttendancePunchKind.RA, x.getProposedOut(), by, now));
            }
        }
        x.setStatus(outcome);
        x.setDecidedBy(by);
        x.setDecidedAt(now);
        x.setDecisionNote(text);
        x = repository.saveAndFlush(x);
        notifier.explanationDecided(x, target);
        return toDto(x);
    }

    /**
     * Lần tự chấm chờ duyệt và giải trình đang chờ mà {@code login} duyệt được: Quản trị/HR cả công ty, trưởng nhóm / trưởng nhóm onsite
     * theo {@link AttendanceAccess#leadScope} (cùng phạm vi với {@code canEnter}). Bỏ hồ sơ đã lưu trữ, chính mình, ngày ngoài lần làm việc
     * và tháng đã khóa (đã chốt hoặc kỳ lương đã duyệt: không duyệt được nữa). Theo ngày, mã nhân viên.
     */
    @Transactional(readOnly = true)
    public List<PendingItemDTO> pending(String login) {
        List<AttendancePunch> punches = punchRepository.findPendingOfActive();
        List<AttendanceExplanation> explanations = repository.findPendingOfActive();
        if (login == null || (punches.isEmpty() && explanations.isEmpty())) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        punches.forEach(p -> ids.add(p.getEmployeeId()));
        explanations.forEach(x -> ids.add(x.getEmployeeId()));
        Map<Long, Employee> people = new HashMap<>();
        employeeRepository.findAllById(ids).forEach(e -> people.put(e.getId(), e));
        Reviewable reviewable = reviewable(login, people);

        List<PendingItemDTO> out = new ArrayList<>();
        for (AttendancePunch p : punches) {
            Employee e = people.get(p.getEmployeeId());
            if (reviewable.test(e, p.getWorkDay())) {
                String summary = (p.getKind() == AttendancePunchKind.VAO ? "Vào " : "Ra ") + AttendanceTimes.hm(p.getPunchTime());
                out.add(item(PUNCH, p.getId(), e, p.getWorkDay(), summary, p.getReviewReason()));
            }
        }
        for (AttendanceExplanation x : explanations) {
            Employee e = people.get(x.getEmployeeId());
            if (reviewable.test(e, x.getWorkDay())) {
                out.add(item(EXPLANATION, x.getId(), e, x.getWorkDay(), AttendanceTimes.inOut(x.getProposedIn(), x.getProposedOut()), x.getReason()));
            }
        }
        out.sort(
            Comparator.comparing(PendingItemDTO::day)
                .thenComparing(PendingItemDTO::employeeCode, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PendingItemDTO::type)
                .thenComparing(PendingItemDTO::id)
        );
        return out;
    }

    private interface Reviewable {
        boolean test(Employee e, LocalDate day);
    }

    /** Ai, ngày nào {@code login} duyệt được, cùng điều kiện với {@link AttendanceAccess#canEnter} nhưng không truy vấn từng mục. */
    private Reviewable reviewable(String login, Map<Long, Employee> people) {
        Set<String> locked = access.lockedMonths();
        if (access.isCompanyWide(login)) {
            EmploymentWindow.Spans spans = window.of(people.values());
            return (e, day) ->
                e != null &&
                !locked.contains(YearMonth.from(day).toString()) &&
                e.getPersonType() == PersonType.NHAN_VIEN &&
                !e.isArchived() &&
                !AttendanceAccess.isSelf(login, e) &&
                spans.covers(e, day);
        }
        Map<YearMonth, AttendanceAccess.LeadScope> scopes = new LinkedHashMap<>();
        return (e, day) -> {
            if (e == null || locked.contains(YearMonth.from(day).toString())) {
                return false;
            }
            return scopes.computeIfAbsent(YearMonth.from(day), m -> access.leadScope(login, m)).days(e.getId()).contains(day);
        };
    }

    // ------------------------------------------------------------------ hỗ trợ

    private static PendingItemDTO item(String type, Long id, Employee e, LocalDate day, String summary, String detail) {
        return new PendingItemDTO(type, id, e.getId(), e.getCode(), e.getFullName(), day, summary, detail);
    }

    private static AttendancePunch punch(AttendanceExplanation x, AttendancePunchKind kind, LocalTime time, String by, Instant now) {
        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(x.getEmployeeId());
        p.setWorkDay(x.getWorkDay());
        p.setPunchTime(time);
        p.setKind(kind);
        p.setSource(AttendancePunchSource.GIAI_TRINH);
        p.setStatus(AttendancePunchStatus.DA_DUYET);
        p.setEnteredBy(by);
        p.setEnteredAt(now);
        p.setDecidedBy(by);
        p.setDecidedAt(now);
        p.setExplanationId(x.getId());
        return p;
    }

    private static void requirePending(AttendanceExplanation x) {
        if (x.getStatus() != ExplanationStatus.DANG_CHO) {
            throw new EmployeeRuleException("Giải trình này không còn chờ duyệt", "explanationnotpending");
        }
    }

    private Employee profile(String login) {
        if (login == null) {
            throw noProfile();
        }
        return employeeRepository.findOneByUserLogin(login).orElseThrow(ExplanationService::noProfile);
    }

    private static ResponseStatusException noProfile() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tài khoản chưa gắn hồ sơ nhân viên");
    }

    private static AccessDeniedException forbidden() {
        return new AccessDeniedException("Không có quyền duyệt giải trình này");
    }

    private static ExplanationDTO toDto(AttendanceExplanation x) {
        return new ExplanationDTO(
            x.getId(),
            x.getWorkDay(),
            x.getProposedIn(),
            x.getProposedOut(),
            x.getReason(),
            x.getStatus().name(),
            x.getDecidedBy(),
            x.getDecidedAt(),
            x.getDecisionNote()
        );
    }
}
