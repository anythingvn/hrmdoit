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

    private final Company company = new Company();

    private final Mail mail = new Mail();

    private final Leave leave = new Leave();

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

    public Mail getMail() {
        return mail;
    }

    public Company getCompany() {
        return company;
    }

    public Leave getLeave() {
        return leave;
    }

    // jhipster-needle-application-properties-property-getter

    public static class Account {

        /** Mật khẩu mặc định khi không cấu hình (hoặc biến môi trường để trống). */
        public static final String DEFAULT_PASSWORD = "Doit@2024";

        /** Độ dài tối thiểu, giống ràng buộc mật khẩu ở mọi nơi khác (AdminPasswordDTO). */
        static final int MIN_LENGTH = 8;

        /**
         * Mật khẩu mặc định của tài khoản mới và khi Quản trị "Đặt lại mật khẩu mặc định" (người dùng phải đổi ở lần đăng nhập
         * đầu). Ghi đè bằng {@code APPLICATION_ACCOUNT_DEFAULT_PASSWORD}; để trống = {@value #DEFAULT_PASSWORD}.
         */
        private String defaultPassword;

        /** Mật khẩu cấu hình bằng {@code .env} / application.yml; {@code null} nếu để trống. */
        public String configuredDefaultPassword() {
            return defaultPassword == null || defaultPassword.isBlank() ? null : defaultPassword;
        }

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

    /**
     * Giá trị ban đầu của cài đặt công ty: chỉ dùng khi tạo dòng {@code company_setting} lần đầu. Sau đó tên công ty và người
     * đại diện đọc qua {@code CompanySettingService} (Quản trị sửa trên trang Cài đặt).
     */
    public static class Company {

        private String name = "CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT";

        /** Người đại diện công ty ký hợp đồng và quyết định; trống thì sinh văn bản báo thiếu. */
        private String representativeName = "";

        private String representativeTitle = "Giám đốc";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRepresentativeName() {
            return representativeName == null ? "" : representativeName;
        }

        public void setRepresentativeName(String representativeName) {
            this.representativeName = representativeName;
        }

        public String getRepresentativeTitle() {
            return representativeTitle == null ? "" : representativeTitle;
        }

        public void setRepresentativeTitle(String representativeTitle) {
            this.representativeTitle = representativeTitle;
        }
    }

    public static class Mail {

        /**
         * Khóa mã hóa mật khẩu hộp thư lưu trên giao diện Quản trị ({@code APPLICATION_MAIL_SECRET_KEY}, ít nhất 16 ký tự).
         * Trống: không lưu được mật khẩu trên giao diện. Đổi khóa: phải nhập lại mật khẩu email.
         */
        private String secretKey = "";

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class Leave {

        /** Khởi động xong thì chạy bù việc đầu tháng của sổ phép nếu tháng này chưa chạy. */
        private boolean catchUpOnStartup = true;

        public boolean isCatchUpOnStartup() {
            return catchUpOnStartup;
        }

        public void setCatchUpOnStartup(boolean catchUpOnStartup) {
            this.catchUpOnStartup = catchUpOnStartup;
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

        /** Thư mục cấu hình sao lưu do web ghi (volume backup-config). */
        private String configDir = "./data/backup-config";

        /** Địa chỉ lấy access token Google (luôn dùng giá trị này, bỏ qua token_uri trong file khóa). */
        private String googleTokenUrl = "https://oauth2.googleapis.com/token";

        /** Địa chỉ gốc Google Drive API v3. */
        private String googleDriveUrl = "https://www.googleapis.com/drive/v3";

        public String getConfigDir() {
            return configDir;
        }

        public void setConfigDir(String configDir) {
            this.configDir = configDir;
        }

        public String getGoogleTokenUrl() {
            return googleTokenUrl;
        }

        public void setGoogleTokenUrl(String googleTokenUrl) {
            this.googleTokenUrl = googleTokenUrl;
        }

        public String getGoogleDriveUrl() {
            return googleDriveUrl;
        }

        public void setGoogleDriveUrl(String googleDriveUrl) {
            this.googleDriveUrl = googleDriveUrl;
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
