package vn.doit.hrm.service.dto;

import java.util.Set;

/**
 * Tạo tài khoản cho nhân viên: tên đăng nhập là số CCCD của hồ sơ. Mật khẩu ban đầu tùy chọn (8–100 ký tự); để trống = mật
 * khẩu mặc định. Dù thế nào người dùng cũng phải đổi mật khẩu ở lần đăng nhập đầu. Vai trò bỏ trống = {@code ROLE_NHAN_VIEN}.
 * {@link #toString()} không bao giờ in mật khẩu.
 */
public record EmployeeAccountCreateDTO(
    @jakarta.validation.constraints.Pattern(regexp = AdminPasswordDTO.OPTIONAL_PATTERN) String password,
    Set<String> authorities
) {
    /** Mật khẩu ban đầu người tạo nhập; null khi để trống (dùng mật khẩu mặc định). */
    public String initialPassword() {
        return password == null || password.isEmpty() ? null : password;
    }

    @Override
    public String toString() {
        return "EmployeeAccountCreateDTO{password=***, authorities=" + authorities + "}";
    }
}
