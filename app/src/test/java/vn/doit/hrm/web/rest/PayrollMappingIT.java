package vn.doit.hrm.web.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.service.payroll.PayrollTestData;

/** Gắn hồ sơ tay cho dòng lương, gợi ý theo tên, tạo hồ sơ rút gọn (Kế toán chỉ tạo, không sửa hồ sơ). */
@IntegrationTest
@AutoConfigureMockMvc
@Import(PayrollTestData.class)
@WithMockUser(username = "ketoan-it", authorities = "ROLE_KE_TOAN")
class PayrollMappingIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-payroll-map-it");
        registry.add("application.storage.root", storage::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollTestData data;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate tx;

    private Map<String, Long> ids;
    private Long serviceId;
    private Integer importId;

    @BeforeEach
    void seed() throws Exception {
        data.clean();
        ids = data.seedProfiles();
        serviceId = data.seedService("HDDV9001", "Tạ Văn Quý");
        importId = JsonPath.read(
            mockMvc
                .perform(
                    multipart("/api/payroll/imports")
                        .file(PayrollImportResourceIT.file("bang-luong.xlsx", PayrollImportResourceIT.fixture()))
                        .param("period", "2026-06")
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id"
        );
    }

    @AfterEach
    void clean() {
        employeeRepository.findOneByCode("TT2601").ifPresent(e -> data.track(e.getId()));
        employeeRepository.findAll().stream().filter(e -> "Hồ Thị Sen".equals(e.getFullName())).forEach(e -> data.track(e.getId()));
        data.clean();
    }

    private String row(int excelRow) {
        return "/api/payroll/imports/" + importId + "/rows/" + excelRow;
    }

    private org.springframework.test.web.servlet.ResultActions map(int excelRow, Long employeeId) throws Exception {
        return mockMvc.perform(put(row(excelRow) + "/employee").contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":" + employeeId + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions create(int excelRow, String json) throws Exception {
        return mockMvc.perform(post(row(excelRow) + "/new-employee").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void mapAndCreateClearsAllErrors() throws Exception {
        map(18, ids.get("DOIT2515")).andExpect(status().isOk()).andExpect(jsonPath("$.errorCount").value(5));
        map(19, ids.get("DOIT2516")).andExpect(status().isOk());
        map(22, serviceId).andExpect(status().isOk());
        create(21, "{\"fullName\":\"Mai Văn Phúc\",\"personType\":\"NHAN_VIEN\",\"code\":\"TT2601\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[?(@.excelRow==21)].employeeCode").value(hasItem("TT2601")));
        create(23, "{\"fullName\":\"Hồ Thị Sen\",\"personType\":\"DICH_VU\",\"code\":\"BO-QUA\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[?(@.excelRow==23)].employeeCode").value(hasItem(startsWith("HDDV"))))
            .andExpect(jsonPath("$.rows[?(@.excelRow==23)].mappedManually").value(hasItem(true)))
            .andExpect(jsonPath("$.errorCount").value(0))
            .andExpect(jsonPath("$.warningCount").value(3))
            .andExpect(jsonPath("$.canConfirm").value(false));
        mockMvc
            .perform(post("/api/payroll/imports/{id}/acknowledge-warnings", importId))
            .andExpect(jsonPath("$.canConfirm").value(true));
    }

    @Test
    void oneProfileCannotTakeTwoRows() throws Exception {
        // DOIT2508 đã tự khớp ở dòng 14 sau khi dòng 18 được gắn chỗ khác; gắn dòng 18 vào chính DOIT2508 vẫn là hai dòng một hồ sơ.
        map(19, ids.get("DOIT2516")).andExpect(status().isOk());
        map(15, ids.get("DOIT2513")).andExpect(status().isOk());
        map(18, ids.get("DOIT2513"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeemappedtwice"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("15")));
        map(18, ids.get("DOIT2515")).andExpect(status().isOk());
        mockMvc.perform(delete(row(18) + "/employee")).andExpect(status().isOk());
        map(19, ids.get("DOIT2515")).andExpect(status().isOk());
    }

    @Test
    void autoMatchedProfileCannotBeMappedAgain() throws Exception {
        map(18, ids.get("DOIT2401"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeemappedtwice"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("10")));
    }

    @Test
    void candidatesFoldDiacritics() throws Exception {
        mockMvc
            .perform(get(row(18) + "/candidates").param("q", "duong tuan minh"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code").value(hasItem("DOIT2515")));
        mockMvc
            .perform(get(row(18) + "/candidates").param("q", "TUAN MINH"))
            .andExpect(jsonPath("$[*].code").value(hasItem("DOIT2515")));
        mockMvc
            .perform(get(row(18) + "/candidates"))
            .andExpect(jsonPath("$[0].code").value("DOIT2515"))
            .andExpect(jsonPath("$[0].exactName").value(true));
    }

    /** R6: hồ sơ đã lưu trữ không tự khớp theo mã, không gắn tay được, không có trong gợi ý. */
    @Test
    void archivedProfilesNeverAttach() throws Exception {
        archive(ids.get("DOIT2401"));
        archive(ids.get("DOIT2515"));

        // Tự khớp theo mã (dòng 10, DOIT2401): thành lỗi, dòng không gắn.
        mockMvc
            .perform(get("/api/payroll/imports/{id}", importId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[?(@.excelRow==10)].employeeId").value(hasItem(org.hamcrest.Matchers.nullValue())))
            .andExpect(
                jsonPath("$.issues[?(@.excelRow==10 && @.severity=='ERROR')].message").value(
                    hasItem(vn.doit.hrm.service.payroll.PayrollCheck.ARCHIVED_MESSAGE)
                )
            )
            .andExpect(jsonPath("$.canConfirm").value(false));

        // Gắn tay: 400 employeearchived.
        map(18, ids.get("DOIT2515")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.employeearchived"));

        // Ô gợi ý (dịch vụ gợi ý hồ sơ thật): không có hồ sơ đã lưu trữ.
        mockMvc
            .perform(get(row(18) + "/candidates").param("q", "duong tuan minh"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.not(hasItem("DOIT2515"))));
    }

    private void archive(Long employeeId) {
        tx.executeWithoutResult(s -> {
            vn.doit.hrm.domain.Employee e = employeeRepository.findById(employeeId).orElseThrow();
            e.setArchivedAt(java.time.Instant.parse("2026-10-20T03:00:00Z"));
            e.setArchivedBy("hr");
            employeeRepository.save(e);
        });
    }

    @Test
    void accountantCreatesButCannotEditProfile() throws Exception {
        create(21, "{\"fullName\":\"Mai Văn Phúc\",\"personType\":\"NHAN_VIEN\",\"code\":\"TT2601\"}").andExpect(status().isOk());
        Long id = employeeRepository.findOneByCode("TT2601").orElseThrow().getId();
        mockMvc
            .perform(patch("/api/employees/{id}", id).contentType("application/merge-patch+json").content("{\"id\":" + id + ",\"fullName\":\"Sửa\"}"))
            .andExpect(status().isForbidden());
        create(23, "{\"fullName\":\"Hồ Thị Sen\",\"personType\":\"NHAN_VIEN\",\"code\":\"DOIT2401\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.codeexists"));
    }

    @Test
    void closedDraftAndUnknownRow() throws Exception {
        map(99, ids.get("DOIT2515")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/payroll/imports/{id}", importId)).andExpect(status().isNoContent());
        map(18, ids.get("DOIT2515")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.importclosed"));
    }

    @Test
    void hrForbidden() throws Exception {
        mockMvc
            .perform(
                put(row(18) + "/employee")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("hr").authorities(() -> "ROLE_HR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"employeeId\":" + ids.get("DOIT2515") + "}")
            )
            .andExpect(status().isForbidden());
    }
}
