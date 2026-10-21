package vn.doit.hrm.service.mail.dto;

import vn.doit.hrm.domain.enumeration.MailSecurity;

/**
 * Cấu hình email Quản trị nhập. {@code password} trống = giữ mật khẩu đang lưu; {@code clearPassword} = xóa mật khẩu.
 */
public record MailSettingsInput(
    String host,
    Integer port,
    MailSecurity security,
    String username,
    String password,
    Boolean clearPassword,
    String fromAddress,
    String fromName
) {}
