package vn.doit.hrm.service.mail;

import vn.doit.hrm.domain.enumeration.MailSecurity;
/**
 * Cấu hình gửi mail đang dùng.
 *
 * @param password mật khẩu đã giải mã (chỉ dùng trong máy chủ, không trả ra API)
 * @param source cấu hình lưu trên giao diện ({@code DATABASE}) hay lấy từ biến môi trường {@code SPRING_MAIL_*} ({@code ENV})
 */
public record MailConfig(
    String host,
    int port,
    MailSecurity security,
    String username,
    String password,
    String fromAddress,
    String fromName,
    Source source
) {
    public enum Source {
        DATABASE,
        ENV,
    }

    /** Có máy chủ SMTP thật: host không trống và khác {@code localhost}. */
    public boolean configured() {
        return host != null && !host.isBlank() && !"localhost".equalsIgnoreCase(host.trim());
    }
}
