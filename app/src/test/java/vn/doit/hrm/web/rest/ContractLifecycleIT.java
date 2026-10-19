package vn.doit.hrm.web.rest;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;

/** Vòng đời hợp đồng: gợi ý Gross, ngày kết thúc mặc định, lương hiện tại. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "ROLE_HR")
@Import(ContractLifecycleIT.FixedClockConfig.class)
class ContractLifecycleIT {

    /** Chỉ lớp IT này dùng đồng hồ cố định (có context riêng). */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee;

    @BeforeEach
    void init() {
        employee = employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9100")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Lê Văn Hợp")
                .searchText("nv9100 le van hop")
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
        );
    }

    private String contractJson(String gross) {
        return (
            "{\"type\":\"HDLD_XDTH\",\"termMonths\":12,\"startDate\":\"2026-10-01\",\"salary\":26215000,\"salaryBasis\":\"NET\"," +
            (gross == null ? "" : "\"grossAmount\":" + gross + ",") +
            "\"status\":\"DA_KY\",\"employee\":{\"id\":" + employee.getId() + "}}"
        );
    }

    @Test
    void netContractGetsGrossSuggestion() throws Exception {
        mockMvc
            .perform(post("/api/employment-contracts").contentType(MediaType.APPLICATION_JSON).content(contractJson(null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.grossAmount").value(30000000))
            .andExpect(jsonPath("$.endDate").value("2027-09-30"));
    }

    @Test
    void userGrossIsKept() throws Exception {
        mockMvc
            .perform(post("/api/employment-contracts").contentType(MediaType.APPLICATION_JSON).content(contractJson("31000000")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.grossAmount").value(31000000));
    }

    @Test
    void badTermRejected() throws Exception {
        mockMvc
            .perform(
                post("/api/employment-contracts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contractJson(null).replace("\"termMonths\":12", "\"termMonths\":18"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.badterm"));
    }

    @Test
    void employeeDetailShowsCurrentSalary() throws Exception {
        mockMvc
            .perform(post("/api/employment-contracts").contentType(MediaType.APPLICATION_JSON).content(contractJson(null)))
            .andExpect(status().isCreated());
        mockMvc
            .perform(get("/api/employees/{id}", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentSalary.amount").value(26215000))
            .andExpect(jsonPath("$.currentSalary.basis").value("NET"))
            .andExpect(jsonPath("$.currentSalary.grossAmount").value(30000000))
            .andExpect(jsonPath("$.currentContractType").value("HDLD_XDTH"));
    }

    @Test
    void salaryToolsAndAdvice() throws Exception {
        mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "26215000").param("dependants", "0").param("month", "2026-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gross").value(30000000));
        mockMvc.perform(get("/api/salary-tools/contract-terms")).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(12));
        mockMvc
            .perform(get("/api/employees/{id}/contract-advice", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextMustBeIndefinite", is(false)));
    }
}
