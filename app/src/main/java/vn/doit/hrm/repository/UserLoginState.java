package vn.doit.hrm.repository;

import java.time.Instant;

/**
 * Trạng thái đăng nhập tối thiểu đọc ở mỗi request mang JWT.
 *
 * @param activated tài khoản đang mở
 * @param credentialsChangedAt lần đổi mật khẩu/vai trò/khóa-mở gần nhất (null = chưa đổi)
 */
public record UserLoginState(boolean activated, Instant credentialsChangedAt) {}
