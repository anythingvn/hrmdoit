package vn.doit.hrm.service.leave;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.attendance.AttendanceAccess;

/**
 * Các cờ trên {@code GET /api/account} để hiện menu: {@code leaveApplicable} (mục "Nghỉ phép": hồ sơ gắn tài khoản là NHAN_VIEN đang
 * làm việc) và {@code isApprover} (mục "Duyệt đơn": BGĐ, HR, Quản trị; hoặc hồ sơ đang làm việc là trưởng nhóm của ai đó, trưởng phòng
 * đang hoạt động, hay đang là người duyệt của một đơn chờ; hoặc là trưởng nhóm onsite / gõ giờ chấm công được cho người khác, để duyệt lần chấm và giải trình). Chỉ dùng để hiện menu; quyền thật kiểm tra ở từng API.
 */
@Service
@Transactional(readOnly = true)
public class LeaveAccountFlags {

    /**
     * @param seesLeaveCalendar mục "Lịch nghỉ": BGĐ, HR, Quản trị; hoặc hồ sơ đang làm việc là trưởng nhóm của ai đó hay trưởng phòng
     *     đang hoạt động (chỉ là người duyệt của một đơn được chuyển lên thì không)
     */
    public record Flags(boolean leaveApplicable, boolean approver, boolean seesLeaveCalendar) {}

    private static final Set<String> APPROVER_ROLES = Set.of(AuthoritiesConstants.BGD, AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN);

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRepository;
    private final OvertimeRequestRepository overtimeRepository;
    private final AttendanceAccess attendanceAccess;

    public LeaveAccountFlags(
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        LeaveRequestRepository leaveRepository,
        OvertimeRequestRepository overtimeRepository,
        AttendanceAccess attendanceAccess
    ) {
        this.attendanceAccess = attendanceAccess;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.leaveRepository = leaveRepository;
        this.overtimeRepository = overtimeRepository;
    }

    public Flags forCurrentUser() {
        Employee me = SecurityUtils.getCurrentUserLogin().flatMap(employeeRepository::findOneByUserLogin).orElse(null);
        boolean working = me != null && me.getStatus() != EmploymentStatus.DA_NGHI;
        boolean leaveApplicable = working && me.getPersonType() == PersonType.NHAN_VIEN;
        if (LeaveSecurity.currentAuthorities().stream().anyMatch(APPROVER_ROLES::contains)) {
            return new Flags(leaveApplicable, true, true);
        }
        if (!working) {
            return new Flags(leaveApplicable, false, false);
        }
        boolean lead = isLeadOrHead(me.getId());
        String login = me.getUser() == null ? null : me.getUser().getLogin();
        // Trưởng nhóm onsite (hoặc ai gõ giờ cho người khác) duyệt lần chấm và giải trình ở "Duyệt đơn".
        boolean attendanceReviewer = attendanceAccess.leadsAnyone(login) || attendanceAccess.entersAnyone(login);
        return new Flags(leaveApplicable, lead || attendanceReviewer || pendingApprover(me.getId()), lead);
    }

    private boolean isLeadOrHead(Long id) {
        return (
            employeeRepository.existsByManagerIdAndStatusAndArchivedAtIsNull(id, EmploymentStatus.DANG_LAM_VIEC) || departmentRepository.existsByHeadIdAndActiveTrue(id)
        );
    }

    private boolean pendingApprover(Long id) {
        return (
            leaveRepository.existsByApproverEmployeeIdAndStatusIn(id, LeaveRequestService.PENDING) ||
            overtimeRepository.existsByApproverEmployeeIdAndStatusIn(id, OvertimeService.INBOX)
        );
    }
}
