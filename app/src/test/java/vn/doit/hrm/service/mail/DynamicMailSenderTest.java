package vn.doit.hrm.service.mail;

import vn.doit.hrm.domain.enumeration.MailSecurity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Bộ gửi mail đọc cấu hình đang lưu mỗi lần dùng: đổi cấu hình là có hiệu lực ngay; luôn có giới hạn thời gian chờ. */
class DynamicMailSenderTest {

    @Test
    void rebuildsWhenSettingsChangeAndSetsTimeouts() {
        MailSettingsService settings = mock(MailSettingsService.class);
        when(settings.current()).thenReturn(new MailConfig("smtp.a.vn", 587, MailSecurity.STARTTLS, "u@a.vn", "p", "u@a.vn", "A", MailConfig.Source.DATABASE));
        DynamicMailSender sender = new DynamicMailSender(settings);
        JavaMailSenderImpl first = sender.delegate();
        assertThat(first.getHost()).isEqualTo("smtp.a.vn");
        assertThat(first.getPort()).isEqualTo(587);
        assertThat(first.getUsername()).isEqualTo("u@a.vn");
        assertThat(first.getJavaMailProperties().getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(first.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
        assertThat(first.getJavaMailProperties().getProperty("mail.smtp.connectiontimeout")).isEqualTo("10000");
        assertThat(first.getJavaMailProperties().getProperty("mail.smtp.timeout")).isEqualTo("10000");
        assertThat(sender.delegate()).isSameAs(first);

        when(settings.current()).thenReturn(new MailConfig("mail.b.vn", 465, MailSecurity.SSL, null, null, "x@b.vn", null, MailConfig.Source.DATABASE));
        JavaMailSenderImpl second = sender.delegate();
        assertThat(second.getHost()).isEqualTo("mail.b.vn");
        assertThat(second.getJavaMailProperties().getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(second.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("false");
    }
}
