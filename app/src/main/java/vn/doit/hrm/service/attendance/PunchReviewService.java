package vn.doit.hrm.service.attendance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.AttendancePunchStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.attendance.AttendanceDayService.DayView;

/**
 * Duyệt / từ chối lần tự chấm đang chờ duyệt (spec 3.7). Người duyệt là người được gõ giờ cho nhân viên đó vào ngày đó
 * ({@link AttendanceAccess#canEnter}: trưởng nhóm, trưởng nhóm onsite đúng ngày onsite, HR, Quản trị). Không ai tự duyệt, kể cả HR; từ
 * chối phải có lý do; tháng đã chốt (hoặc kỳ lương tháng đó đã duyệt) thì không duyệt được.
 *
 * <p>Thứ tự: lần chấm (không có → Quản trị/HR 400 {@code idnotfound}, người khác 403 như ngoài phạm vi), tự duyệt ({@code attendanceselfreview}), quyền (403), lý do từ chối; rồi
 * khóa hồ sơ người được duyệt (cùng khóa với tự chấm), khóa dòng tháng (tạo nếu chưa có) và dòng kỳ lương như lưu giờ gõ tay, kiểm tháng
 * đã chốt / kỳ lương đã duyệt, đọc lại lần chấm và kiểm vẫn {@code CHO_DUYET}, rồi ghi trạng thái, người quyết, thời điểm, ghi chú.
 */
@Service
@Transactional
public class PunchReviewService {

    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final int NOTE_MAX = 500;

    private final AttendancePunchRepository punchRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceMonthRepository monthRepository;
    private final PayrollPeriodRepository periodRepository;
    private final AttendanceAccess access;
    private final AttendanceDayService dayService;
    private final EntityManager entityManager;
    private final Clock clock;

    public PunchReviewService(
        AttendancePunchRepository punchRepository,
        EmployeeRepository employeeRepository,
        AttendanceMonthRepository monthRepository,
        PayrollPeriodRepository periodRepository,
        AttendanceAccess access,
        AttendanceDayService dayService,
        EntityManager entityManager,
        Clock clock
    ) {
        this.entityManager = entityManager;
        this.punchRepository = punchRepository;
        this.employeeRepository = employeeRepository;
        this.monthRepository = monthRepository;
        this.periodRepository = periodRepository;
        this.access = access;
        this.dayService = dayService;
        this.clock = clock;
    }

    /** Duyệt lần chấm {@code punchId}: thành {@code DA_DUYET}, tính công ngay. Trả kết quả ngày đó sau khi duyệt. */
    public DayView approve(String login, Long punchId) {
        return decide(login, punchId, AttendancePunchStatus.DA_DUYET, null);
    }

    /** Từ chối lần chấm {@code punchId} với lý do {@code note} (bắt buộc): thành {@code TU_CHOI}, giữ nguyên dữ liệu. */
    public DayView reject(String login, Long punchId, String note) {
        return decide(login, punchId, AttendancePunchStatus.TU_CHOI, note);
    }

    private DayView decide(String login, Long punchId, AttendancePunchStatus outcome, String note) {
        AttendancePunch found = punchId == null ? null : punchRepository.findById(punchId).orElse(null);
        Employee target = found == null ? null : employeeRepository.findById(found.getEmployeeId()).orElse(null);
        if (found == null || target == null) {
            // Như lưu giờ gõ tay: Quản trị/HR thấy lỗi cụ thể; người khác 403, không dò được id ngoài phạm vi.
            if (access.isCompanyWide(login)) {
                throw new EmployeeRuleException("Không tìm thấy lần chấm", "idnotfound");
            }
            throw forbidden();
        }
        if (AttendanceAccess.isSelf(login, target)) {
            throw new EmployeeRuleException("Không ai tự duyệt lần chấm của chính mình", "attendanceselfreview");
        }
        if (!access.canEnter(login, target, found.getWorkDay())) {
            throw forbidden();
        }
        String text = note == null ? null : note.strip();
        if (outcome == AttendancePunchStatus.TU_CHOI && (text == null || text.isEmpty())) {
            throw new EmployeeRuleException("Cần nhập lý do từ chối", "attendancerejectnoterequired");
        }
        if (text != null && text.length() > NOTE_MAX) {
            throw new EmployeeRuleException("Lý do tối đa 500 ký tự", "attendancedecisionnotetoolong");
        }
        if (text != null && text.isEmpty()) {
            text = null;
        }

        // Khóa hồ sơ người được duyệt (cùng khóa với tự chấm và giải trình), rồi dòng tháng và kỳ lương như lưu giờ gõ tay.
        employeeRepository.findByIdForUpdate(target.getId()).orElseThrow(PunchReviewService::forbidden);
        YearMonth month = YearMonth.from(found.getWorkDay());
        String key = month.toString();
        AttendanceMonth monthRow = monthRepository.lockOrCreate(key);
        if (monthRow.getStatus() == AttendanceMonthStatus.DA_CHOT) {
            throw new EmployeeRuleException(
                "Bảng công tháng " + month.format(MM_YYYY) + " đã chốt, không duyệt được lần chấm",
                "attendancemonthclosed"
            );
        }
        if (periodRepository.findForUpdate(key).filter(p -> p.getStatus() == PayrollPeriodStatus.DA_DUYET).isPresent()) {
            throw new EmployeeRuleException(
                "Kỳ lương tháng " + month.format(MM_YYYY) + " đã duyệt, bảng công tháng này khóa; điều chỉnh ở kỳ sau",
                "attendancepayrollapproved"
            );
        }
        // Đọc lại và khóa dòng lần chấm sau khi giữ các khóa trên: người duyệt khác có thể vừa quyết.
        AttendancePunch p = found;
        entityManager.refresh(p, LockModeType.PESSIMISTIC_WRITE);
        if (p.getSource() != AttendancePunchSource.APP || p.getStatus() != AttendancePunchStatus.CHO_DUYET) {
            throw new EmployeeRuleException("Lần chấm này không còn chờ duyệt", "attendancepunchnotpending");
        }
        p.setStatus(outcome);
        p.setDecidedBy(login.length() > 50 ? login.substring(0, 50) : login);
        p.setDecidedAt(Instant.now(clock));
        p.setDecisionNote(text);
        punchRepository.saveAndFlush(p);
        // Người duyệt ngày đó: được thấy tọa độ / IP.
        return dayService.day(target, p.getWorkDay(), AttendanceDayService.LocationAccess.ALL);
    }

    private static AccessDeniedException forbidden() {
        return new AccessDeniedException("Không có quyền duyệt lần chấm này");
    }
}
