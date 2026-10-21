package vn.doit.hrm.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MailSecretBoxTest {

    @Test
    void encryptsAndDecryptsWithTheSameKey() {
        MailSecretBox box = new MailSecretBox("khoa-bi-mat-rat-dai-0123456789");
        assertThat(box.available()).isTrue();
        String enc = box.encrypt("dl0veai123@");
        assertThat(enc).doesNotContain("dl0veai123");
        assertThat(box.encrypt("dl0veai123@")).isNotEqualTo(enc);
        assertThat(box.decrypt(enc)).isEqualTo("dl0veai123@");
        assertThatThrownBy(() -> new MailSecretBox("khoa-khac-hoan-toan-9876543210").decrypt(enc)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noKeyMeansUnavailable() {
        assertThat(new MailSecretBox("").available()).isFalse();
        assertThat(new MailSecretBox(null).available()).isFalse();
        assertThat(new MailSecretBox("ngan").available()).isFalse();
    }
}
