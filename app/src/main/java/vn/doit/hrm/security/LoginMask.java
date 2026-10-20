package vn.doit.hrm.security;

/**
 * Tên đăng nhập là số CCCD: chỉ Quản trị/Nhân sự (được xem CCCD) và chính chủ thấy đủ; vai trò khác thấy dạng che
 * {@code ********1234} (4 số cuối).
 */
public final class LoginMask {

    private static final int VISIBLE = 4;

    private LoginMask() {}

    /** Che mọi ký tự trừ 4 ký tự cuối; null giữ null. */
    public static String mask(String login) {
        if (login == null) {
            return null;
        }
        if (login.length() <= VISIBLE) {
            return "*".repeat(VISIBLE);
        }
        return "*".repeat(login.length() - VISIBLE) + login.substring(login.length() - VISIBLE);
    }

    /** Người đang xem được xem số CCCD (Quản trị, Nhân sự). */
    public static boolean viewerSeesIdNumbers() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR);
    }

    /** Tên đăng nhập hiển thị cho người đang xem: đủ nếu được xem CCCD hoặc là chính mình, ngược lại che. */
    public static String forViewer(String login) {
        if (login == null || viewerSeesIdNumbers()) {
            return login;
        }
        boolean self = SecurityUtils.getCurrentUserLogin().map(login::equalsIgnoreCase).orElse(false);
        return self ? login : mask(login);
    }

    /** Như {@link #forViewer} nhưng chỉ che chuỗi toàn chữ số (dạng CCCD), vd người tải tệp lên. */
    public static String forViewerIfDigits(String login) {
        if (login == null || login.isEmpty() || !login.chars().allMatch(Character::isDigit)) {
            return login;
        }
        return forViewer(login);
    }

    /** Dùng trong log: không ghi tên đăng nhập gốc (có thể là CCCD). */
    public static String forLog(String login) {
        return mask(login == null ? "" : login.trim());
    }
}
