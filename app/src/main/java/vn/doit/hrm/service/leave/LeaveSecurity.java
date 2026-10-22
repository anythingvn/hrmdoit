package vn.doit.hrm.service.leave;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
}
