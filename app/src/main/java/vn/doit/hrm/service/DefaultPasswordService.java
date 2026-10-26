package vn.doit.hrm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.aop.logging.NoArgumentLogging;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.domain.CompanySetting;
import vn.doit.hrm.repository.CompanySettingRepository;

/**
 * Mật khẩu mặc định của tài khoản mới và của "Đặt lại mật khẩu mặc định". Thứ tự ưu tiên: cấu hình trên web (Cài đặt) →
 * {@code application.account.default-password} ({@code .env}) → {@code Doit@2024}. Đọc từ CSDL mỗi lần gọi (không cache) nên
 * đổi trên web có hiệu lực ngay. Không ghi log giá trị mật khẩu. Đổi chỉ áp dụng cho tài khoản tạo / đặt lại về sau.
 */
@Service
@NoArgumentLogging
@Transactional
public class DefaultPasswordService {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 100;

    public enum Source {
        WEB,
        ENV,
        DEFAULT,
    }

    /** toString không in mật khẩu. */
    public record View(String defaultPassword, Source source) {
        @Override
        public String toString() {
            return "View[source=" + source + "]";
        }
    }

    private final CompanySettingService companySettings;
    private final CompanySettingRepository repository;
    private final ApplicationProperties properties;

    public DefaultPasswordService(
        CompanySettingService companySettings,
        CompanySettingRepository repository,
        ApplicationProperties properties
    ) {
        this.companySettings = companySettings;
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public String effective() {
        return resolve(web(companySettings.current())).defaultPassword();
    }

    @Transactional(readOnly = true)
    public Source source() {
        return view().source();
    }

    @Transactional(readOnly = true)
    public View view() {
        return resolve(web(companySettings.current()));
    }

    /** Lưu mật khẩu mặc định trên web; {@code null} hoặc trống = bỏ, quay về {@code .env} / {@code Doit@2024}. */
    public View update(String defaultPassword) {
        String value = defaultPassword == null || defaultPassword.isBlank() ? null : defaultPassword.trim();
        if (value != null && (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH)) {
            throw new EmployeeRuleException("Mật khẩu mặc định phải có từ 8 đến 100 ký tự.", "defaultpasswordinvalid");
        }
        CompanySetting s = companySettings.current();
        s.setDefaultPassword(value);
        repository.save(s);
        return resolve(value);
    }

    private static String web(CompanySetting s) {
        String v = s.getDefaultPassword();
        return v == null || v.isBlank() ? null : v;
    }

    private View resolve(String web) {
        if (web != null) {
            return new View(web, Source.WEB);
        }
        String env = properties.getAccount().configuredDefaultPassword();
        if (env != null) {
            return new View(env, Source.ENV);
        }
        return new View(ApplicationProperties.Account.DEFAULT_PASSWORD, Source.DEFAULT);
    }
}
