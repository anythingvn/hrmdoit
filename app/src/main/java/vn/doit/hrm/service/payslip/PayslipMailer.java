package vn.doit.hrm.service.payslip;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import vn.doit.hrm.service.mail.MailSettingsService;

/**
 * Gửi phiếu lương: thư văn bản kèm một tệp PDF, đồng bộ (lỗi ném ra để ghi vào lượt gửi).
 * Dùng chung {@link JavaMailSender} với email nhắc việc (đã đặt giới hạn thời gian chờ).
 */
@Component
public class PayslipMailer {

    private final JavaMailSender sender;
    private final MailSettingsService settings;

    public PayslipMailer(JavaMailSender sender, MailSettingsService settings) {
        this.sender = sender;
        this.settings = settings;
    }

    public void send(String to, String subject, String text, String fileName, byte[] pdf) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(settings.from());
            helper.setSubject(subject);
            helper.setText(text, false);
            helper.addAttachment(fileName, new ByteArrayResource(pdf), "application/pdf");
            sender.send(message);
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
    }
}
