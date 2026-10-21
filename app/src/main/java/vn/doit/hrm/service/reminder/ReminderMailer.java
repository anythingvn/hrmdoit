package vn.doit.hrm.service.reminder;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import vn.doit.hrm.service.mail.MailSettingsService;

/** Gửi email nhắc việc dạng văn bản, đồng bộ: lỗi ném ra để người gọi biết địa chỉ nào chưa nhận được. */
@Component
public class ReminderMailer {

    private final JavaMailSender sender;
    private final MailSettingsService settings;

    public ReminderMailer(JavaMailSender sender, MailSettingsService settings) {
        this.sender = sender;
        this.settings = settings;
    }

    /** Có máy chủ SMTP thật theo cấu hình đang dùng (giao diện Quản trị hoặc .env). */
    public boolean configured() {
        return settings.configured();
    }

    public void send(String to, String subject, String text) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(settings.from());
            helper.setSubject(subject);
            helper.setText(text, false);
            sender.send(message);
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
    }
}
