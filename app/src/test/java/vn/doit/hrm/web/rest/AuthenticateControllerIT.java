package vn.doit.hrm.web.rest;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.LoginAttemptService;
import vn.doit.hrm.web.rest.vm.LoginVM;

/**
 * Integration tests for the {@link AuthenticateController} REST controller.
 */
@AutoConfigureMockMvc
@IntegrationTest
class AuthenticateControllerIT {

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void resetAttempts() {
        loginAttemptService.clear();
    }

    private void createUser(String login, String password) {
        User user = new User();
        user.setLogin(login);
        user.setEmail(login + "@example.com");
        user.setActivated(true);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.saveAndFlush(user);
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password, String ip) throws Exception {
        LoginVM login = new LoginVM();
        login.setUsername(username);
        login.setPassword(password);
        return mockMvc.perform(
            post("/api/authenticate")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(login))
        );
    }

    @Test
    @Transactional
    void fiveFailuresThenTooManyRequestsEvenWithRightPassword() throws Exception {
        createUser("user-rate-limit", "MatKhauDung2026");
        for (int i = 0; i < 5; i++) {
            login("user-rate-limit", "sai-mat-khau", "10.0.0.5").andExpect(status().isUnauthorized());
        }
        login("user-rate-limit", "MatKhauDung2026", "10.0.0.5")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.message").value("error.toomanyloginattempts"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Vui lòng thử lại sau")))
            .andExpect(jsonPath("$.id_token").doesNotExist());
        // IP khác không bị ảnh hưởng.
        login("user-rate-limit", "MatKhauDung2026", "10.0.0.6").andExpect(status().isOk());
    }

    @Test
    @Transactional
    void successfulLoginResetsCounter() throws Exception {
        createUser("user-rate-reset", "MatKhauDung2026");
        for (int i = 0; i < 4; i++) {
            login("user-rate-reset", "sai-mat-khau", "10.0.0.7").andExpect(status().isUnauthorized());
        }
        login("user-rate-reset", "MatKhauDung2026", "10.0.0.7").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("user-rate-reset", "sai-mat-khau", "10.0.0.7").andExpect(status().isUnauthorized());
        }
        login("user-rate-reset", "MatKhauDung2026", "10.0.0.7").andExpect(status().isOk());
    }

    @Test
    void unknownLoginAlsoLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("khong-ton-tai-rate", "sai-mat-khau", "10.0.0.8").andExpect(status().isUnauthorized());
        }
        login("khong-ton-tai-rate", "sai-mat-khau", "10.0.0.8").andExpect(status().isTooManyRequests());
    }

    @Test
    @Transactional
    void testAuthorize() throws Exception {
        User user = new User();
        user.setLogin("user-jwt-controller");
        user.setEmail("user-jwt-controller@example.com");
        user.setActivated(true);
        user.setPassword(passwordEncoder.encode("test"));

        userRepository.saveAndFlush(user);

        LoginVM login = new LoginVM();
        login.setUsername("user-jwt-controller");
        login.setPassword("test");
        mockMvc
            .perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(login)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id_token").isString())
            .andExpect(jsonPath("$.id_token").isNotEmpty())
            .andExpect(header().string("Authorization", not(nullValue())))
            .andExpect(header().string("Authorization", not(is(emptyString()))));
    }

    @Test
    @Transactional
    void testAuthorizeWithRememberMe() throws Exception {
        User user = new User();
        user.setLogin("user-jwt-controller-remember-me");
        user.setEmail("user-jwt-controller-remember-me@example.com");
        user.setActivated(true);
        user.setPassword(passwordEncoder.encode("test"));

        userRepository.saveAndFlush(user);

        LoginVM login = new LoginVM();
        login.setUsername("user-jwt-controller-remember-me");
        login.setPassword("test");
        login.setRememberMe(true);
        mockMvc
            .perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(login)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id_token").isString())
            .andExpect(jsonPath("$.id_token").isNotEmpty())
            .andExpect(header().string("Authorization", not(nullValue())))
            .andExpect(header().string("Authorization", not(is(emptyString()))));
    }

    @Test
    void testAuthorizeFails() throws Exception {
        LoginVM login = new LoginVM();
        login.setUsername("wrong-user");
        login.setPassword("wrong password");
        mockMvc
            .perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(login)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.id_token").doesNotExist())
            .andExpect(header().doesNotExist("Authorization"));
    }
}
