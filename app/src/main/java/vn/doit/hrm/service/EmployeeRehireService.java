package vn.doit.hrm.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentPeriod;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentPeriodRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.LoginAttemptService;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.RehireRequestDTO;
import vn.doit.hrm.service.leave.LeaveAccrualService;

/**
 * Nhận làm lại người đã nghỉ (hồ sơ Đã nghỉ, chưa lưu trữ, không có lịch nghỉ việc đang hẹn), trong một transaction:
 * <ul>
 *   <li>ghi một lần làm việc ({@link EmploymentPeriod}) cho lần trước, gắn với lần cho nghỉ việc đã áp dụng tương ứng – từ đây lần
 *       cho nghỉ đó không hoàn tác được nữa;</li>
 *   <li>hồ sơ: trạng thái Đang làm việc, ngày vào làm mới, xóa trắng 4 trường nghỉ việc, giữ mã; phòng ban, vị trí, loại hồ sơ đổi
 *       khi có nhập;</li>
 *   <li>mở lại tài khoản đã gắn, trừ tài khoản {@code admin} hệ thống; tài khoản có quyền Quản trị chỉ được mở lại khi người làm là
 *       Quản trị (như hoàn tác nghỉ việc).</li>
 * </ul>
 * Sổ phép: dòng nghỉ việc đã đưa số dư về 0; việc chốt tháng (chạy ngay sau khi nhận làm lại và hằng ngày) cộng phép theo ngày vào
 * làm mới (mọi tháng từ tháng vào làm mới đến nay, cả quy tắc tháng vào làm, và cộng bù tháng đã cộng rồi bị thu hồi ở lần nghỉ
 * trước), mức thâm niên tính từ ngày đó; các tháng giữa hai lần làm việc không được cộng.
 */
@Service
@Transactional
public class EmployeeRehireService {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeRehireService.class);

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmployeeRepository employeeRepository;

    private final DepartmentRepository departmentRepository;

    private final EmploymentPeriodRepository periodRepository;

    private final TerminationRecordRepository terminationRecordRepository;

    private final UserRepository userRepository;

    private final LoginAttemptService loginAttemptService;

    private final ArchivedEmployeeGuard archivedGuard;

    private final EmployeeService employeeService;

    private final Clock clock;

    private final LeaveAccrualService accrualService;

    public EmployeeRehireService(
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        EmploymentPeriodRepository periodRepository,
        TerminationRecordRepository terminationRecordRepository,
        UserRepository userRepository,
        LoginAttemptService loginAttemptService,
        ArchivedEmployeeGuard archivedGuard,
        EmployeeService employeeService,
        Clock clock,
        LeaveAccrualService accrualService
    ) {
        this.accrualService = accrualService;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.periodRepository = periodRepository;
        this.terminationRecordRepository = terminationRecordRepository;
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
        this.archivedGuard = archivedGuard;
        this.employeeService = employeeService;
        this.clock = clock;
    }

    /** Nhận làm lại; trả hồ sơ sau khi đổi (kèm ghi chú nếu tài khoản không được mở lại). */
    public EmployeeDTO rehire(Long id, RehireRequestDTO request) {
        LOG.debug("Request to rehire Employee {} : {}", id, request);
        Employee employee = employeeRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound"));
        archivedGuard.requireActive(employee);
        if (terminationRecordRepository.existsByEmployeeIdAndStatus(id, TerminationRecordStatus.SCHEDULED)) {
            throw new EmployeeRuleException("Nhân viên đang có lịch nghỉ việc; hủy lịch trước khi nhận làm lại", "terminationscheduled");
        }
        if (employee.getStatus() != EmploymentStatus.DA_NGHI) {
            throw new EmployeeRuleException("Chỉ nhận làm lại được nhân viên đã nghỉ việc", "rehirenotterminated");
        }
        if (request == null || request.joinDate() == null) {
            throw new EmployeeRuleException("Cần ngày vào làm mới", "rehiredaterequired");
        }
        if (employee.getLastWorkingDay() != null && !request.joinDate().isAfter(employee.getLastWorkingDay())) {
            throw new EmployeeRuleException(
                "Ngày vào làm mới phải sau ngày làm việc cuối của lần trước (" + employee.getLastWorkingDay().format(VN_DATE) + ")",
                "rehiredatebeforeleave"
            );
        }
        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository
                .findById(request.departmentId())
                .filter(d -> !Boolean.FALSE.equals(d.getActive()))
                .orElseThrow(() -> new EmployeeRuleException("Phòng ban không tồn tại hoặc đã ngừng hoạt động", "rehiredepartmentinvalid"));
        }
        String position = request.position() == null ? null : request.position().trim();
        if (position != null && position.length() > 100) {
            throw new EmployeeRuleException("Vị trí tối đa 100 ký tự", "rehirepositiontoolong");
        }

        String login = SecurityUtils.getCurrentUserLogin().orElse("system");
        if (employee.getLastWorkingDay() != null) {
            EmploymentPeriod period = new EmploymentPeriod();
            period.setEmployeeId(id);
            period.setJoinDate(employee.getJoinDate());
            period.setLastWorkingDay(employee.getLastWorkingDay());
            period.setTerminationDate(employee.getTerminationDate());
            period.setTerminationType(employee.getTerminationType());
            period.setTerminationReason(employee.getTerminationReason());
            period.setTerminationRecordId(matchingTermination(employee));
            period.setPersonType(employee.getPersonType()); // loại hồ sơ của lần trước, trước khi đổi bên dưới (R27)
            period.setCreatedAt(Instant.now(clock));
            period.setCreatedBy(login.length() > 50 ? login.substring(0, 50) : login);
            periodRepository.save(period);
        }

        employee.setStatus(EmploymentStatus.DANG_LAM_VIEC);
        employee.setJoinDate(request.joinDate());
        employee.setLastWorkingDay(null);
        employee.setTerminationDate(null);
        employee.setTerminationType(null);
        employee.setTerminationReason(null);
        if (department != null) {
            employee.setDepartment(department);
        }
        if (position != null && !position.isEmpty()) {
            employee.setPosition(position);
        }
        if (request.personType() != null) {
            employee.setPersonType(request.personType());
        }
        employeeRepository.save(employee);

        String notice = reopenAccount(employee.getUser());
        employeeRepository.flush();
        // Chốt ngay mọi tháng từ tháng vào làm mới đến tháng hiện tại (kể cả cộng bù tháng đã cộng rồi bị thu hồi ở lần nghỉ trước),
        // không chờ job (R20).
        accrualService.settleFrom(id, YearMonth.from(request.joinDate()), LocalDate.now(clock));
        EmployeeDTO dto = employeeService.findOne(id).orElseThrow();
        dto.setTerminationNotice(notice);
        return dto;
    }

    /** Lần cho nghỉ việc đã áp dụng gần nhất, nếu đúng là lần đã kết thúc lần làm việc này và chưa gắn với lần làm việc nào. */
    private Long matchingTermination(Employee employee) {
        return terminationRecordRepository
            .findFirstByEmployeeIdAndStatusOrderByIdDesc(employee.getId(), TerminationRecordStatus.APPLIED)
            .filter(r -> employee.getLastWorkingDay().equals(r.getLastWorkingDay()))
            .map(TerminationRecord::getId)
            .filter(recordId -> !periodRepository.existsByTerminationRecordId(recordId))
            .orElse(null);
    }

    /** Mở lại tài khoản đã gắn (trừ {@code admin} hệ thống); trả ghi chú khi không mở được. */
    private String reopenAccount(User user) {
        if (user == null || user.isActivated() || IdNumbers.SYSTEM_ADMIN_LOGIN.equals(user.getLogin())) {
            return null;
        }
        boolean adminAccount = user
            .getAuthorities()
            .stream()
            .anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName()));
        if (adminAccount && !SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return "Tài khoản quản trị: cần Quản trị mở lại (tài khoản " + user.getLogin() + " vẫn đang khóa).";
        }
        user.setActivated(true);
        user.markCredentialsChanged();
        userRepository.save(user);
        loginAttemptService.unlock(user.getLogin());
        return null;
    }
}
