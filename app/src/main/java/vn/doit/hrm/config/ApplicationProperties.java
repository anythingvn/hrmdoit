package vn.doit.hrm.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Hrm.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final Liquibase liquibase = new Liquibase();

    private final Contract contract = new Contract();

    private final Storage storage = new Storage();

    private final Backup backup = new Backup();

    private final Account account = new Account();

    private final Pdf pdf = new Pdf();

    // jhipster-needle-application-properties-property

    public Liquibase getLiquibase() {
        return liquibase;
    }

    public Contract getContract() {
        return contract;
    }

    public Storage getStorage() {
        return storage;
    }

    public Backup getBackup() {
        return backup;
    }

    public Account getAccount() {
        return account;
    }

    public Pdf getPdf() {
        return pdf;
    }

    // jhipster-needle-application-properties-property-getter

    public static class Account {

        /** Mật khẩu mặc định khi không cấu hình (hoặc biến môi trường để trống). */
        public static final String DEFAULT_PASSWORD = "Doit@2014";

        /** Độ dài tối thiểu, giống ràng buộc mật khẩu ở mọi nơi khác (AdminPasswordDTO). */
        static final int MIN_LENGTH = 8;

        /**
         * Mật khẩu mặc định của tài khoản mới và khi Quản trị "Đặt lại mật khẩu mặc định" (người dùng phải đổi ở lần đăng nhập
         * đầu). Ghi đè bằng {@code APPLICATION_ACCOUNT_DEFAULT_PASSWORD}; để trống = {@value #DEFAULT_PASSWORD}.
         */
        private String defaultPassword = DEFAULT_PASSWORD;

        public String getDefaultPassword() {
            return defaultPassword == null || defaultPassword.isBlank() ? DEFAULT_PASSWORD : defaultPassword;
        }

        public void setDefaultPassword(String defaultPassword) {
            if (defaultPassword != null && !defaultPassword.isBlank() && defaultPassword.length() < MIN_LENGTH) {
                throw new IllegalArgumentException(
                    "application.account.default-password phải có ít nhất " + MIN_LENGTH + " ký tự (hoặc để trống để dùng mặc định)"
                );
            }
            this.defaultPassword = defaultPassword;
        }
    }

    public static class Pdf {

        /** Địa chỉ dịch vụ chuyển PDF (Gotenberg, container {@code pdf} trong compose). */
        private String url = "http://localhost:3000";

        /** Thời gian chờ tối đa một lần chuyển (giây). */
        private int timeoutSeconds = 60;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Backup {

        /** File trạng thái do script backup ghi (volume backup-status, chỉ đọc với app). */
        private String statusFile = "./data/backup-status/status.json";

        public String getStatusFile() {
            return statusFile;
        }

        public void setStatusFile(String statusFile) {
            this.statusFile = statusFile;
        }
    }

    public static class Contract {

        private List<Integer> termOptionsMonths = List.of(12, 24, 36);

        public List<Integer> getTermOptionsMonths() {
            return termOptionsMonths;
        }

        public void setTermOptionsMonths(List<Integer> termOptionsMonths) {
            this.termOptionsMonths = termOptionsMonths;
        }
    }

    public static class Storage {

        /** Thư mục gốc lưu file tải lên (hồ sơ scan, file hợp đồng). */
        private String root = "./data/files";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }

    public static class Liquibase {

        private Boolean asyncStart = true;

        public Boolean getAsyncStart() {
            return asyncStart;
        }

        public void setAsyncStart(Boolean asyncStart) {
            this.asyncStart = asyncStart;
        }
    }

    // jhipster-needle-application-properties-property-class
}
