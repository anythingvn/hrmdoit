package vn.doit.hrm.service.reminder;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Gửi email nhắc việc dạng văn bản, đồng bộ: lỗi ném ra để người gọi biết địa chỉ nào chưa nhận được. */
@Component
public class ReminderMailer {

    /** Mặc định JavaMail chờ vô hạn: máy chủ SMTP treo sẽ giữ luồng lập lịch mãi (chặn cả các job khác). */
    private static final String TIMEOUT_MS = "10000";

    private final JavaMailSender sender;
    private final String host;
    private final String from;

    public ReminderMailer(JavaMailSender sender, @Value("${spring.mail.host:}") String host, @Value("${jhipster.mail.from}") String from) {
        this.sender = sender;
        this.host = host;
        this.from = from;
        if (sender instanceof JavaMailSenderImpl impl) {
            for (String key : new String[] { "mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout" }) {
                impl.getJavaMailProperties().putIfAbsent(key, TIMEOUT_MS);
            }
        }
    }

    /** Có máy chủ SMTP thật: {@code spring.mail.host} không trống và khác {@code localhost}. */
    public boolean configured() {
        return host != null && !host.isBlank() && !"localhost".equalsIgnoreCase(host.trim());
    }

    public void send(String to, String subject, String text) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(from);
            helper.setSubject(subject);
            helper.setText(text, false);
            sender.send(message);
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
    }
}
