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

    // jhipster-needle-application-properties-property-getter

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

        private Boolean asyncStart;

        public Boolean getAsyncStart() {
            return asyncStart;
        }

        public void setAsyncStart(Boolean asyncStart) {
            this.asyncStart = asyncStart;
        }
    }
    // jhipster-needle-application-properties-property-class
}
