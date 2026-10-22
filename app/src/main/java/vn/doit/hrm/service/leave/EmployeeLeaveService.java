package vn.doit.hrm.service.leave;

import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.dto.EmployeeLeaveDTO;
import vn.doit.hrm.service.leave.dto.LeaveLedgerEntryDTO;

/**
 * Tab "Nghỉ phép" trên hồ sơ nhân viên (spec 6.3): HR, Quản trị, trưởng nhóm và trưởng phòng của người đó xem được; người xem đã nghỉ
 * việc không còn quyền theo quan hệ (R25). Chính người đó xem ở trang "Nghỉ phép" của mình. Thực tập, dịch vụ → 403 leavenotapplicable.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeLeaveService {

    private final EmployeeRepository employeeRepository;
    private final LeaveLedgerEntryRepository entries;
    private final LeaveBalanceService balances;
    private final LeaveRequestService requests;

    public EmployeeLeaveService(
        EmployeeRepository employeeRepository,
        LeaveLedgerEntryRepository entries,
        LeaveBalanceService balances,
        LeaveRequestService requests
    ) {
        this.employeeRepository = employeeRepository;
        this.entries = entries;
        this.balances = balances;
        this.requests = requests;
    }

    public EmployeeLeaveDTO overview(Long employeeId) {
        // Kiểm tra quyền trước khi báo "không tìm thấy": người không phải HR / Quản trị không dò được id nào tồn tại (403 cho mọi id).
        boolean staff = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR);
        Employee target = employeeRepository.findById(employeeId).orElse(null);
        if (!staff && (target == null || !viaRelation(target))) {
            throw new AccessDeniedException("Không có quyền xem nghỉ phép của người này");
        }
        if (target == null) {
            throw new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound");
        }
        if (target.getPersonType() != PersonType.NHAN_VIEN) {
            throw new LeaveNotApplicableException();
        }
        return new EmployeeLeaveDTO(
            balances.personal(target),
            requests.ofEmployee(target.getId()),
            entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(target.getId()).stream().map(LeaveLedgerEntryDTO::of).toList()
        );
    }

    /** Trưởng nhóm hoặc trưởng phòng (đang làm việc) của người đó. */
    private boolean viaRelation(Employee target) {
        Employee viewer = SecurityUtils.getCurrentUserLogin().flatMap(employeeRepository::findOneByUserLogin).orElse(null);
        if (viewer == null || viewer.getStatus() == EmploymentStatus.DA_NGHI || Objects.equals(viewer.getId(), target.getId())) {
            return false;
        }
        Department d = target.getDepartment();
        boolean lead = target.getManager() != null && Objects.equals(target.getManager().getId(), viewer.getId());
        boolean head = d != null && !Boolean.FALSE.equals(d.getActive()) && d.getHead() != null && Objects.equals(d.getHead().getId(), viewer.getId());
        return lead || head;
    }
}
