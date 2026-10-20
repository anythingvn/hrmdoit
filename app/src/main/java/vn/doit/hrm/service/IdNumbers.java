package vn.doit.hrm.service;

import java.util.regex.Pattern;

/**
 * Số CCCD dùng làm tên đăng nhập: bỏ khoảng trắng hai đầu, chỉ gồm chữ số, 12 số (CCCD) hoặc 9 số (CMND cũ).
 */
public final class IdNumbers {

    /** Tài khoản quản trị hệ thống có sẵn: giữ nguyên tên đăng nhập, không gắn/đổi theo CCCD. */
    public static final String SYSTEM_ADMIN_LOGIN = "admin";

    private static final Pattern VALID = Pattern.compile("\\d{12}|\\d{9}");

    private IdNumbers() {}

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    public static String trim(String idNumber) {
        if (idNumber == null) {
            return null;
        }
        String trimmed = idNumber.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Tên đăng nhập tương ứng số CCCD.
     *
     * @throws EmployeeRuleException {@code idnumberrequired} khi trống, {@code idnumberinvalid} khi không phải 12 (hoặc 9) chữ số
     */
    public static String toLogin(String idNumber) {
        String trimmed = trim(idNumber);
        if (trimmed == null) {
            throw new EmployeeRuleException("Nhập số CCCD trong hồ sơ trước khi tạo tài khoản", "idnumberrequired");
        }
        if (!VALID.matcher(trimmed).matches()) {
            throw new EmployeeRuleException("Số CCCD phải gồm 12 chữ số (hoặc 9 chữ số CMND cũ)", "idnumberinvalid");
        }
        return trimmed;
    }
}
