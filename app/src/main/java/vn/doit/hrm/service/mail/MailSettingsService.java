package vn.doit.hrm.service.mail;

import vn.doit.hrm.domain.enumeration.MailSecurity;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.domain.MailSetting;
import vn.doit.hrm.repository.MailSettingRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.mail.dto.MailSettingsDTO;
import vn.doit.hrm.service.mail.dto.MailSettingsInput;

/**
 * Cấu hình gửi email dùng chung cho mọi thư: lưu trên giao diện (Quản trị) thì dùng bản lưu, chưa lưu thì dùng
 * {@code SPRING_MAIL_*} và {@code JHIPSTER_MAIL_FROM} trong .env. Đổi cấu hình có hiệu lực ngay (bộ nhớ đệm được xóa).
 */
@Service
public class MailSettingsService {

    private static final Logger LOG = LoggerFactory.getLogger(MailSettingsService.class);

    private final MailSettingRepository repository;
    private final MailSecretBox secretBox;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MailConfig env;

    private volatile MailConfig cached;

    public MailSettingsService(
        MailSettingRepository repository,
        MailSecretBox secretBox,
        TransactionTemplate tx,
        Clock clock,
        @Value("${spring.mail.host:}") String envHost,
        @Value("${spring.mail.port:25}") int envPort,
        @Value("${spring.mail.username:}") String envUsername,
        @Value("${spring.mail.password:}") String envPassword,
        @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean envStartTls,
        @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") boolean envSsl,
        @Value("${jhipster.mail.from:hrm@localhost}") String envFrom
    ) {
        this.repository = repository;
        this.secretBox = secretBox;
        this.tx = tx;
        this.clock = clock;
        this.env = new MailConfig(
            envHost,
            envPort,
            envSsl ? MailSecurity.SSL : envStartTls ? MailSecurity.STARTTLS : MailSecurity.NONE,
            blankToNull(envUsername),
            blankToNull(envPassword),
            envFrom,
            null,
            MailConfig.Source.ENV
        );
    }

    /** Cấu hình đang dùng (đã giải mã mật khẩu). */
    public MailConfig current() {
        MailConfig c = cached;
        if (c == null) {
            c = tx.execute(s -> repository.findById(MailSetting.ID).map(this::toConfig).orElse(env));
            cached = c;
        }
        return c;
    }

    public boolean configured() {
        return current().configured();
    }

    /** Người gửi: email gửi kèm tên hiển thị (nếu có). */
    public InternetAddress from() {
        MailConfig c = current();
        try {
            InternetAddress a = new InternetAddress(c.fromAddress());
            if (c.fromName() != null && !c.fromName().isBlank()) {
                a.setPersonal(c.fromName().trim(), StandardCharsets.UTF_8.name());
            }
            return a;
        } catch (AddressException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Email gửi không hợp lệ: " + c.fromAddress(), e);
        }
    }

    public MailSettingsDTO view() {
        MailConfig c = current();
        Optional<MailSetting> saved = tx.execute(s -> repository.findById(MailSetting.ID));
        return new MailSettingsDTO(
            c.host(),
            c.port(),
            c.security(),
            c.username(),
            c.password() != null && !c.password().isBlank(),
            c.fromAddress(),
            c.fromName(),
            c.source(),
            c.configured(),
            secretBox.available(),
            saved.map(MailSetting::getUpdatedBy).orElse(null),
            saved.map(MailSetting::getUpdatedAt).orElse(null)
        );
    }

    public MailSettingsDTO save(MailSettingsInput in) {
        String host = in.host() == null ? "" : in.host().trim();
        int port = in.port() == null ? 0 : in.port();
        String fromAddress = in.fromAddress() == null ? "" : in.fromAddress().trim();
        if (host.isEmpty() || port < 1 || port > 65535) {
            throw invalid("Cần máy chủ SMTP và cổng (1–65535).");
        }
        if (!validEmail(fromAddress)) {
            throw invalid("Email gửi không hợp lệ.");
        }
        boolean newPassword = in.password() != null && !in.password().isBlank();
        if (newPassword && !secretBox.available()) {
            throw new EmployeeRuleException(
                "Chưa đặt APPLICATION_MAIL_SECRET_KEY trong .env nên chưa lưu được mật khẩu. Thêm khóa rồi khởi động lại ứng dụng.",
                "mailkeymissing"
            );
        }
        tx.executeWithoutResult(s -> {
            MailSetting m = repository.findById(MailSetting.ID).orElseGet(MailSetting::new);
            m.setHost(host);
            m.setPort(port);
            m.setSecurity(in.security() == null ? MailSecurity.STARTTLS : in.security());
            m.setUsername(blankToNull(in.username()));
            if (Boolean.TRUE.equals(in.clearPassword())) {
                m.setPasswordEnc(null);
            } else if (newPassword) {
                m.setPasswordEnc(secretBox.encrypt(in.password()));
            }
            m.setFromAddress(fromAddress);
            m.setFromName(blankToNull(in.fromName()));
            m.setUpdatedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
            m.setUpdatedAt(Instant.now(clock));
            repository.save(m);
        });
        cached = null;
        return view();
    }

    /** Xóa cấu hình trên giao diện: dùng lại .env. */
    public void reset() {
        tx.executeWithoutResult(s -> repository.findById(MailSetting.ID).ifPresent(repository::delete));
        cached = null;
    }

    /** Gửi một thư thử bằng cấu hình đang dùng; lỗi được diễn giải thành câu dễ hiểu (400 {@code mailtestfailed}). */
    public void testSend(JavaMailSender sender, String to) {
        if (!validEmail(to)) {
            throw new EmployeeRuleException("Cần email nhận thư thử hợp lệ.", "mailtestfailed");
        }
        MailConfig c = current();
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(from());
            helper.setSubject("[HRM] Thư thử cấu hình email");
            helper.setText("Cấu hình gửi email của hệ thống HRM hoạt động.\n\nMáy chủ: " + c.host() + ":" + c.port() + "\n", false);
            sender.send(message);
        } catch (MailException | jakarta.mail.MessagingException e) {
            LOG.warn("Gửi thư thử thất bại: {}", e.getMessage());
            throw new EmployeeRuleException(explain(e, c), "mailtestfailed");
        }
    }

    static String explain(Throwable e, MailConfig c) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException || t instanceof org.springframework.mail.MailAuthenticationException) {
                return "Máy chủ từ chối đăng nhập: sai tài khoản hoặc mật khẩu email.";
            }
            if (t instanceof ConnectException || t instanceof SocketTimeoutException || t instanceof UnknownHostException) {
                return "Không kết nối được máy chủ " + c.host() + ":" + c.port() + " – kiểm tra tên máy chủ, cổng và kiểu bảo mật.";
            }
        }
        return "Không gửi được thư thử: " + e.getMessage();
    }

    private static boolean validEmail(String s) {
        if (s == null || s.isBlank() || !s.contains("@")) {
            return false;
        }
        try {
            new InternetAddress(s.trim(), true);
            return true;
        } catch (AddressException e) {
            return false;
        }
    }

    private MailConfig toConfig(MailSetting m) {
        String password = null;
        if (m.getPasswordEnc() != null) {
            try {
                password = secretBox.decrypt(m.getPasswordEnc());
            } catch (IllegalStateException e) {
                LOG.warn("{}", e.getMessage());
            }
        }
        return new MailConfig(
            m.getHost(),
            m.getPort(),
            m.getSecurity(),
            m.getUsername(),
            password,
            m.getFromAddress(),
            m.getFromName(),
            MailConfig.Source.DATABASE
        );
    }

    private static EmployeeRuleException invalid(String message) {
        return new EmployeeRuleException(message, "mailsettingsinvalid");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
