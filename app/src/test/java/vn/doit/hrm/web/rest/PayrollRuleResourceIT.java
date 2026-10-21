package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.EmploymentContractService;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.EmploymentContractDTO;
import vn.doit.hrm.service.payroll.PayrollRuleTestSupport;

/** Mức luật định: xem (Quản trị, Kế toán, HR), thêm mốc và đổi "đã biết luật đến" (chỉ Quản trị). */
@IntegrationTest
@AutoConfigureMockMvc
@Import(PayrollRuleTestSupport.class)
class PayrollRuleResourceIT {

    private static final String MILESTONE_2027 =
        "{\"effectiveFrom\":\"2027-01\",\"personalDeduction\":16000000,\"dependantDeduction\":6200000," +
        "\"socialInsuranceCap\":50600000,\"unemploymentInsuranceCap\":106200000," +
        "\"brackets\":[{\"upTo\":10000000,\"rate\":0.05},{\"upTo\":30000000,\"rate\":0.10},{\"upTo\":null,\"rate\":0.35}]," +
        "\"legalBasis\":\"NQ thử nghiệm\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollRuleTestSupport support;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private EmploymentContractService contractService;

    private Long employeeId;

    @AfterEach
    void reset() {
        support.resetToSeed();
        if (employeeId != null) {
            tx.executeWithoutResult(s -> {
                contractRepository.findByEmployeeId(employeeId).forEach(contractRepository::delete);
                employeeRepository.deleteById(employeeId);
            });
        }
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void hrSeesSeededMilestonesNewestFirst() throws Exception {
        mockMvc
            .perform(get("/api/payroll/rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.knownThrough").value("2026-12"))
            .andExpect(jsonPath("$.milestones.length()").value(6))
            .andExpect(jsonPath("$.milestones[0].effectiveFrom").value("2026-07"))
            .andExpect(jsonPath("$.milestones[1].effectiveFrom").value("2026-01"))
            .andExpect(jsonPath("$.milestones[1].personalDeduction").value(15500000))
            .andExpect(jsonPath("$.milestones[1].brackets.length()").value(5))
            .andExpect(jsonPath("$.milestones[1].brackets[4].upTo").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin-luat", authorities = "ROLE_ADMIN")
    void adminAddsMilestoneAndSuggestionChangesButStoredGrossDoesNot() throws Exception {
        Long contractId = savedNetContract();
        Long grossBefore = contractRepository.findById(contractId).orElseThrow().getGrossAmount();
        assertThat(grossBefore).isNotNull();

        mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "20000000").param("month", "2027-03"))
            .andExpect(jsonPath("$.estimated").value(true));
        String before = mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "20000000").param("month", "2027-03"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        mockMvc
            .perform(post("/api/payroll/rules").contentType(MediaType.APPLICATION_JSON).content(MILESTONE_2027))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy").value("admin-luat"));
        mockMvc
            .perform(put("/api/payroll/rules/known-through").contentType(MediaType.APPLICATION_JSON).content("{\"knownThrough\":\"2027-12\"}"))
            .andExpect(status().isOk());

        mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "20000000").param("month", "2027-03"))
            .andExpect(jsonPath("$.estimated").value(false))
            .andExpect(jsonPath("$.rulesFrom").value("2027-01"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isNotEqualTo(before));

        // Gross đã lưu không bị tính lại khi luật đổi.
        assertThat(contractRepository.findById(contractId).orElseThrow().getGrossAmount()).isEqualTo(grossBefore);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void invalidMilestonesRejected() throws Exception {
        String[] bad = {
            MILESTONE_2027.replace("\"2027-01\"", "\"01/2027\""),
            MILESTONE_2027.replace("\"personalDeduction\":16000000", "\"personalDeduction\":0"),
            MILESTONE_2027.replace("{\"upTo\":30000000,\"rate\":0.10}", "{\"upTo\":5000000,\"rate\":0.10}"),
            MILESTONE_2027.replace("{\"upTo\":null,\"rate\":0.35}", "{\"upTo\":90000000,\"rate\":0.35}"),
            MILESTONE_2027.replace("\"rate\":0.35", "\"rate\":1.5"),
        };
        for (String body : bad) {
            mockMvc
                .perform(post("/api/payroll/rules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.rulesinvalid"))
                .andExpect(jsonPath("$.detail").value(containsString("")));
        }
        mockMvc
            .perform(put("/api/payroll/rules/known-through").contentType(MediaType.APPLICATION_JSON).content("{\"knownThrough\":\"12/2027\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.rulesinvalid"));
    }

    @Test
    @WithMockUser(authorities = { "ROLE_HR", "ROLE_KE_TOAN" })
    void hrAndAccountantCannotAdd() throws Exception {
        mockMvc.perform(get("/api/payroll/rules")).andExpect(status().isOk());
        mockMvc.perform(post("/api/payroll/rules").contentType(MediaType.APPLICATION_JSON).content(MILESTONE_2027)).andExpect(status().isForbidden());
        mockMvc
            .perform(put("/api/payroll/rules/known-through").contentType(MediaType.APPLICATION_JSON).content("{\"knownThrough\":\"2027-12\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void boardCannotSee() throws Exception {
        mockMvc.perform(get("/api/payroll/rules")).andExpect(status().isForbidden());
    }

    /** Hợp đồng lương Net kỳ 2026, Gross được gợi ý và lưu lúc tạo. */
    private Long savedNetContract() {
        employeeId = tx.execute(s ->
            employeeRepository
                .save(
                    new Employee()
                        .code("LUAT-IT-1")
                        .fullName("Người Thử Luật")
                        .personType(PersonType.NHAN_VIEN)
                        .status(EmploymentStatus.DANG_LAM_VIEC)
                        .dependants(0)
                )
                .getId()
        );
        EmploymentContractDTO dto = new EmploymentContractDTO();
        dto.setType(ContractType.HDLD_KXDTH);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setSalary(20_000_000L);
        dto.setSalaryBasis(SalaryBasis.NET);
        dto.setStatus(ContractStatus.NHAP);
        EmployeeDTO e = new EmployeeDTO();
        e.setId(employeeId);
        dto.setEmployee(e);
        return contractService.save(dto).getId();
    }
}
