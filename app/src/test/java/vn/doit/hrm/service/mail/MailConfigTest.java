package vn.doit.hrm.service.mail;

import vn.doit.hrm.domain.enumeration.MailSecurity;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailConfigTest {

    private static MailConfig host(String host) {
        return new MailConfig(host, 587, MailSecurity.STARTTLS, null, null, "hrm@doit.test", null, MailConfig.Source.ENV);
    }

    @Test
    void configuredOnlyWithRealHost() {
        assertThat(host("smtp.doit.vn").configured()).isTrue();
        assertThat(host("localhost").configured()).isFalse();
        assertThat(host(" ").configured()).isFalse();
        assertThat(host(null).configured()).isFalse();
    }
}
