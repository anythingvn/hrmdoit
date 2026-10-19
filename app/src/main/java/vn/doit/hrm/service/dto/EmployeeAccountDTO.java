package vn.doit.hrm.service.dto;

import java.util.Set;

/**
 * Tài khoản đăng nhập gắn với một hồ sơ nhân viên; mọi trường null khi hồ sơ chưa gắn tài khoản.
 */
public record EmployeeAccountDTO(Long userId, String login, Boolean activated, Set<String> authorities) {
    public static final EmployeeAccountDTO NONE = new EmployeeAccountDTO(null, null, null, null);
}
