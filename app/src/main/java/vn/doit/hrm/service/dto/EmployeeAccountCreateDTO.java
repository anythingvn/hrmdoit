package vn.doit.hrm.service.dto;

import java.util.Set;

/**
 * Tạo tài khoản cho nhân viên: tên đăng nhập là số CCCD của hồ sơ, mật khẩu là mật khẩu mặc định (người dùng phải đổi ở lần
 * đăng nhập đầu). Vai trò bỏ trống = {@code ROLE_NHAN_VIEN}.
 */
public record EmployeeAccountCreateDTO(Set<String> authorities) {}
