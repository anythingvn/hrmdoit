package vn.doit.hrm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import vn.doit.hrm.service.mail.DynamicMailSender;
import vn.doit.hrm.service.mail.MailSettingsService;

/** Một bộ gửi mail cho cả ứng dụng, đọc cấu hình đang lưu (giao diện Quản trị hoặc .env) mỗi lần gửi. */
@Configuration
public class MailConfiguration {

    @Bean
    public JavaMailSender javaMailSender(MailSettingsService settings) {
        return new DynamicMailSender(settings);
    }
}
