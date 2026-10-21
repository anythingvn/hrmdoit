package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.doit.hrm.web.rest.PayrollFlow.ADMIN;
import static vn.doit.hrm.web.rest.PayrollFlow.KE_TOAN;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.repository.SensitiveAccessLogRepository;
import vn.doit.hrm.service.payroll.PayrollTestData;
import vn.doit.hrm.service.pdf.PdfService;

/** Phiếu lương PDF (không mật khẩu) để Kế toán, Quản trị kiểm tra; chỉ kỳ đã duyệt. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(PayrollTestData.class)
class PayslipDocumentIT {

    static final byte[] PDF = "%PDF-1.4 phieu".getBytes();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-payslip-doc-it");
        registry.add("application.storage.root", storage::toString);
    }

    @MockitoBean
    private PdfService pdfService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollTestData data;

    @Autowired
    private SensitiveAccessLogRepository accessLogRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Map<String, Long> ids;

    @BeforeEach
    void seed() throws Exception {
        data.clean();
        when(pdfService.toPdf(any(), anyString())).thenReturn(PDF);
        ids = new HashMap<>(data.seedProfiles());
        ids.put("TT2601", data.seedService("TT2601", "Mai Văn Phúc"));
        ids.put("HDDV9001", data.seedService("HDDV9001", "Tạ Văn Quý"));
        ids.put("HDDV9002", data.seedService("HDDV9002", "Hồ Thị Sen"));
        Integer draft = PayrollFlow.readyDraft(mockMvc, "2026-06", ids);
        mockMvc.perform(post("/api/payroll/imports/{id}/confirm", draft).with(KE_TOAN)).andExpect(status().isCreated());
    }

    @AfterEach
    void clean() {
        data.clean();
    }

    private String url(String code) {
        return "/api/payroll/periods/2026-06/payslips/" + ids.get(code) + "/pdf";
    }

    @Test
    void accountantDownloadsPayslipOfApprovedPeriod() throws Exception {
        mockMvc.perform(get(url("DOIT2401")).with(KE_TOAN)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.payslipnotapproved"));

        mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(ADMIN)).andExpect(status().isOk());
        long logs = accessLogRepository.count();
        mockMvc
            .perform(get(url("DOIT2401")).with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(content().bytes(PDF));
        assertThat(accessLogRepository.count()).isEqualTo(logs + 1);

        ArgumentCaptor<byte[]> docx = ArgumentCaptor.forClass(byte[].class);
        verify(pdfService).toPdf(docx.capture(), anyString());
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx.getValue())); XWPFWordExtractor text = new XWPFWordExtractor(doc)) {
            String all = text.getText();
            assertThat(all).contains("Phạm Văn An").contains("DOIT2401").contains("Thực lĩnh").contains("06/2026").contains("Căn cứ:").doesNotContain("{{");
        }
        assertThat(jdbc.queryForObject("select rules_snapshot from payroll_version", String.class)).contains("legalBasis");

        mockMvc.perform(get(url("TT2601")).with(ADMIN)).andExpect(status().isOk());
    }

    @Test
    void otherRolesForbidden() throws Exception {
        mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(ADMIN)).andExpect(status().isOk());
        for (String role : new String[] { "ROLE_HR", "ROLE_BGD", "ROLE_NHAN_VIEN" }) {
            mockMvc.perform(get(url("DOIT2401")).with(user("x").authorities(() -> role))).andExpect(status().isForbidden());
        }
    }
}
