package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.backup.GoogleDriveProbe;
import vn.doit.hrm.service.backup.GoogleDriveProbe.SharedDrive;

/** API cấu hình sao lưu Google Drive: ghi/đọc/xóa file trong thư mục backup-config. */
@IntegrationTest
@AutoConfigureMockMvc
class BackupSettingsResourceIT {

    private static final Path DIR;
    private static final String AGE = "age1e3tlg3rkq2fjze8gzynkhx4dlsl3wn2pk9ahkj8hawx5x253rv0qyktdll";
    private static final String AGE2 = "age1" + "q".repeat(58);
    private static final String LINK = "https://drive.google.com/drive/folders/0AAfBVsNi11ezUk9PVA";

    static {
        try {
            DIR = Files.createTempDirectory("backup-config-it");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("application.backup.config-dir", () -> DIR.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleDriveProbe probe;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() throws IOException {
        reset(probe);
        try (Stream<Path> s = Files.list(DIR)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static String fakeKeyJson(String extra) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String b64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(gen.generateKeyPair().getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
        return (
            "{\"type\":\"service_account\",\"project_id\":\"proj-1\",\"client_email\":\"bk@proj-1.iam.gserviceaccount.com\"," +
            "\"private_key\":\"" +
            pem.replace("\n", "\\n") +
            "\"" +
            extra +
            "}"
        );
    }

    private String body(Object... kv) throws Exception {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return mapper.writeValueAsString(m);
    }

    private void save(String age, boolean keySaved) throws Exception {
        doReturn(new SharedDrive("0AAfBVsNi11ezUk9PVA", "HRM backup")).when(probe).check(any(), anyString());
        mockMvc
            .perform(
                put("/api/admin/backup-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("serviceAccountJson", fakeKeyJson(""), "sharedDrive", LINK, "ageRecipient", age, "keySaved", keySaved))
            )
            .andExpect(status().isOk());
    }

    private long fileCount() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.count();
        }
    }

    @Test
    void requestToStringHidesKey() throws Exception {
        String json = fakeKeyJson("");
        assertThat(new BackupSettingsResource.SaveRequest(json, LINK, AGE, true).toString())
            .doesNotContain("PRIVATE KEY")
            .doesNotContain("private_key");
        assertThat(new BackupSettingsResource.KeyCheckRequest(json).toString()).doesNotContain("PRIVATE KEY");
        assertThat(new BackupSettingsResource.ConnectionRequest(json, LINK).toString()).doesNotContain("PRIVATE KEY");
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void hrIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/backup-settings")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getNoneWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/admin/backup-settings")).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("NONE"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void checkKeyReturnsEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/admin/backup-settings/kiem-tra-khoa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("serviceAccountJson", fakeKeyJson("")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceAccountEmail").value("bk@proj-1.iam.gserviceaccount.com"))
            .andExpect(jsonPath("$.projectId").value("proj-1"));
        mockMvc
            .perform(
                post("/api/admin/backup-settings/kiem-tra-khoa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("serviceAccountJson", "{\"web\":{\"client_id\":\"x\"}}"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("error.backupkeyoauth")));
    }

    @Test
    @WithMockUser(username = "user", authorities = "ROLE_ADMIN")
    void saveWritesThreeFiles() throws Exception {
        save(AGE, true);
        mockMvc
            .perform(get("/api/admin/backup-settings"))
            .andExpect(jsonPath("$.source").value("WEB"))
            .andExpect(jsonPath("$.sharedDriveId").value("0AAfBVsNi11ezUk9PVA"))
            .andExpect(jsonPath("$.sharedDriveName").value("HRM backup"))
            .andExpect(jsonPath("$.serviceAccountEmail").value("bk@proj-1.iam.gserviceaccount.com"))
            .andExpect(jsonPath("$.ageRecipient").value(AGE))
            .andExpect(jsonPath("$.updatedBy").value("user"));
        String rclone = Files.readString(DIR.resolve("rclone.conf"), StandardCharsets.UTF_8);
        assertThat(rclone)
            .contains("team_drive = 0AAfBVsNi11ezUk9PVA")
            .contains("service_account_file = /data/backup-config/service-account.json");
        String offsite = Files.readString(DIR.resolve("offsite.conf"), StandardCharsets.UTF_8);
        assertThat(offsite)
            .contains("BACKUP_OFFSITE_RCLONE=gdrive:HRM-backup")
            .contains("BACKUP_AGE_RECIPIENT=" + AGE)
            .contains("UPDATED_BY=user")
            .contains("SHARED_DRIVE_NAME=HRM backup");
        assertThat(Files.readString(DIR.resolve("service-account.json"))).contains("service_account");
        if (Files.getFileStore(DIR).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(DIR.resolve("service-account.json"))).isEqualTo(
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            );
        }
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getNeverLeaksKey() throws Exception {
        save(AGE, true);
        String res = mockMvc.perform(get("/api/admin/backup-settings")).andReturn().getResponse().getContentAsString();
        assertThat(res).doesNotContain("PRIVATE KEY").doesNotContain("private_key");
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void saveFailsCheckWritesNothing() throws Exception {
        when(probe.check(any(), anyString())).thenThrow(new EmployeeRuleException("Không thấy", "backupdrivenotfound"));
        String req = body("serviceAccountJson", fakeKeyJson(""), "sharedDrive", LINK, "ageRecipient", AGE, "keySaved", true);
        mockMvc
            .perform(put("/api/admin/backup-settings").contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isBadRequest());
        assertThat(fileCount()).isZero();

        save(AGE, true);
        String before1 = Files.readString(DIR.resolve("offsite.conf"));
        String before2 = Files.readString(DIR.resolve("rclone.conf"));
        String before3 = Files.readString(DIR.resolve("service-account.json"));
        when(probe.check(any(), anyString())).thenThrow(new EmployeeRuleException("Không thấy", "backupdrivenotfound"));
        mockMvc
            .perform(put("/api/admin/backup-settings").contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isBadRequest());
        assertThat(Files.readString(DIR.resolve("offsite.conf"))).isEqualTo(before1);
        assertThat(Files.readString(DIR.resolve("rclone.conf"))).isEqualTo(before2);
        assertThat(Files.readString(DIR.resolve("service-account.json"))).isEqualTo(before3);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void editKeepsStoredKey() throws Exception {
        save(AGE, true);
        when(probe.check(any(), anyString())).thenReturn(new SharedDrive("0AAfBVsNi11ezUk9PVA", "HRM backup 2"));
        mockMvc
            .perform(
                put("/api/admin/backup-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("sharedDrive", LINK, "ageRecipient", AGE, "keySaved", false))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sharedDriveName").value("HRM backup 2"))
            .andExpect(jsonPath("$.serviceAccountEmail").value("bk@proj-1.iam.gserviceaccount.com"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void newAgeKeyNeedsConfirmation() throws Exception {
        save(AGE, true);
        when(probe.check(any(), anyString())).thenReturn(new SharedDrive("0AAfBVsNi11ezUk9PVA", "HRM backup"));
        mockMvc
            .perform(
                put("/api/admin/backup-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("sharedDrive", LINK, "ageRecipient", AGE2, "keySaved", false))
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("error.backupkeynotsaved")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void noKeyFromEnvSource() throws Exception {
        when(probe.check(any(), anyString())).thenReturn(new SharedDrive("0AAfBVsNi11ezUk9PVA", "HRM backup"));
        mockMvc
            .perform(
                put("/api/admin/backup-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("sharedDrive", LINK, "ageRecipient", AGE, "keySaved", true))
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("error.backupnokey")));
        assertThat(fileCount()).isZero();
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void deleteRemovesAll() throws Exception {
        save(AGE, true);
        mockMvc.perform(delete("/api/admin/backup-settings")).andExpect(status().isNoContent());
        assertThat(fileCount()).isZero();
        mockMvc.perform(get("/api/admin/backup-settings")).andExpect(jsonPath("$.source").value("NONE"));
    }
}
