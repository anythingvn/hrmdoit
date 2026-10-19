package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;

/** Quản trị đặt mật khẩu cho tài khoản (khi không có máy chủ thư để gửi link đặt lại). */
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
class AdminSetPasswordIT {

    private static final String LOGIN = "nhanvien-matkhau";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void init() {
        User user = new User();
        user.setLogin(LOGIN);
        user.setEmail("nhanvien-matkhau@example.com");
        user.setActivated(true);
        user.setLangKey("vi");
        user.setPassword(passwordEncoder.encode("mat-khau-ngau-nhien-cu"));
        userRepository.saveAndFlush(user);
    }

    private org.springframework.test.web.servlet.ResultActions setPassword(String login, String password) throws Exception {
        return mockMvc.perform(
            put("/api/admin/users/{login}/password", login)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"" + password + "\"}")
        );
    }

    @Test
    void adminSetsPasswordThenUserCanLogIn() throws Exception {
        setPassword(LOGIN, "MatKhauMoi2026").andExpect(status().isNoContent()).andExpect(content().string(""));
        User saved = userRepository.findOneByLogin(LOGIN).orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("MatKhauMoi2026");
        assertThat(passwordEncoder.matches("MatKhauMoi2026", saved.getPassword())).isTrue();

        mockMvc
            .perform(
                post("/api/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + LOGIN + "\",\"password\":\"MatKhauMoi2026\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id_token").isNotEmpty());
    }

    @Test
    void tooShortRejected() throws Exception {
        setPassword(LOGIN, "1234567").andExpect(status().isBadRequest());
        assertThat(passwordEncoder.matches("1234567", userRepository.findOneByLogin(LOGIN).orElseThrow().getPassword())).isFalse();
    }

    @Test
    void tooLongRejected() throws Exception {
        setPassword(LOGIN, "a".repeat(101)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserNotFound() throws Exception {
        setPassword("khong-ton-tai", "MatKhauMoi2026").andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void hrForbidden() throws Exception {
        setPassword(LOGIN, "MatKhauMoi2026").andExpect(status().isForbidden());
        assertThat(passwordEncoder.matches("MatKhauMoi2026", userRepository.findOneByLogin(LOGIN).orElseThrow().getPassword())).isFalse();
    }
}
