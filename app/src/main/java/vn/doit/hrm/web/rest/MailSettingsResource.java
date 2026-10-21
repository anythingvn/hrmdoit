package vn.doit.hrm.web.rest;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.mail.MailSettingsService;
import vn.doit.hrm.service.mail.dto.MailSettingsDTO;
import vn.doit.hrm.service.mail.dto.MailSettingsInput;
import vn.doit.hrm.service.reminder.ReminderRecipients;

/** Cấu hình gửi email (Quản trị): xem, lưu, trở lại cấu hình .env, gửi thư thử. Mật khẩu không bao giờ trả về. */
@RestController
@RequestMapping("/api/admin/mail-settings")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
public class MailSettingsResource {

    private final MailSettingsService settings;
    private final JavaMailSender mailSender;
    private final ReminderRecipients recipients;

    public MailSettingsResource(MailSettingsService settings, JavaMailSender mailSender, ReminderRecipients recipients) {
        this.settings = settings;
        this.mailSender = mailSender;
        this.recipients = recipients;
    }

    @GetMapping("")
    public MailSettingsDTO get() {
        return settings.view();
    }

    @PutMapping("")
    public MailSettingsDTO save(@RequestBody MailSettingsInput body) {
        return settings.save(body);
    }

    @DeleteMapping("")
    public ResponseEntity<Void> reset() {
        settings.reset();
        return ResponseEntity.noContent().build();
    }

    public record TestVM(String to) {}

    /** Gửi thư thử tới {@code to}, mặc định email của Quản trị đang đăng nhập. */
    @PostMapping("/test")
    public Map<String, String> test(@RequestBody(required = false) TestVM body) {
        String to = body != null && body.to() != null && !body.to().isBlank()
            ? body.to().trim()
            : SecurityUtils.getCurrentUserLogin()
                .flatMap(recipients::emailOf)
                .orElseThrow(() -> new EmployeeRuleException("Tài khoản của bạn chưa có email; nhập email nhận thư thử.", "mailtestfailed"));
        settings.testSend(mailSender, to);
        return Map.of("sentTo", to);
    }
}
