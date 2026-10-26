package vn.doit.hrm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountDefaultPasswordTest {

    @Test
    void defaultsToDoit2024() {
        assertThat(new ApplicationProperties().getAccount().getDefaultPassword()).isEqualTo("Doit@2024");
    }

    @Test
    void blankMeansDefault() {
        ApplicationProperties.Account account = new ApplicationProperties().getAccount();
        account.setDefaultPassword("");
        assertThat(account.getDefaultPassword()).isEqualTo("Doit@2024");
        account.setDefaultPassword("   ");
        assertThat(account.getDefaultPassword()).isEqualTo("Doit@2024");
        account.setDefaultPassword(null);
        assertThat(account.getDefaultPassword()).isEqualTo("Doit@2024");
    }

    @Test
    void canBeOverridden() {
        ApplicationProperties.Account account = new ApplicationProperties().getAccount();
        account.setDefaultPassword("CongTy@2027");
        assertThat(account.getDefaultPassword()).isEqualTo("CongTy@2027");
    }

    @Test
    void tooShortIsRejectedAtStartup() {
        ApplicationProperties.Account account = new ApplicationProperties().getAccount();
        assertThatThrownBy(() -> account.setDefaultPassword("1234567")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configuredDefaultPasswordIsNullWhenNotSet() {
        ApplicationProperties.Account account = new ApplicationProperties().getAccount();
        assertThat(account.configuredDefaultPassword()).isNull();
        account.setDefaultPassword("  ");
        assertThat(account.configuredDefaultPassword()).isNull();
        account.setDefaultPassword("CongTy@2027");
        assertThat(account.configuredDefaultPassword()).isEqualTo("CongTy@2027");
    }
}
