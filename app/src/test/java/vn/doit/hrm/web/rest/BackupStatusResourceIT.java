package vn.doit.hrm.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;

/** Trạng thái backup đọc từ status.json do script backup ghi. */
@IntegrationTest
@AutoConfigureMockMvc
class BackupStatusResourceIT {

    private static final Path STATUS_FILE;

    static {
        try {
            STATUS_FILE = Files.createTempDirectory("backup-status-it").resolve("status.json");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("application.backup.status-file", () -> STATUS_FILE.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(STATUS_FILE);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void noFileMeansNotConfigured() throws Exception {
        mockMvc
            .perform(get("/api/admin/backup-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lastBackupAt").value((Object) null))
            .andExpect(jsonPath("$.offsiteConfigured").value(false));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void returnsFileValues() throws Exception {
        Files.writeString(
            STATUS_FILE,
            "{\"lastBackupAt\": \"2026-10-19T02:00:05+07:00\", \"offsiteConfigured\": true, \"offsiteOk\": false}",
            StandardCharsets.UTF_8
        );
        mockMvc
            .perform(get("/api/admin/backup-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lastBackupAt").value("2026-10-19T02:00:05+07:00"))
            .andExpect(jsonPath("$.offsiteConfigured").value(true))
            .andExpect(jsonPath("$.offsiteOk").value(false));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void corruptFileTreatedAsNoBackup() throws Exception {
        Files.writeString(STATUS_FILE, "{not json", StandardCharsets.UTF_8);
        mockMvc
            .perform(get("/api/admin/backup-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lastBackupAt").value((Object) null))
            .andExpect(jsonPath("$.offsiteConfigured").value(false));
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void hrIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/backup-status")).andExpect(status().isForbidden());
    }
}
