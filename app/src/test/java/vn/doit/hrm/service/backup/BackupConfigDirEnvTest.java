package vn.doit.hrm.service.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * application.yml (bản chính, không phải bản test che mất) phải ánh xạ biến môi trường BACKUP_CONFIG_DIR / BACKUP_STATUS_FILE
 * vào application.backup.*; thiếu dòng này thì trong Docker thư mục cấu hình rơi về mặc định.
 */
class BackupConfigDirEnvTest {

    private static String resolve(String key, Map<String, Object> env) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("env", env));
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:config/application.yml")) {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("yml", r);
            for (PropertySource<?> ps : sources) {
                Object raw = ps.getProperty(key);
                if (raw != null) {
                    return environment.resolvePlaceholders(raw.toString());
                }
            }
        }
        throw new IllegalStateException("application.yml không khai báo " + key);
    }

    @Test
    void configDirMapsFromEnvVariable() throws IOException {
        assertThat(resolve("application.backup.config-dir", Map.of("BACKUP_CONFIG_DIR", "/data/backup-config"))).isEqualTo("/data/backup-config");
    }

    @Test
    void statusFileMapsFromEnvVariable() throws IOException {
        assertThat(resolve("application.backup.status-file", Map.of("BACKUP_STATUS_FILE", "/data/s.json"))).isEqualTo("/data/s.json");
    }

    @Test
    void configDirHasDefault() throws IOException {
        assertThat(resolve("application.backup.config-dir", Map.of())).isEqualTo("./data/backup-config");
    }
}
