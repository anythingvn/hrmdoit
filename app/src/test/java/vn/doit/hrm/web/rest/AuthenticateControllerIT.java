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
    void parallelWrongPasswordsAtMostFiveChecked() throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(20);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                results.add(
                    executor.submit(() -> {
                        start.await();
                        return login("song-song-rate", "sai-mat-khau", "10.0.0.20").andReturn().getResponse().getStatus();
                    })
                );
            }
            start.countDown();
            int tooMany = 0;
            int unauthorized = 0;
            for (var f : results) {
                int code = f.get(60, java.util.concurrent.TimeUnit.SECONDS);
                if (code == 429) tooMany++;
                if (code == 401) unauthorized++;
            }
            org.assertj.core.api.Assertions.assertThat(unauthorized).isLessThanOrEqualTo(5);
            org.assertj.core.api.Assertions.assertThat(tooMany).isGreaterThanOrEqualTo(15);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional
    void emailAndLoginShareTheCounter() throws Exception {
        createUser("user-rate-email", "MatKhauDung2026");
        for (int i = 0; i < 3; i++) {
            login("user-rate-email", "sai-mat-khau", "10.0.0.9").andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 2; i++) {
            login("User-Rate-Email@Example.com", "sai-mat-khau", "10.0.0.9").andExpect(status().isUnauthorized());
        }
        login("user-rate-email", "MatKhauDung2026", "10.0.0.9").andExpect(status().isTooManyRequests());
    }

    @Test
    void perIpCapAcrossManyLogins() throws Exception {
        for (int i = 0; i < LoginAttemptService.MAX_IP_FAILURES; i++) {
            login("ip-cap-" + i, "sai-mat-khau", "10.0.0.30").andExpect(status().isUnauthorized());
        }
        login("tai-khoan-khac", "sai-mat-khau", "10.0.0.30")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("địa chỉ mạng")));
    }

    @Test
    @Transactional
    void adminCanUnlockHrCannot() throws Exception {
        createUser("user-rate-unlock", "MatKhauDung2026");
        for (int i = 0; i < 5; i++) {
            login("user-rate-unlock", "sai-mat-khau", "10.0.0.11").andExpect(status().isUnauthorized());
        }
        login("user-rate-unlock", "MatKhauDung2026", "10.0.0.11").andExpect(status().isTooManyRequests());
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/admin/login-attempts/user-rate-unlock"
                ).with(
                    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("hr").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_HR")
                    )
                )
            )
            .andExpect(status().isForbidden());
        login("user-rate-unlock", "MatKhauDung2026", "10.0.0.11").andExpect(status().isTooManyRequests());
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/admin/login-attempts/user-rate-unlock"
                ).with(
                    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")
                    )
                )
            )
            .andExpect(status().isNoContent());
        login("user-rate-unlock", "MatKhauDung2026", "10.0.0.11").andExpect(status().isOk());
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
