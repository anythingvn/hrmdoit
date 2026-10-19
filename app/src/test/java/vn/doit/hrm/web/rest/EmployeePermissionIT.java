package vn.doit.hrm.web.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.SensitiveAccessLogRepository;

/**
 * Ma trận phân quyền hồ sơ nhân viên và dữ liệu nhạy cảm.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class EmployeePermissionIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SensitiveAccessLogRepository sensitiveAccessLogRepository;

    private Employee employee;

    @BeforeEach
    void init() {
        employee = employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9001")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Trần Minh Quân")
                .searchText("nv9001 tran minh quan")
                .dependants(0)
                .status(vn.doit.hrm.domain.enumeration.EmploymentStatus.DANG_LAM_VIEC)
                .idNumber("079123456789")
                .idIssueDate(LocalDate.of(2020, 1, 2))
                .idIssuePlace("Cục CSQLHC")
                .taxCode("8001234567")
                .bankAccount("111222333")
                .bankName("Vietcombank")
                .socialInsuranceNo("7900112233")
        );
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void listCannotSortBySensitiveFields() throws Exception {
        for (String sort : new String[] { "idNumber,asc", "taxCode,desc", "bankAccount,asc", "manager.idNumber,asc", "socialInsuranceSalary,asc" }) {
            mockMvc
                .perform(get("/api/employees").param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.badsort"));
        }
        mockMvc.perform(get("/api/employees").param("sort", "id,asc").param("sort", "idNumber,asc")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void listSortsByAllowedFields() throws Exception {
        mockMvc
            .perform(get("/api/employees").param("sort", "fullName,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].code").value(hasItem("NV9001")));
        mockMvc.perform(get("/api/employees").param("sort", "department.name,asc")).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees").param("sort", "code,desc").param("sort", "id,asc")).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void bgdCanRead() throws Exception {
        mockMvc.perform(get("/api/employees/{id}", employee.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees")).andExpect(status().isOk());
        mockMvc.perform(get("/api/employment-contracts")).andExpect(status().isOk());
        mockMvc.perform(get("/api/salary-changes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/allowances")).andExpect(status().isOk());
        mockMvc.perform(get("/api/work-assignments")).andExpect(status().isOk());
        mockMvc.perform(get("/api/employee-documents")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantCanRead() throws Exception {
        mockMvc.perform(get("/api/employees/{id}", employee.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/salary-changes")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void bgdCannotUpdate() throws Exception {
        mockMvc
            .perform(
                put("/api/employees/{id}", employee.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":" + employee.getId() + ",\"code\":\"NV9001\",\"personType\":\"NHAN_VIEN\",\"fullName\":\"X\"}")
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                patch("/api/employees/{id}", employee.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + employee.getId() + ",\"fullName\":\"X\"}")
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantCannotCreateContract() throws Exception {
        mockMvc
            .perform(post("/api/employment-contracts").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/salary-changes").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/allowances").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/work-assignments").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/employee-documents").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void bgdCannotWriteChildren() throws Exception {
        mockMvc
            .perform(put("/api/employment-contracts/1").contentType(MediaType.APPLICATION_JSON).content("{\"id\":1}"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(patch("/api/allowances/1").contentType("application/merge-patch+json").content("{\"id\":1}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/salary-changes/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void listOmitsSensitive() throws Exception {
        mockMvc
            .perform(get("/api/employees"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].code").value(hasItem("NV9001")))
            .andExpect(content().string(not(containsString("idNumber"))))
            .andExpect(content().string(not(containsString("079123456789"))))
            .andExpect(content().string(not(containsString("bankAccount"))))
            .andExpect(content().string(not(containsString("taxCode"))));
        mockMvc
            .perform(get("/api/employees/{id}", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("socialInsuranceNo"))));
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void bgdCannotFilterBySensitive() throws Exception {
        mockMvc.perform(get("/api/employees?idNumber.equals=079123456789")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void sensitiveForHrIsFullAndLogged() throws Exception {
        long before = sensitiveAccessLogRepository.count();
        mockMvc
            .perform(get("/api/employees/{id}/sensitive", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idNumber").value("079123456789"))
            .andExpect(jsonPath("$.idIssueDate").value("2020-01-02"))
            .andExpect(jsonPath("$.idIssuePlace").value("Cục CSQLHC"))
            .andExpect(jsonPath("$.taxCode").value("8001234567"))
            .andExpect(jsonPath("$.bankAccount").value("111222333"))
            .andExpect(jsonPath("$.bankName").value("Vietcombank"))
            .andExpect(jsonPath("$.socialInsuranceNo").value("7900112233"));
        assertEquals(before + 1, sensitiveAccessLogRepository.count());
        var log = sensitiveAccessLogRepository.findAll().stream().filter(l -> l.getEmployeeId().equals(employee.getId())).findFirst().orElseThrow();
        assertEquals("user", log.getViewerLogin());
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void sensitiveForAccountantIsPartial() throws Exception {
        long before = sensitiveAccessLogRepository.count();
        mockMvc
            .perform(get("/api/employees/{id}/sensitive", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idNumber").value(nullValue()))
            .andExpect(jsonPath("$.idIssueDate").value(nullValue()))
            .andExpect(jsonPath("$.idIssuePlace").value(nullValue()))
            .andExpect(jsonPath("$.taxCode").value(nullValue()))
            .andExpect(jsonPath("$.bankAccount").value("111222333"))
            .andExpect(jsonPath("$.bankName").value("Vietcombank"))
            .andExpect(jsonPath("$.socialInsuranceNo").value("7900112233"));
        assertEquals(before + 1, sensitiveAccessLogRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void sensitiveForbiddenForBgd() throws Exception {
        long before = sensitiveAccessLogRepository.count();
        mockMvc.perform(get("/api/employees/{id}/sensitive", employee.getId())).andExpect(status().isForbidden());
        assertEquals(before, sensitiveAccessLogRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void sensitiveNotFoundIsNotLogged() throws Exception {
        long before = sensitiveAccessLogRepository.count();
        mockMvc.perform(get("/api/employees/{id}/sensitive", Long.MAX_VALUE)).andExpect(status().isNotFound());
        assertEquals(before, sensitiveAccessLogRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void hrCanWriteSensitiveOnCreate() throws Exception {
        mockMvc
            .perform(
                post("/api/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"NV9002\",\"personType\":\"NHAN_VIEN\",\"fullName\":\"Lê An\",\"idNumber\":\"0791\",\"bankAccount\":\"999\"}")
            )
            .andExpect(status().isCreated())
            .andExpect(content().string(not(containsString("0791"))));
        Employee saved = employeeRepository.findAll().stream().filter(e -> "NV9002".equals(e.getCode())).findFirst().orElseThrow();
        assertEquals("0791", saved.getIdNumber());
        assertEquals("999", saved.getBankAccount());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void putWithoutSensitiveKeepsThem() throws Exception {
        mockMvc
            .perform(
                put("/api/employees/{id}", employee.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":" + employee.getId() + ",\"code\":\"NV9001\",\"personType\":\"NHAN_VIEN\",\"fullName\":\"Trần Minh Quân 2\"}")
            )
            .andExpect(status().isOk());
        employeeRepository.flush();
        Employee reloaded = employeeRepository.findById(employee.getId()).orElseThrow();
        assertEquals("Trần Minh Quân 2", reloaded.getFullName());
        assertEquals("079123456789", reloaded.getIdNumber());
        assertEquals("8001234567", reloaded.getTaxCode());
        assertEquals("111222333", reloaded.getBankAccount());
        assertEquals("Vietcombank", reloaded.getBankName());
        assertEquals("7900112233", reloaded.getSocialInsuranceNo());
        assertEquals(LocalDate.of(2020, 1, 2), reloaded.getIdIssueDate());
        assertEquals("Cục CSQLHC", reloaded.getIdIssuePlace());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void putWithSensitiveReplacesThem() throws Exception {
        mockMvc
            .perform(
                put("/api/employees/{id}", employee.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":" + employee.getId() + ",\"code\":\"NV9001\",\"personType\":\"NHAN_VIEN\",\"fullName\":\"Q\",\"bankAccount\":\"555\"}")
            )
            .andExpect(status().isOk());
        employeeRepository.flush();
        assertEquals("555", employeeRepository.findById(employee.getId()).orElseThrow().getBankAccount());
    }
}
