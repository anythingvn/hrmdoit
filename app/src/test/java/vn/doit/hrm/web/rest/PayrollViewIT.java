package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.doit.hrm.web.rest.PayrollFlow.ADMIN;
import static vn.doit.hrm.web.rest.PayrollFlow.KE_TOAN;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.payroll.PayrollFixture;
import vn.doit.hrm.service.payroll.PayrollTestData;

/** Xem bảng lương đã lưu: danh sách kỳ, chi tiết, dòng lương, file gốc, khối trên hồ sơ; chỉ Kế toán và Quản trị. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(PayrollTestData.class)
class PayrollViewIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-payroll-view-it");
        registry.add("application.storage.root", storage::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollTestData data;

    private Map<String, Long> ids;

    @BeforeEach
    void seed() throws Exception {
        data.clean();
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

    @Test
    void periodListAndDetail() throws Exception {
        mockMvc
            .perform(get("/api/payroll/periods").with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].period").value("2026-06"))
            .andExpect(jsonPath("$[0].status").value("NHAP"))
            .andExpect(jsonPath("$[0].officialVersion").value(1))
            .andExpect(jsonPath("$[0].latestVersion").value(1))
            .andExpect(jsonPath("$[0].headcount").value(14))
            .andExpect(jsonPath("$[0].totalNet").value(PayrollFixture.TOTAL_NET))
            .andExpect(jsonPath("$[0].totalCost").value(PayrollFixture.TOTAL_COST));

        mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(ADMIN)).andExpect(status().isOk());
        mockMvc
            .perform(get("/api/payroll/periods/2026-06").with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_DUYET"))
            .andExpect(jsonPath("$.officialVersion").value(1))
            .andExpect(jsonPath("$.versions[0].version").value(1))
            .andExpect(jsonPath("$.versions[0].headcount").value(14))
            .andExpect(jsonPath("$.versions[0].rules.effectiveFrom").value("2026-01"))
            .andExpect(jsonPath("$.versions[0].rules.personalDeduction").value(15500000))
            .andExpect(jsonPath("$.versions[0].issues.length()").value(3))
            .andExpect(jsonPath("$.events[0].action").value("DUYET"))
            .andExpect(jsonPath("$.events[0].actor").value("admin-it"));
    }

    @Test
    void linesFileAndEmployeeBlock() throws Exception {
        mockMvc
            .perform(get("/api/payroll/periods/2026-06/versions/1/lines").with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(14))
            .andExpect(jsonPath("$[0].excelRow").value(10))
            .andExpect(jsonPath("$[0].employeeCode").value("DOIT2401"))
            .andExpect(jsonPath("$[0].amounts.length()").value(36))
            .andExpect(jsonPath("$[?(@.excelRow==18)].employeeCode").value(org.hamcrest.Matchers.hasItem("DOIT2515")))
            .andExpect(jsonPath("$[?(@.excelRow==18)].fileCode").value(org.hamcrest.Matchers.hasItem("DOIT2508")))
            .andExpect(jsonPath("$[?(@.excelRow==18)].mappedManually").value(org.hamcrest.Matchers.hasItem(true)));

        byte[] original = mockMvc
            .perform(get("/api/payroll/periods/2026-06/versions/1/file").with(KE_TOAN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        assertThat(original).isEqualTo(PayrollImportResourceIT.fixture());

        mockMvc
            .perform(get("/api/employees/{id}/payroll", ids.get("DOIT2515")).with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].period").value("2026-06"))
            .andExpect(jsonPath("$[0].version").value(1))
            .andExpect(jsonPath("$[0].thucNhan").isNumber());

        mockMvc.perform(get("/api/payroll/fields").with(KE_TOAN)).andExpect(jsonPath("$.length()").value(36));
        mockMvc.perform(get("/api/payroll/periods/2026-06/versions/9/lines").with(KE_TOAN)).andExpect(status().isNotFound());
    }

    @Test
    void otherRolesForbidden() throws Exception {
        for (String role : List.of("ROLE_HR", "ROLE_BGD", "ROLE_NHAN_VIEN")) {
            var u = user("x").authorities(() -> role);
            mockMvc.perform(get("/api/payroll/periods").with(u)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/payroll/periods/2026-06").with(u)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/payroll/periods/2026-06/versions/1/lines").with(u)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/payroll/periods/2026-06/versions/1/file").with(u)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/employees/{id}/payroll", ids.get("DOIT2515")).with(u)).andExpect(status().isForbidden());
        }
    }
}
