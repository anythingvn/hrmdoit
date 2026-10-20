package vn.doit.hrm.web.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.AllowanceRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;

/** Danh sách hợp đồng / điều chỉnh lương / phụ cấp của một nhân viên (trang chi tiết). */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class EmployeeRecordsResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private SalaryChangeRepository salaryChangeRepository;

    @Autowired
    private AllowanceRepository allowanceRepository;

    private Employee employee;

    private Employee other;

    private Employee newEmployee(String code) {
        return employeeRepository.saveAndFlush(
            new Employee()
                .code(code)
                .personType(PersonType.NHAN_VIEN)
                .fullName("Người " + code)
                .searchText(code.toLowerCase())
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
        );
    }

    private void contract(Employee e, LocalDate start) {
        contractRepository.saveAndFlush(
            new EmploymentContract().type(ContractType.HDTV).startDate(start).status(ContractStatus.DA_KY).salary(10_000_000L).employee(e)
        );
    }

    @BeforeEach
    void init() {
        employee = newEmployee("NV9201");
        other = newEmployee("NV9202");
        contract(employee, LocalDate.of(2026, 1, 1));
        contract(employee, LocalDate.of(2026, 3, 1));
        contract(other, LocalDate.of(2026, 2, 1));
        salaryChangeRepository.saveAndFlush(
            new SalaryChange().effectiveDate(LocalDate.of(2026, 4, 1)).amount(12_000_000L).basis(SalaryBasis.GROSS).employee(employee)
        );
        salaryChangeRepository.saveAndFlush(
            new SalaryChange().effectiveDate(LocalDate.of(2026, 4, 1)).amount(9_000_000L).basis(SalaryBasis.GROSS).employee(other)
        );
        allowanceRepository.saveAndFlush(
            new Allowance().name("Ăn trưa").amountPerMonth(730_000L).fromDate(LocalDate.of(2026, 1, 1)).employee(employee)
        );
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void contractsOfOneEmployeeNewestFirst() throws Exception {
        mockMvc
            .perform(get("/api/employees/{id}/contracts", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].startDate").value("2026-03-01"))
            .andExpect(jsonPath("$[1].startDate").value("2026-01-01"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void salaryChangesOfOneEmployee() throws Exception {
        mockMvc
            .perform(get("/api/employees/{id}/salary-changes", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].amount").value(12000000));
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void allowancesOfOneEmployee() throws Exception {
        mockMvc
            .perform(get("/api/employees/{id}/allowances", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Ăn trưa"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void unknownEmployeeIs404() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/contracts", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void otherRolesForbidden() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/allowances", employee.getId())).andExpect(status().isForbidden());
    }
}
