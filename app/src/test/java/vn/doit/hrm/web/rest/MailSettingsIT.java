package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.mail.DynamicMailSender;
import vn.doit.hrm.service.mail.MailSettingsService;
import vn.doit.hrm.service.reminder.ReminderMailer;

/** Quản trị cấu hình máy chủ gửi email trên giao diện: mật khẩu mã hóa, không trả về; có hiệu lực ngay; chưa cấu hình thì dùng .env. */
@IntegrationTest
@AutoConfigureMockMvc
class MailSettingsIT {

    private static final RequestPostProcessor ADMIN = user("admin").authorities(() -> "ROLE_ADMIN");
    private static final String URL = "/api/admin/mail-settings";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "smtp.env.test");
        registry.add("application.mail.secret-key", () -> "khoa-test-cau-hinh-email-0123456789");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private MailSettingsService settings;

    @Autowired
    private ReminderMailer reminderMailer;

    @AfterEach
    void clean() throws Exception {
        mockMvc.perform(delete(URL).with(ADMIN));
    }

    private void save(String json) throws Exception {
        mockMvc.perform(put(URL).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    @Test
    void envUntilSavedThenDatabaseWithoutExposingPassword() throws Exception {
        mockMvc
            .perform(get(URL).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("ENV"))
            .andExpect(jsonPath("$.host").value("smtp.env.test"))
            .andExpect(jsonPath("$.keyAvailable").value(true));

        save(
            "{\"host\":\"mail.doit.com.vn\",\"port\":587,\"security\":\"STARTTLS\",\"username\":\"admin@doit.com.vn\"," +
            "\"password\":\"MatKhau@Thu1\",\"fromAddress\":\"admin@doit.com.vn\",\"fromName\":\"Nhân sự DOIT\"}"
        );
        String body = mockMvc
            .perform(get(URL).with(ADMIN))
            .andExpect(jsonPath("$.source").value("DATABASE"))
            .andExpect(jsonPath("$.host").value("mail.doit.com.vn"))
            .andExpect(jsonPath("$.passwordSet").value(true))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(body).doesNotContain("MatKhau@Thu1");
        String stored = tx.execute(s -> jdbc.queryForObject("select password_enc from mail_setting", String.class));
        assertThat(stored).isNotBlank().doesNotContain("MatKhau@Thu1");

        // Có hiệu lực ngay: bộ gửi mail và kiểm tra "đã cấu hình" theo cấu hình mới.
        assertThat(((DynamicMailSender) mailSender).delegate().getHost()).isEqualTo("mail.doit.com.vn");
        assertThat(((DynamicMailSender) mailSender).delegate().getPassword()).isEqualTo("MatKhau@Thu1");
        assertThat(reminderMailer.configured()).isTrue();
        assertThat(settings.from().getAddress()).isEqualTo("admin@doit.com.vn");
        assertThat(settings.from().getPersonal()).isEqualTo("Nhân sự DOIT");

        // Để trống mật khẩu: giữ mật khẩu cũ.
        save(
            "{\"host\":\"mail.doit.com.vn\",\"port\":465,\"security\":\"SSL\",\"username\":\"admin@doit.com.vn\"," +
            "\"fromAddress\":\"admin@doit.com.vn\",\"fromName\":\"Nhân sự DOIT\"}"
        );
        assertThat(settings.current().password()).isEqualTo("MatKhau@Thu1");
        assertThat(settings.current().port()).isEqualTo(465);

        // Xóa mật khẩu.
        save(
            "{\"host\":\"mail.doit.com.vn\",\"port\":465,\"security\":\"SSL\",\"username\":\"admin@doit.com.vn\",\"clearPassword\":true," +
            "\"fromAddress\":\"admin@doit.com.vn\"}"
        );
        assertThat(settings.current().password()).isNull();

        mockMvc.perform(delete(URL).with(ADMIN)).andExpect(status().isNoContent());
        mockMvc.perform(get(URL).with(ADMIN)).andExpect(jsonPath("$.source").value("ENV")).andExpect(jsonPath("$.host").value("smtp.env.test"));
    }

    @Test
    void invalidInputRejected() throws Exception {
        mockMvc
            .perform(put(URL).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"host\":\"\",\"port\":587,\"fromAddress\":\"a@b.vn\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.mailsettingsinvalid"));
        mockMvc
            .perform(
                put(URL).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"host\":\"smtp.x.vn\",\"port\":587,\"fromAddress\":\"khong-phai-email\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.mailsettingsinvalid"));
    }

    @Test
    void testSendReportsConnectionFailure() throws Exception {
        save("{\"host\":\"127.0.0.1\",\"port\":1,\"security\":\"NONE\",\"fromAddress\":\"hrm@doit.test\"}");
        mockMvc
            .perform(post(URL + "/test").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"qt@doit.test\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.mailtestfailed"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Không kết nối được máy chủ")));
    }

    @Test
    void onlyAdmin() throws Exception {
        for (String role : new String[] { "ROLE_HR", "ROLE_KE_TOAN" }) {
            mockMvc.perform(get(URL).with(user("x").authorities(() -> role))).andExpect(status().isForbidden());
        }
    }
}
