package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.domain.CompanySetting;
import vn.doit.hrm.repository.CompanySettingRepository;
import vn.doit.hrm.service.DefaultPasswordService.Source;

/** Mật khẩu mặc định: ưu tiên web, rồi .env, rồi Doit@2024. */
class DefaultPasswordServiceTest {

    private CompanySetting setting;
    private CompanySettingRepository repository;
    private ApplicationProperties properties;
    private DefaultPasswordService service;

    @BeforeEach
    void setUp() {
        setting = new CompanySetting();
        CompanySettingService companySettings = mock(CompanySettingService.class);
        when(companySettings.current()).thenReturn(setting);
        repository = mock(CompanySettingRepository.class);
        properties = new ApplicationProperties();
        service = new DefaultPasswordService(companySettings, repository, properties);
    }

    @Test
    void webWins() {
        setting.setDefaultPassword("Moi@12345");
        properties.getAccount().setDefaultPassword("CongTy@2027");
        assertThat(service.effective()).isEqualTo("Moi@12345");
        assertThat(service.source()).isEqualTo(Source.WEB);
    }

    @Test
    void envWhenWebEmpty() {
        properties.getAccount().setDefaultPassword("CongTy@2027");
        assertThat(service.effective()).isEqualTo("CongTy@2027");
        assertThat(service.source()).isEqualTo(Source.ENV);
    }

    @Test
    void builtInWhenBothEmpty() {
        assertThat(service.effective()).isEqualTo("Doit@2024");
        assertThat(service.source()).isEqualTo(Source.DEFAULT);
        assertThat(service.view().defaultPassword()).isEqualTo("Doit@2024");
    }

    @Test
    void tooShortOrTooLongRejected() {
        assertThatThrownBy(() -> service.update("ngan"))
            .isInstanceOf(EmployeeRuleException.class)
            .hasMessage("Mật khẩu mặc định phải có từ 8 đến 100 ký tự.");
        assertThatThrownBy(() -> service.update("a".repeat(101))).isInstanceOf(EmployeeRuleException.class);
    }

    @Test
    void blankClearsToNext() {
        setting.setDefaultPassword("Moi@12345");
        DefaultPasswordService.View v = service.update(" ");
        assertThat(setting.getDefaultPassword()).isNull();
        assertThat(v.source()).isEqualTo(Source.DEFAULT);
        verify(repository).save(setting);
    }

    @Test
    void updateTrimsAndStores() {
        DefaultPasswordService.View v = service.update("  Moi@12345 ");
        assertThat(setting.getDefaultPassword()).isEqualTo("Moi@12345");
        assertThat(v.source()).isEqualTo(Source.WEB);
    }
}
