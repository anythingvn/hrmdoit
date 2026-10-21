package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.payroll.NetGrossConverter;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.InsuranceExemption;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.service.payroll.RuleBookService;

/** Người không thuộc diện đóng BHXH bắt buộc: lưu lý do trên hồ sơ, Gross gợi ý từ Net không cộng BH. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "ROLE_HR")
class InsuranceExemptionIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private RuleBookService ruleBookService;

    private Employee employee;

    @BeforeEach
    void seed() {
        employee = employeeRepository.saveAndFlush(
            new Employee()
                .code("DOIT2790")
                .fullName("Nguyễn Hưu Trí")
                .searchText("doit2790 nguyen huu tri")
                .personType(PersonType.NHAN_VIEN)
                .status(EmploymentStatus.DANG_LAM_VIEC)
                .dependants(0)
        );
    }

    private BigDecimal expectedGross(long net, boolean insured) {
        return NetGrossConverter.suggestGross(BigDecimal.valueOf(net), 0, YearMonth.of(2026, 6), ruleBookService.current(), insured).orElseThrow();
    }

    @Test
    void exemptionSavedAndGrossWithoutInsurance() throws Exception {
        mockMvc
            .perform(
                patch("/api/employees/{id}", employee.getId())
                    .contentType("application/merge-patch+json")
                    .content(
                        "{\"id\":" + employee.getId() + ",\"insuranceExemption\":\"LUONG_HUU\",\"insuranceExemptionNote\":\"Đã nghỉ hưu từ 2024\"}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.insuranceExemption").value("LUONG_HUU"))
            .andExpect(jsonPath("$.insuranceExemptionNote").value("Đã nghỉ hưu từ 2024"));
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getInsuranceExemption()).isEqualTo(InsuranceExemption.LUONG_HUU);

        String body = mockMvc
            .perform(
                post("/api/employment-contracts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"type\":\"HDLD_XDTH\",\"termMonths\":12,\"startDate\":\"2026-06-01\",\"salary\":18000000,\"salaryBasis\":\"NET\"," +
                        "\"status\":\"NHAP\",\"employee\":{\"id\":" +
                        employee.getId() +
                        "}}"
                    )
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long gross = ((Number) JsonPath.read(body, "$.grossAmount")).longValue();
        assertThat(gross).isEqualTo(expectedGross(18_000_000, false).longValueExact());
        assertThat(gross).isLessThan(expectedGross(18_000_000, true).longValueExact());

        mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "18000000").param("month", "2026-06").param("insured", "false"))
            .andExpect(jsonPath("$.gross").value(expectedGross(18_000_000, false).longValueExact()));
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantCannotChangeExemption() throws Exception {
        mockMvc
            .perform(
                patch("/api/employees/{id}", employee.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + employee.getId() + ",\"insuranceExemption\":\"KHAC\"}")
            )
            .andExpect(status().isForbidden());
    }
}
