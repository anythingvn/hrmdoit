package vn.doit.hrm.service.attendance;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.security.AuthoritiesConstants;

/**
 * Ai được gõ giờ cho ai, vào ngày nào (spec 3.4):
 * <ul>
 *   <li>Quản trị, HR: mọi nhân viên;</li>
 *   <li>trưởng nhóm ({@code Employee.manager}): người trong nhóm mình;</li>
 *   <li>trưởng nhóm onsite của khách hàng X: người có phân công {@code KHACH_HANG} ở X còn hiệu lực đúng ngày đó.</li>
 * </ul>
 * Trưởng nhóm (thường hoặc onsite) có hồ sơ đã lưu trữ hoặc đã nghỉ việc thì không còn quyền gì. Không ai gõ cho chính mình. Chỉ
 * chấm công {@code NHAN_VIEN} chưa lưu trữ, trong khoảng từ ngày vào làm đến ngày làm việc cuối.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceAccess {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final WorkAssignmentRepository assignmentRepository;
    private final AttendanceScheduleService scheduleService;

    public AttendanceAccess(
        EmployeeRepository employeeRepository,
        UserRepository userRepository,
        WorkAssignmentRepository assignmentRepository,
        AttendanceScheduleService scheduleService
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.scheduleService = scheduleService;
    }

    /** {@code login} được gõ giờ ngày {@code day} cho {@code target} (không gồm chính mình). */
    public boolean canEnter(String login, Employee target, LocalDate day) {
        if (login == null || target == null || day == null || !attendable(target, day) || isSelf(login, target)) {
            return false;
        }
        if (companyWide(login)) {
            return true;
        }
        Employee me = activeLead(login);
        if (me == null) {
            return false;
        }
        if (target.getManager() != null && Objects.equals(target.getManager().getId(), me.getId())) {
            return true;
        }
        Set<Long> sites = scheduleService.onsiteLeadSiteIds(me.getId());
        return !sites.isEmpty() && onsiteAt(target.getId(), sites, day);
    }

    /** Những người {@code login} được gõ giờ ngày {@code day}, theo mã. */
    public List<Employee> enterableEmployees(String login, LocalDate day) {
        if (login == null || day == null) {
            return List.of();
        }
        Map<Long, Employee> out = new LinkedHashMap<>();
        if (companyWide(login)) {
            employeeRepository.findByPersonTypeAndArchivedAtIsNull(PersonType.NHAN_VIEN).forEach(e -> out.put(e.getId(), e));
        } else {
            Employee me = activeLead(login);
            if (me == null) {
                return List.of();
            }
            employeeRepository.findByManagerIdAndArchivedAtIsNullOrderByCode(me.getId()).forEach(e -> out.put(e.getId(), e));
            Set<Long> sites = scheduleService.onsiteLeadSiteIds(me.getId());
            if (!sites.isEmpty()) {
                assignmentRepository.findOnsiteOn(sites, day).forEach(w -> out.putIfAbsent(w.getEmployee().getId(), w.getEmployee()));
            }
        }
        return out
            .values()
            .stream()
            .filter(e -> attendable(e, day) && !isSelf(login, e))
            .sorted(Comparator.comparing(Employee::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /** {@code login} gõ được cho ít nhất một người vào một ngày nào đó (Quản trị, HR, trưởng nhóm, trưởng nhóm onsite còn làm việc). */
    public boolean entersAnyone(String login) {
        if (login == null) {
            return false;
        }
        if (companyWide(login)) {
            return true;
        }
        Employee me = activeLead(login);
        return (
            me != null &&
            (employeeRepository.existsByManagerIdAndStatusAndArchivedAtIsNull(me.getId(), EmploymentStatus.DANG_LAM_VIEC) ||
                !scheduleService.onsiteLeadSiteIds(me.getId()).isEmpty())
        );
    }

    /** Hồ sơ {@code target} gắn với tài khoản {@code login}. */
    public static boolean isSelf(String login, Employee target) {
        return target != null && target.getUser() != null && login != null && login.equalsIgnoreCase(target.getUser().getLogin());
    }

    /** Người được chấm công ngày {@code day}: NHAN_VIEN, chưa lưu trữ, trong khoảng ngày vào làm – ngày làm việc cuối. */
    static boolean attendable(Employee e, LocalDate day) {
        if (e.getPersonType() != PersonType.NHAN_VIEN || e.isArchived()) {
            return false;
        }
        if (e.getJoinDate() != null && day.isBefore(e.getJoinDate())) {
            return false;
        }
        if (e.getLastWorkingDay() != null) {
            return !day.isAfter(e.getLastWorkingDay());
        }
        return e.getStatus() != EmploymentStatus.DA_NGHI;
    }

    private boolean onsiteAt(Long employeeId, Set<Long> sites, LocalDate day) {
        for (WorkAssignment w : assignmentRepository.findOverlapping(List.of(employeeId), day, day)) {
            if (w.getType() == WorkLocationType.KHACH_HANG && w.getSite() != null && sites.contains(w.getSite().getId())) {
                return true;
            }
        }
        return false;
    }

    /** Hồ sơ của {@code login} khi còn làm việc và chưa lưu trữ (R5); ngược lại null. */
    private Employee activeLead(String login) {
        Employee me = employeeRepository.findOneByUserLogin(login).orElse(null);
        if (me == null || me.isArchived() || me.getStatus() == EmploymentStatus.DA_NGHI) {
            return null;
        }
        return me;
    }

    private boolean companyWide(String login) {
        Set<String> roles = authorities(login);
        return roles.contains(AuthoritiesConstants.ADMIN) || roles.contains(AuthoritiesConstants.HR);
    }

    /** Vai trò của {@code login}: lấy từ phiên hiện tại nếu đúng người đó, không thì đọc từ tài khoản. */
    private Set<String> authorities(String login) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && login.equalsIgnoreCase(auth.getName())) {
            return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        }
        return userRepository
            .findOneWithAuthoritiesByLogin(login)
            .map(u -> u.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet()))
            .orElse(Set.of());
    }
}
