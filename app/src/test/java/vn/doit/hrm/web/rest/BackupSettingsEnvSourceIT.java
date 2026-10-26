package vn.doit.hrm.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;

/** Không có offsite.conf nhưng .env đã đặt BACKUP_OFFSITE_RCLONE: GET báo nguồn ENV. */
@IntegrationTest
@AutoConfigureMockMvc
class BackupSettingsEnvSourceIT {

    private static final Path DIR = createDir();

    private static Path createDir() {
        try {
            return Files.createTempDirectory("backup-config-envsrc-it");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("application.backup.config-dir", DIR::toString);
        registry.add("BACKUP_OFFSITE_RCLONE", () -> "gdrive:HRM-backup");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void sourceIsEnv() throws Exception {
        mockMvc.perform(get("/api/admin/backup-settings")).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("ENV"));
    }
}
