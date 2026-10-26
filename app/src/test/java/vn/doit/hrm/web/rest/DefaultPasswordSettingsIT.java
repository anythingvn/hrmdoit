package vn.doit.hrm.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.DefaultPasswordService;

/** Mật khẩu mặc định cấu hình trên web: quyền, nguồn, áp dụng ngay cho tài khoản mới và đặt lại. */
@IntegrationTest
@AutoConfigureMockMvc
class DefaultPasswordSettingsIT {

    private static final String LOGIN = "079100000777";

    private static final String URL = "/api/admin/account-settings/default-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DefaultPasswordService defaultPasswords;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void reset() {
        defaultPasswords.update(null);
        userRepository.findOneByLogin(LOGIN).ifPresent(userRepository::delete);
    }

    /** Mỗi request gắn lại Quản trị: /api/authenticate đổi SecurityContext của luồng test sang người vừa đăng nhập. */
    private static final RequestPostProcessor ADMIN = SecurityMockMvcRequestPostProcessors.user("admin").authorities(
        new SimpleGrantedAuthority("ROLE_ADMIN")
    );

    private void putPassword(String json) throws Exception {
        mockMvc.perform(put(URL).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void hrReadsButCannotWrite() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.source").isNotEmpty());
        mockMvc
            .perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"defaultPassword\":\"Moi@12345\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminSetsThenClears() throws Exception {
        putPassword("{\"defaultPassword\":\"Moi@12345\"}");
        mockMvc.perform(get(URL)).andExpect(jsonPath("$.defaultPassword").value("Moi@12345")).andExpect(jsonPath("$.source").value("WEB"));
        putPassword("{\"defaultPassword\":null}");
        mockMvc
            .perform(get(URL))
            .andExpect(jsonPath("$.defaultPassword").value("Doit@2024"))
            .andExpect(jsonPath("$.source").value("DEFAULT"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void shortPasswordReturns400() throws Exception {
        mockMvc
            .perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"defaultPassword\":\"ngan\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.defaultpasswordinvalid"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void newAccountAfterChangeUsesNewPasswordAndResetFollows() throws Exception {
        putPassword("{\"defaultPassword\":\"Moi@12345\"}");
        mockMvc
            .perform(
                post("/api/admin/users")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"x\",\"idNumber\":\"" + LOGIN + "\",\"authorities\":[\"ROLE_NHAN_VIEN\"]}")
            )
            .andExpect(status().isCreated());
        mockMvc
            .perform(
                post("/api/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + LOGIN + "\",\"password\":\"Moi@12345\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id_token").isNotEmpty());

        putPassword("{\"defaultPassword\":\"Khac@67890\"}");
        mockMvc.perform(put("/api/admin/users/" + LOGIN + "/password/default").with(ADMIN)).andExpect(status().is2xxSuccessful());
        mockMvc
            .perform(
                post("/api/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + LOGIN + "\",\"password\":\"Khac@67890\"}")
            )
            .andExpect(status().isOk());
    }
}
