package vn.doit.hrm.service.mail.dto;

import java.time.Instant;
import vn.doit.hrm.service.mail.MailConfig;
import vn.doit.hrm.domain.enumeration.MailSecurity;

/**
 * Cấu hình email hiển thị cho Quản trị: không bao giờ có mật khẩu, chỉ {@code passwordSet}.
 *
 * @param keyAvailable đã đặt APPLICATION_MAIL_SECRET_KEY (cần để lưu mật khẩu trên giao diện)
 */
public record MailSettingsDTO(
    String host,
    int port,
    MailSecurity security,
    String username,
    boolean passwordSet,
    String fromAddress,
    String fromName,
    MailConfig.Source source,
    boolean configured,
    boolean keyAvailable,
    String updatedBy,
    Instant updatedAt
) {}
