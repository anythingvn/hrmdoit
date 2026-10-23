package vn.doit.hrm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tài khoản đang dùng mật khẩu mặc định ({@code mustChangePassword}): đăng nhập được nhưng mọi API trừ xem tài khoản
 * ({@code GET /api/account}), đổi mật khẩu ({@code POST /api/account/change-password}) và kiểm tra/đăng nhập
 * ({@code /api/authenticate}) trả 403 {@code error.mustchangepassword} cho tới khi tự đổi mật khẩu. Đăng xuất chỉ xóa token ở
 * trình duyệt nên không cần API.
 * <p>
 * Cờ do {@code SecurityJwtConfiguration} đặt vào JWT đã giải mã (claim {@value #CLAIM}) theo giá trị trong CSDL ở mỗi request.
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    /** Claim chỉ tồn tại trong JWT đã giải mã phía máy chủ, không bao giờ ký vào token phát cho người dùng. */
    public static final String CLAIM = "hrm_must_change_password";

    public static final String MESSAGE = "Bạn cần đổi mật khẩu được cấp để tiếp tục";

    private static final String BODY =
        "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"" +
        MESSAGE +
        "\",\"message\":\"error.mustchangepassword\"}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (mustChangePassword() && isBlocked(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(BODY);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean mustChangePassword() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt && Boolean.TRUE.equals(jwt.getToken().getClaim(CLAIM));
    }

    static boolean isBlocked(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        if (path.equals("/api/account") && HttpMethod.GET.matches(method)) {
            return false;
        }
        if (path.equals("/api/account/change-password") && HttpMethod.POST.matches(method)) {
            return false;
        }
        if (path.equals("/api/authenticate")) {
            return false;
        }
        // Logo, tên công ty: màn đổi mật khẩu cũng cần, và vốn công khai.
        if ((path.equals("/api/public/branding") || path.startsWith("/api/public/branding/")) && HttpMethod.GET.matches(method)) {
            return false;
        }
        if (path.equals("/management/health") || path.startsWith("/management/health/") || path.equals("/management/info")) {
            return false;
        }
        return path.startsWith("/api/") || path.startsWith("/management/") || path.startsWith("/v3/api-docs");
    }
}
