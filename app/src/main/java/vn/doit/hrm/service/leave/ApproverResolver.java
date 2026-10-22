package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;

/**
 * Người duyệt đơn (spec 5.1, Global Constraints):
 * <ul>
 *   <li>người xin có vai trò BGĐ → Quản trị;</li>
 *   <li>nghỉ quá ngưỡng (mặc định 3 ngày làm việc) hoặc người xin là trưởng phòng → giám đốc (bất kỳ người BGĐ nào);</li>
 *   <li>còn lại (và làm thêm giờ) → trưởng nhóm ({@code Employee.manager}), không có thì trưởng phòng, không có thì giám đốc;</li>
 *   <li>người duyệt là chính người xin, đã nghỉ việc, hồ sơ đã lưu trữ, hoặc không có tài khoản đang hoạt động (R2) → bỏ qua, lên cấp kế tiếp;</li>
 *   <li>không còn BGĐ nào khác đang hoạt động → Quản trị.</li>
 * </ul>
 * Không ai duyệt đơn của chính mình; HR và Quản trị duyệt thay được mọi đơn khác.
 */
@Service
@Transactional(readOnly = true)
public class ApproverResolver {

    /** Người duyệt: cấp và hồ sơ (null nếu BGĐ hoặc Quản trị — bất kỳ người nào có vai trò đó). */
    public record Approver(ApproverKind kind, Long employeeId) {}

    private static final Approver ADMIN = new Approver(ApproverKind.QUAN_TRI, null);
    private static final Approver DIRECTOR = new Approver(ApproverKind.BGD, null);

    private final LeaveSettingService settings;
    private final DepartmentRepository departments;
    private final UserRepository users;

    public ApproverResolver(LeaveSettingService settings, DepartmentRepository departments, UserRepository users) {
        this.settings = settings;
        this.departments = departments;
        this.users = users;
    }

    /** Người duyệt cho một đơn nghỉ ({@code overtime = false}, {@code workingDays} = số ngày làm việc) hoặc đơn làm thêm giờ. */
    public Approver resolve(Employee requester, BigDecimal workingDays, boolean overtime) {
        if (hasRole(requester.getUser(), AuthoritiesConstants.BGD)) {
            return ADMIN;
        }
        BigDecimal threshold = BigDecimal.valueOf(settings.current().getDirectorThresholdDays());
        if (!overtime && workingDays != null && workingDays.compareTo(threshold) > 0) {
            return director(requester);
        }
        if (requester.getId() != null && departments.existsByHeadIdAndActiveTrue(requester.getId())) {
            return director(requester);
        }
        return from(requester, ApproverKind.TRUONG_NHOM);
    }

    /** Người duyệt kế tiếp sau cấp {@code current} (khi người duyệt hiện tại nghỉ việc hoặc bị khóa tài khoản). */
    public Approver escalate(Employee requester, ApproverKind current) {
        if (hasRole(requester.getUser(), AuthoritiesConstants.BGD)) {
            return ADMIN;
        }
        return switch (current) {
            case TRUONG_NHOM -> from(requester, ApproverKind.TRUONG_PHONG);
            case TRUONG_PHONG, BGD -> director(requester);
            case QUAN_TRI -> ADMIN;
        };
    }

    /** Người duyệt có hồ sơ dùng được: không phải người xin, chưa nghỉ việc, chưa lưu trữ, có tài khoản đang hoạt động (R2). */
    public boolean available(Employee approver, Employee requester) {
        if (approver == null || approver.getStatus() == EmploymentStatus.DA_NGHI || approver.isArchived()) {
            return false;
        }
        if (requester != null && Objects.equals(approver.getId(), requester.getId())) {
            return false;
        }
        User u = approver.getUser();
        if (u == null || !u.isActivated()) {
            return false;
        }
        User ru = requester == null ? null : requester.getUser();
        return ru == null || !(Objects.equals(u.getId(), ru.getId()) || (u.getLogin() != null && u.getLogin().equalsIgnoreCase(ru.getLogin())));
    }

    /**
     * Người đăng nhập {@code login} với các vai trò {@code authorities} được quyết định đơn {@code r}: người duyệt lưu trên đơn, bất
     * kỳ BGĐ nếu cấp BGĐ, Quản trị nếu cấp Quản trị, hoặc HR / Quản trị duyệt thay. Không bao giờ là chính người xin.
     */
    public boolean canDecide(String login, Collection<String> authorities, LeaveRequest r) {
        return canDecide(login, authorities, r.getApproverKind(), r.getApproverEmployee(), r.getEmployee());
    }

    /** Như trên, theo vai trò lưu trong tài khoản. */
    public boolean canDecide(User user, LeaveRequest r) {
        if (user == null || !user.isActivated()) {
            return false;
        }
        return canDecide(user.getLogin(), roles(user), r);
    }

    /** Dạng chung (đơn nghỉ, đơn làm thêm giờ). */
    public boolean canDecide(String login, Collection<String> authorities, ApproverKind kind, Employee approver, Employee requester) {
        if (login == null || isSelf(login, requester)) {
            return false;
        }
        if (authorities.contains(AuthoritiesConstants.HR) || authorities.contains(AuthoritiesConstants.ADMIN)) {
            return true;
        }
        return isDesignated(login, authorities, kind, approver);
    }

    /** Người đăng nhập chính là người duyệt theo đơn (không phải duyệt thay). */
    public boolean isDesignated(String login, Collection<String> authorities, ApproverKind kind, Employee approver) {
        return switch (kind) {
            case BGD -> authorities.contains(AuthoritiesConstants.BGD);
            case QUAN_TRI -> authorities.contains(AuthoritiesConstants.ADMIN);
            case TRUONG_NHOM, TRUONG_PHONG -> approver != null && isLogin(approver.getUser(), login);
        };
    }

    /** {@code login} là tài khoản của người xin. */
    public static boolean isSelf(String login, Employee requester) {
        return requester != null && isLogin(requester.getUser(), login);
    }

    static boolean isLogin(User user, String login) {
        return user != null && login != null && user.getLogin() != null && user.getLogin().equalsIgnoreCase(login);
    }

    private Approver from(Employee requester, ApproverKind start) {
        if (start == ApproverKind.TRUONG_NHOM && available(requester.getManager(), requester)) {
            return new Approver(ApproverKind.TRUONG_NHOM, requester.getManager().getId());
        }
        if (start == ApproverKind.TRUONG_NHOM || start == ApproverKind.TRUONG_PHONG) {
            Department d = requester.getDepartment();
            Employee head = d == null || Boolean.FALSE.equals(d.getActive()) ? null : d.getHead();
            if (available(head, requester)) {
                return new Approver(ApproverKind.TRUONG_PHONG, head.getId());
            }
        }
        return director(requester);
    }

    /** Giám đốc nếu còn người BGĐ đang hoạt động khác người xin; không thì Quản trị. */
    private Approver director(Employee requester) {
        User self = requester.getUser();
        List<User> bgd = users.findActiveWithAnyAuthority(List.of(AuthoritiesConstants.BGD));
        boolean other = bgd.stream().anyMatch(u -> self == null || !Objects.equals(u.getId(), self.getId()));
        return other ? DIRECTOR : ADMIN;
    }

    private static boolean hasRole(User user, String role) {
        return user != null && user.getAuthorities().stream().anyMatch(a -> role.equals(a.getName()));
    }

    private static List<String> roles(User user) {
        return user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toList());
    }
}
