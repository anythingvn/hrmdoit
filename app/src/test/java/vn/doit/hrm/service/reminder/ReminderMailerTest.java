package vn.doit.hrm.service.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

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
}
