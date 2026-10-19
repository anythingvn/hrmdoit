package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import vn.doit.hrm.config.Constants;

/**
 * Tạo tài khoản cho nhân viên với mật khẩu ban đầu (đăng nhập được ngay). Vai trò bỏ trống = {@code ROLE_NHAN_VIEN}.
 * {@link #toString()} không bao giờ in mật khẩu.
 */
public record EmployeeAccountCreateDTO(
    @NotBlank @Pattern(regexp = Constants.LOGIN_REGEX) @Size(min = 1, max = 50) String login,
    @NotNull @Size(min = AdminPasswordDTO.MIN_LENGTH, max = AdminPasswordDTO.MAX_LENGTH) String password,
    Set<String> authorities
) {
    @Override
    public String toString() {
        return "EmployeeAccountCreateDTO{login=" + login + ", password=***, authorities=" + authorities + "}";
    }
}
