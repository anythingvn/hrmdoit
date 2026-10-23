package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.payroll.PayrollFixture;
import vn.doit.hrm.service.payroll.PayrollTestData;

/** Bản nháp import bảng lương: tải file, đọc, kiểm tra, báo cáo lỗi; file sai loại bị từ chối. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(PayrollTestData.class)
@WithMockUser(username = "ketoan-it", authorities = "ROLE_KE_TOAN")
class PayrollImportResourceIT {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-payroll-it");
        registry.add("application.storage.root", storage::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollTestData data;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        data.clean();
        data.seedProfiles();
    }

    @AfterEach
    void clean() {
        data.clean();
    }

    static byte[] fixture() throws IOException {
        try (InputStream in = PayrollImportResourceIT.class.getResourceAsStream(PayrollFixture.RESOURCE)) {
            return in.readAllBytes();
        }
    }

    static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, XLSX, content);
    }

    @Test
    void uploadReadsAndChecksSample() throws Exception {
        String body = mockMvc
            .perform(multipart("/api/payroll/imports").file(file("Bang luong 06-2026.xlsx", fixture())).param("period", "2026-06"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.period").value("2026-06"))
            .andExpect(jsonPath("$.status").value("DANG_LAM"))
            .andExpect(jsonPath("$.fileName").value("Bang luong 06-2026.xlsx"))
            .andExpect(jsonPath("$.rows.length()").value(14))
            .andExpect(jsonPath("$.errorCount").value(7))
            .andExpect(jsonPath("$.columns.length()").value(36))
            .andExpect(jsonPath("$.missingRequired.length()").value(0))
            .andExpect(jsonPath("$.canConfirm").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
        Integer id = JsonPath.read(body, "$.id");

        mockMvc
            .perform(get("/api/payroll/imports/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorCount").value(7))
            .andExpect(jsonPath("$.rows[0].employeeCode").value("DOIT2401"));

        mockMvc
            .perform(get("/api/payroll/imports").param("status", "DANG_LAM"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id));

        byte[] report = mockMvc
            .perform(get("/api/payroll/imports/{id}/issues.xlsx", id))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(report))) {
            XSSFSheet sh = wb.getSheetAt(0);
            assertThat(sh.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Dòng");
            assertThat(sh.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Mức độ");
            // 7 lỗi + 6 cảnh báo (3 thật + 2 dòng mang mã trùng có tên khác hồ sơ của mã đó + "Bảng công tháng này chưa chốt").
            assertThat(sh.getLastRowNum()).isEqualTo(13);
        }

        mockMvc.perform(post("/api/payroll/imports/{id}/acknowledge-warnings", id)).andExpect(jsonPath("$.warningsAcknowledged").value(true));
        mockMvc.perform(delete("/api/payroll/imports/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/payroll/imports/{id}", id)).andExpect(jsonPath("$.status").value("HUY")).andExpect(jsonPath("$.canConfirm").value(false));
    }

    @Test
    void wrongFilesRejectedWithoutDraft() throws Exception {
        mockMvc
            .perform(multipart("/api/payroll/imports").file(file("bang-luong.xls", fixture())).param("period", "2026-06"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.payrollfilenotxlsx"));
        mockMvc
            .perform(
                multipart("/api/payroll/imports").file(file("gia.xlsx", "không phải Excel".getBytes(StandardCharsets.UTF_8))).param("period", "2026-06")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.payrollfilenotxlsx"));
        mockMvc
            .perform(multipart("/api/payroll/imports").file(file("trong.xlsx", workbookWithoutHeader())).param("period", "2026-06"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.payrollfilelayout"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Mã nhân viên")));
        mockMvc
            .perform(multipart("/api/payroll/imports").file(file("a.xlsx", fixture())).param("period", "06/2026"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.periodinvalid"));
        assertThat(jdbc.queryForList("select file_name from payroll_import")).isEmpty();
    }

    @Test
    @WithMockUser(authorities = { "ROLE_HR", "ROLE_BGD" })
    void hrAndBoardForbidden() throws Exception {
        mockMvc
            .perform(multipart("/api/payroll/imports").file(file("a.xlsx", fixture())).param("period", "2026-06"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payroll/imports")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payroll/imports/template.xlsx")).andExpect(status().isForbidden());
    }

    @Test
    void templateDownloadIsReadableByTheImporter() throws Exception {
        byte[] xlsx = mockMvc
            .perform(get("/api/payroll/imports/template.xlsx"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("mau-bang-luong.xlsx")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        var parsed = new vn.doit.hr.payroll.PayrollSheetParser().parse(vn.doit.hr.payroll.PoiSheetGrid.read(new java.io.ByteArrayInputStream(xlsx)));
        assertThat(parsed.columns()).hasSize(vn.doit.hr.payroll.PayrollField.values().length);
    }

    private static byte[] workbookWithoutHeader() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("Bảng lương").createRow(0).createCell(0).setCellValue("Không có tiêu đề");
            wb.write(out);
            return out.toByteArray();
        }
    }
}
