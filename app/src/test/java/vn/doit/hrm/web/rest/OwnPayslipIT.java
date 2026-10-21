package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.doit.hrm.web.rest.PayrollFlow.ADMIN;
import static vn.doit.hrm.web.rest.PayrollFlow.KE_TOAN;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.SensitiveAccessLogRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.payroll.PayrollTestData;
import vn.doit.hrm.service.pdf.PdfService;

/** Nhân viên xem phiếu lương của chính mình ở Hồ sơ của tôi: chỉ các kỳ đã gửi thành công, không xem được của người khác. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(PayrollTestData.class)
class OwnPayslipIT {

    static final byte[] PDF = "%PDF-1.4 phieu".getBytes();
    private static final RequestPostProcessor ME = user("nv-own").authorities(() -> "ROLE_NHAN_VIEN");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-own-payslip-it");
        registry.add("application.storage.root", storage::toString);
    }

    @MockitoBean
    private PdfService pdfService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollTestData data;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SensitiveAccessLogRepository accessLogRepository;

    private Map<String, Long> ids;
    private Long userId;

    @BeforeEach
    void seed() throws Exception {
        clean();
        when(pdfService.toPdf(any(), anyString())).thenReturn(PDF);
        ids = new HashMap<>(data.seedProfiles());
        ids.put("TT2601", data.seedService("TT2601", "Mai Văn Phúc"));
        ids.put("HDDV9001", data.seedService("HDDV9001", "Tạ Văn Quý"));
        ids.put("HDDV9002", data.seedService("HDDV9002", "Hồ Thị Sen"));
        Integer draft = PayrollFlow.readyDraft(mockMvc, "2026-06", ids);
        mockMvc.perform(post("/api/payroll/imports/{id}/confirm", draft).with(KE_TOAN)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(ADMIN)).andExpect(status().isOk());
        tx.executeWithoutResult(s -> {
            User u = new User();
            u.setLogin("nv-own");
            u.setActivated(true);
            u.setLangKey("vi");
            u.setPassword(passwordEncoder.encode("MatKhau@2026"));
            u.setAuthorities(Set.of(authorityRepository.findById("ROLE_NHAN_VIEN").orElseThrow()));
            userId = userRepository.saveAndFlush(u).getId();
            jdbc.update("update employee set user_id = ? where id = ?", userId, ids.get("DOIT2401"));
        });
    }

    private void delivered(String code, String status) {
        Long periodId = jdbc.queryForObject("select id from payroll_period where period = '2026-06'", Long.class);
        tx.executeWithoutResult(s ->
            jdbc.update(
                "insert into payslip_delivery (id, period_id, version, employee_id, email, status, requested_by, requested_at, sent_at) " +
                "values (nextval('sequence_generator'), ?, 1, ?, 'x@doit.test', ?, 'kt', now(), now())",
                periodId,
                ids.get(code),
                status
            )
        );
    }

    @AfterEach
    void clean() {
        tx.executeWithoutResult(s -> {
            jdbc.update("delete from payslip_delivery");
            if (userId != null) {
                jdbc.update("update employee set user_id = null where user_id = ?", userId);
                userRepository.deleteById(userId);
            }
        });
        userId = null;
        data.clean();
    }

    @Test
    void ownSentPayslipListedAndDownloadable() throws Exception {
        mockMvc.perform(get("/api/account/employee/payslips").with(ME)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        delivered("DOIT2401", "FAILED");
        mockMvc.perform(get("/api/account/employee/payslips").with(ME)).andExpect(jsonPath("$.length()").value(0));
        delivered("DOIT2401", "SENT");
        mockMvc
            .perform(get("/api/account/employee/payslips").with(ME))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].period").value("2026-06"))
            .andExpect(jsonPath("$[0].version").value(1));
        long logs = accessLogRepository.count();
        mockMvc
            .perform(get("/api/account/employee/payslips/2026-06/pdf").with(ME))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(content().bytes(PDF));
        assertThat(accessLogRepository.count()).isEqualTo(logs + 1);
    }

    @Test
    void otherEmployeeOrUnsentPeriodIs404() throws Exception {
        delivered("DOIT2402", "SENT");
        mockMvc.perform(get("/api/account/employee/payslips").with(ME)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/account/employee/payslips/2026-06/pdf").with(ME)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/account/employee/payslips/2026-05/pdf").with(ME)).andExpect(status().isNotFound());
        mockMvc
            .perform(get("/api/account/employee/payslips").with(user("chua-gan").authorities(() -> "ROLE_HR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
