package vn.doit.hrm.service.leave;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.security.AuthoritiesConstants;

/**
 * Ai xem được lịch nghỉ của ai (spec 6.2). Trưởng nhóm và trưởng phòng là quan hệ trong dữ liệu, không phải vai trò: chính mình,
 * người có quản lý trực tiếp là mình, người thuộc phòng có trưởng phòng là mình (hợp các tập). BGĐ, HR, Quản trị thấy tất cả.
 * Chỉ tính người NHAN_VIEN (thực tập, dịch vụ không có sổ phép).
 */
@Service
@Transactional(readOnly = true)
public class LeaveVisibility {

    private final EmployeeRepository employeeRepository;

    public LeaveVisibility(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** Tập hồ sơ người đăng nhập hiện tại được xem. */
    public Set<Long> visibleEmployeeIds() {
        return visibleEmployeeIds(LeaveSecurity.currentLogin(), LeaveSecurity.currentAuthorities());
    }

    public Set<Long> visibleEmployeeIds(String login, Set<String> authorities) {
        if (
            authorities.contains(AuthoritiesConstants.ADMIN) ||
            authorities.contains(AuthoritiesConstants.HR) ||
            authorities.contains(AuthoritiesConstants.BGD)
        ) {
            return new HashSet<>(employeeRepository.findIdsByPersonType(PersonType.NHAN_VIEN));
        }
        Set<Long> ids = new HashSet<>();
        Employee me = employeeRepository.findOneByUserLogin(login).orElse(null);
        if (me == null) {
            return ids;
        }
        if (me.getPersonType() == PersonType.NHAN_VIEN) {
            ids.add(me.getId());
        }
        ids.addAll(employeeRepository.findNhanVienIdsByManagerId(me.getId()));
        ids.addAll(employeeRepository.findNhanVienIdsByDepartmentHeadId(me.getId()));
        return ids;
    }
}
