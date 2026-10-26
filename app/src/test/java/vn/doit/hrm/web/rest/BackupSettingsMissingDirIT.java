package vn.doit.hrm.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPairGenerator;
import java.util.Base64;
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
import vn.doit.hrm.service.backup.GoogleDriveProbe;
import vn.doit.hrm.service.backup.GoogleDriveProbe.SharedDrive;

/** Thư mục backup-config chưa được mount: báo lỗi rõ ràng. */
@IntegrationTest
@AutoConfigureMockMvc
class BackupSettingsMissingDirIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("application.backup.config-dir", () -> "/nonexistent/backup-config-it");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleDriveProbe probe;

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void missingConfigDir() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String b64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(gen.generateKeyPair().getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
        java.util.Map<String, Object> keyJson = new java.util.LinkedHashMap<>();
        keyJson.put("type", "service_account");
        keyJson.put("client_email", "a@b.iam.gserviceaccount.com");
        keyJson.put("private_key", pem);
        ObjectMapper mapper = new ObjectMapper();
        when(probe.check(any(), anyString())).thenReturn(new SharedDrive("0AAfBVsNi11ezUk9PVA", "HRM backup"));
        java.util.Map<String, Object> reqMap = new java.util.LinkedHashMap<>();
        reqMap.put("serviceAccountJson", mapper.writeValueAsString(keyJson));
        reqMap.put("sharedDrive", "0AAfBVsNi11ezUk9PVA");
        reqMap.put("ageRecipient", "age1e3tlg3rkq2fjze8gzynkhx4dlsl3wn2pk9ahkj8hawx5x253rv0qyktdll");
        reqMap.put("keySaved", true);
        String req = mapper.writeValueAsString(reqMap);
        mockMvc
            .perform(put("/api/admin/backup-settings").contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("error.backupconfigdir")));
    }
}
