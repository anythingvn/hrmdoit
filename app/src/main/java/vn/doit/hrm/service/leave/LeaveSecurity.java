package vn.doit.hrm.service.leave;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.security.SecurityUtils;

/** Người đăng nhập cho các đơn (đơn nghỉ, đơn làm thêm giờ): tên đăng nhập (cắt 50 ký tự, "system" khi chạy tự động) và vai trò. */
final class LeaveSecurity {

    static final String SYSTEM = "system";

    private LeaveSecurity() {}

    static String currentLogin() {
        String login = SecurityUtils.getCurrentUserLogin().orElse(SYSTEM);
        return login.length() > 50 ? login.substring(0, 50) : login;
    }

    static Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    /**
     * {@code login} xem được {@code target} theo quan hệ (R25): là trưởng nhóm ({@code manager}) hoặc trưởng phòng của phòng đang hoạt
     * động của người đó, và chính mình chưa nghỉ việc. So tên đăng nhập không phân biệt hoa thường. Không gồm chính người đó.
     */
    static boolean viaRelation(String login, Employee target) {
        if (login == null || target == null) {
            return false;
        }
        if (relationOf(target.getManager(), target, login)) {
            return true;
        }
        Department d = target.getDepartment();
        return d != null && !Boolean.FALSE.equals(d.getActive()) && relationOf(d.getHead(), target, login);
    }

    private static boolean relationOf(Employee viewer, Employee target, String login) {
        return (
            viewer != null &&
            viewer.getStatus() != EmploymentStatus.DA_NGHI &&
            !java.util.Objects.equals(viewer.getId(), target.getId()) &&
            ApproverResolver.isLogin(viewer.getUser(), login)
        );
    }
}
