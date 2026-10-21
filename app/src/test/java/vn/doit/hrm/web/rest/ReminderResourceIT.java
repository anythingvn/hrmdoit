package vn.doit.hrm.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.UserRepository;

/** Nhắc việc sắp tới (trang Tổng quan) và trạng thái / gửi thử email (Quản trị). */
@IntegrationTest
@AutoConfigureMockMvc
class ReminderResourceIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "smtp.test");
    }

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long employeeId;
    private Long probationId;
    private Long userId;

    @BeforeEach
    void seed() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        tx.executeWithoutResult(s -> {
            Employee e = employeeRepository.save(
                new Employee().code("UP-1").fullName("Người Sắp Hết Thử Việc").personType(PersonType.NHAN_VIEN).status(EmploymentStatus.DANG_LAM_VIEC).dependants(0)
            );
            employeeId = e.getId();
            probationId = contractRepository
                .save(new EmploymentContract().type(ContractType.HDTV).startDate(today.minusMonths(1)).endDate(today.plusDays(5)).status(ContractStatus.DA_KY).employee(e))
                .getId();
            User u = new User();
            u.setLogin("qt-up");
            u.setEmail("qt-up@doit.test");
            u.setActivated(true);
            u.setLangKey("vi");
            u.setPassword(passwordEncoder.encode("MatKhau@2026"));
            u.setAuthorities(Set.of(authorityRepository.findById("ROLE_ADMIN").orElseThrow()));
            userId = userRepository.save(u).getId();
        });
    }

    @AfterEach
    void clean() {
        tx.executeWithoutResult(s -> {
            contractRepository.findByEmployeeId(employeeId).forEach(contractRepository::delete);
            employeeRepository.deleteById(employeeId);
            userRepository.deleteById(userId);
        });
    }

    @Test
    void upcomingForBusinessRoles() throws Exception {
        for (String role : new String[] { "ROLE_HR", "ROLE_KE_TOAN", "ROLE_BGD", "ROLE_ADMIN" }) {
            mockMvc
                .perform(get("/api/reminders/upcoming").with(user("x").authorities(() -> role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.employeeCode=='UP-1')].type").value(org.hamcrest.Matchers.hasItem("PROBATION_END")))
                .andExpect(jsonPath("$[?(@.employeeCode=='UP-1')].contractId").value(org.hamcrest.Matchers.hasItem(probationId.intValue())))
                .andExpect(jsonPath("$[?(@.employeeCode=='UP-1')].employeeId").value(org.hamcrest.Matchers.hasItem(employeeId.intValue())))
                .andExpect(jsonPath("$[?(@.employeeCode=='UP-1')].daysLeft").value(org.hamcrest.Matchers.hasItem(5)));
        }
        mockMvc
            .perform(get("/api/reminders/upcoming").param("type", "BIRTHDAY").with(user("x").authorities(() -> "ROLE_HR")))
            .andExpect(jsonPath("$[?(@.employeeCode=='UP-1')]").isEmpty());
        mockMvc.perform(get("/api/reminders/upcoming").with(user("x").authorities(() -> "ROLE_NHAN_VIEN"))).andExpect(status().isForbidden());
    }

    @Test
    void adminStatusAndTestSend() throws Exception {
        mockMvc.perform(get("/api/admin/reminders/status").with(user("x").authorities(() -> "ROLE_HR"))).andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/admin/reminders/status").with(user("qt-up").authorities(() -> "ROLE_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mailConfigured").value(true))
            .andExpect(jsonPath("$.missingRecipients").isArray());
        mockMvc.perform(post("/api/admin/reminders/test-send").with(user("qt-up").authorities(() -> "ROLE_ADMIN"))).andExpect(status().isOk());
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
