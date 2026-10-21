package vn.doit.hrm.service.mail;

import vn.doit.hrm.domain.enumeration.MailSecurity;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Properties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;

/**
 * {@link JavaMailSender} dùng chung cho mọi thư (đặt lại mật khẩu, nhắc việc, phiếu lương): đọc cấu hình đang lưu mỗi lần
 * dùng và dựng lại khi cấu hình đổi, nên Quản trị đổi trên giao diện là có hiệu lực ngay, không cần khởi động lại.
 */
public class DynamicMailSender implements JavaMailSender {

    /** Mặc định JavaMail chờ vô hạn: máy chủ SMTP treo sẽ giữ luồng gửi mãi (chặn cả các job khác). */
    static final String TIMEOUT_MS = "10000";

    private final MailSettingsService settings;
    private volatile Built built;

    private record Built(MailConfig config, JavaMailSenderImpl sender) {}

    public DynamicMailSender(MailSettingsService settings) {
        this.settings = settings;
    }

    /** Bộ gửi theo cấu hình hiện tại. */
    public JavaMailSenderImpl delegate() {
        MailConfig config = settings.current();
        Built b = built;
        if (b == null || !b.config().equals(config)) {
            b = new Built(config, build(config));
            built = b;
        }
        return b.sender();
    }

    static JavaMailSenderImpl build(MailConfig c) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(c.host());
        s.setPort(c.port());
        s.setDefaultEncoding("UTF-8");
        boolean auth = c.username() != null && !c.username().isBlank();
        if (auth) {
            s.setUsername(c.username());
            s.setPassword(c.password());
        }
        Properties p = s.getJavaMailProperties();
        p.setProperty("mail.smtp.auth", String.valueOf(auth));
        p.setProperty("mail.smtp.starttls.enable", String.valueOf(c.security() == MailSecurity.STARTTLS));
        p.setProperty("mail.smtp.ssl.enable", String.valueOf(c.security() == MailSecurity.SSL));
        for (String key : new String[] { "mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout" }) {
            p.setProperty(key, TIMEOUT_MS);
        }
        return s;
    }

    @Override
    public MimeMessage createMimeMessage() {
        return delegate().createMimeMessage();
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return delegate().createMimeMessage(contentStream);
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        delegate().send(mimeMessages);
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        delegate().send(mimeMessagePreparators);
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        delegate().send(simpleMessages);
    }
}
