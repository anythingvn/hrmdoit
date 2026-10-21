package vn.doit.hrm.service.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class ReminderMailerTest {

    private static ReminderMailer mailer(String host) {
        return new ReminderMailer(mock(JavaMailSender.class), host, "hrm@doit.test");
    }

    @Test
    void configuredOnlyWithRealHost() {
        assertThat(mailer("smtp.doit.vn").configured()).isTrue();
        assertThat(mailer("localhost").configured()).isFalse();
        assertThat(mailer(" ").configured()).isFalse();
        assertThat(mailer(null).configured()).isFalse();
    }

    @Test
    void smtpTimeoutsSetSoAHungServerCannotBlockTheScheduler() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
        new ReminderMailer(sender, "smtp.doit.vn", "hrm@doit.test");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.connectiontimeout")).isEqualTo("10000");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.writetimeout")).isEqualTo("10000");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.timeout")).isEqualTo("3000");
    }
}
