package vn.doit.hrm.repository;

import java.time.Instant;

/**
 * Trạng thái đăng nhập tối thiểu đọc ở mỗi request mang JWT.
 *
 * @param activated tài khoản đang mở
 * @param credentialsChangedAt lần đổi mật khẩu/vai trò/khóa-mở gần nhất (null = chưa đổi)
 * @param mustChangePassword đang dùng mật khẩu mặc định (chỉ được xem tài khoản và đổi mật khẩu)
 */
public record UserLoginState(boolean activated, Instant credentialsChangedAt, boolean mustChangePassword) {}
