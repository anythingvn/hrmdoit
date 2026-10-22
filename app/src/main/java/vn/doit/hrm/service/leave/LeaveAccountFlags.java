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

/**
 * Hai cờ trên {@code GET /api/account} để hiện menu: {@code leaveApplicable} (mục "Nghỉ phép": hồ sơ gắn tài khoản là NHAN_VIEN đang
 * làm việc) và {@code isApprover} (mục "Duyệt đơn": BGĐ, HR, Quản trị; hoặc hồ sơ đang làm việc là trưởng nhóm của ai đó, trưởng phòng
 * đang hoạt động, hay đang là người duyệt của một đơn chờ). Chỉ dùng để hiện menu; quyền thật kiểm tra ở từng API.
 */
@Service
@Transactional(readOnly = true)
public class LeaveAccountFlags {

    public record Flags(boolean leaveApplicable, boolean approver) {}

    private static final Set<String> APPROVER_ROLES = Set.of(AuthoritiesConstants.BGD, AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN);

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRepository;
    private final OvertimeRequestRepository overtimeRepository;

    public LeaveAccountFlags(
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        LeaveRequestRepository leaveRepository,
        OvertimeRequestRepository overtimeRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.leaveRepository = leaveRepository;
        this.overtimeRepository = overtimeRepository;
    }

    public Flags forCurrentUser() {
        Employee me = SecurityUtils.getCurrentUserLogin().flatMap(employeeRepository::findOneByUserLogin).orElse(null);
        boolean working = me != null && me.getStatus() != EmploymentStatus.DA_NGHI;
        boolean leaveApplicable = working && me.getPersonType() == PersonType.NHAN_VIEN;
        boolean approver =
            LeaveSecurity.currentAuthorities().stream().anyMatch(APPROVER_ROLES::contains) || (working && relationApprover(me.getId()));
        return new Flags(leaveApplicable, approver);
    }

    private boolean relationApprover(Long id) {
        return (
            employeeRepository.existsByManagerIdAndStatus(id, EmploymentStatus.DANG_LAM_VIEC) ||
            departmentRepository.existsByHeadIdAndActiveTrue(id) ||
            leaveRepository.existsByApproverEmployeeIdAndStatusIn(id, LeaveRequestService.PENDING) ||
            overtimeRepository.existsByApproverEmployeeIdAndStatusIn(id, OvertimeService.INBOX)
        );
    }
}
