package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

/**
 * Ràng buộc dữ liệu (KH1b Task 5): số tiền không âm, không xóa cứng HĐ đã ký, sửa không chuyển bản ghi sang nhân viên khác,
 * kỳ lương sai định dạng trả 400.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "ROLE_HR")
class DataIntegrityRulesIT {

    private static final String MERGE_PATCH = "application/merge-patch+json";

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

    private Employee an;
    private Employee binh;

    private Employee employee(String code, String name) {
        return employeeRepository.saveAndFlush(
            new Employee()
                .code(code)
                .personType(PersonType.NHAN_VIEN)
                .fullName(name)
                .searchText(code.toLowerCase())
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
        );
    }

    @BeforeEach
    void init() {
        an = employee("NV9501", "Nguyễn An");
        binh = employee("NV9502", "Trần Bình");
    }

    private EmploymentContract contract(Employee e, ContractStatus status) {
        EmploymentContract c = new EmploymentContract();
        c.setType(ContractType.HDLD_KXDTH);
        c.setStartDate(LocalDate.of(2026, 1, 1));
        c.setStatus(status);
        c.setSalary(20_000_000L);
        c.setSalaryBasis(SalaryBasis.GROSS);
        c.setEmployee(e);
        return contractRepository.saveAndFlush(c);
    }

    private SalaryChange salaryChange(Employee e) {
        SalaryChange s = new SalaryChange();
        s.setEffectiveDate(LocalDate.of(2026, 3, 1));
        s.setAmount(22_000_000L);
        s.setBasis(SalaryBasis.GROSS);
        s.setEmployee(e);
        return salaryChangeRepository.saveAndFlush(s);
    }

    private Allowance allowance(Employee e) {
        Allowance a = new Allowance();
        a.setName("Ăn trưa");
        a.setAmountPerMonth(730_000L);
        a.setFromDate(LocalDate.of(2026, 1, 1));
        a.setEmployee(e);
        return allowanceRepository.saveAndFlush(a);
    }

    // ---- Số tiền không âm ----

    @Test
    void negativeContractSalaryRejected() throws Exception {
        mockMvc
            .perform(
                post("/api/employment-contracts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"type\":\"HDLD_KXDTH\",\"startDate\":\"2026-01-01\",\"status\":\"NHAP\",\"salary\":-1,\"salaryBasis\":\"GROSS\"," +
                            "\"employee\":{\"id\":" +
                            an.getId() +
                            "}}"
                    )
            )
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(
                post("/api/employment-contracts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"type\":\"HDLD_KXDTH\",\"startDate\":\"2026-01-01\",\"status\":\"NHAP\",\"salary\":1000,\"salaryBasis\":\"NET\"," +
                            "\"grossAmount\":-5,\"employee\":{\"id\":" +
                            an.getId() +
                            "}}"
                    )
            )
            .andExpect(status().isBadRequest());
        assertThat(contractRepository.findByEmployeeId(an.getId())).isEmpty();
    }

    @Test
    void negativeContractSalaryRejectedOnPatch() throws Exception {
        EmploymentContract c = contract(an, ContractStatus.NHAP);
        mockMvc
            .perform(
                patch("/api/employment-contracts/{id}", c.getId())
                    .contentType(MERGE_PATCH)
                    .content("{\"id\":" + c.getId() + ",\"salary\":-100}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.negativeamount"));
        assertThat(contractRepository.findById(c.getId()).orElseThrow().getSalary()).isEqualTo(20_000_000L);
    }

    @Test
    void negativeSalaryChangeRejected() throws Exception {
        mockMvc
            .perform(
                post("/api/salary-changes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"effectiveDate\":\"2026-03-01\",\"amount\":-1,\"basis\":\"GROSS\",\"employee\":{\"id\":" + an.getId() + "}}"
                    )
            )
            .andExpect(status().isBadRequest());
        SalaryChange s = salaryChange(an);
        mockMvc
            .perform(
                patch("/api/salary-changes/{id}", s.getId())
                    .contentType(MERGE_PATCH)
                    .content("{\"id\":" + s.getId() + ",\"grossAmount\":-1}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.negativeamount"));
    }

    @Test
    void negativeAllowanceRejected() throws Exception {
        mockMvc
            .perform(
                post("/api/allowances")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Xăng xe\",\"amountPerMonth\":-500000,\"fromDate\":\"2026-01-01\",\"employee\":{\"id\":" +
                            an.getId() +
                            "}}"
                    )
            )
            .andExpect(status().isBadRequest());
        Allowance a = allowance(an);
        mockMvc
            .perform(
                patch("/api/allowances/{id}", a.getId())
                    .contentType(MERGE_PATCH)
                    .content("{\"id\":" + a.getId() + ",\"amountPerMonth\":-1}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.negativeamount"));
    }

    @Test
    void negativeSocialInsuranceSalaryRejected() throws Exception {
        mockMvc
            .perform(
                post("/api/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"NV9503\",\"personType\":\"NHAN_VIEN\",\"fullName\":\"Lê Cường\",\"socialInsuranceSalary\":-1}")
            )
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(
                patch("/api/employees/{id}", an.getId())
                    .contentType(MERGE_PATCH)
                    .content("{\"id\":" + an.getId() + ",\"socialInsuranceSalary\":-1}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.negativeamount"));
    }

    @Test
    void zeroAmountAllowed() throws Exception {
        mockMvc
            .perform(
                post("/api/allowances")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Tạm dừng\",\"amountPerMonth\":0,\"fromDate\":\"2026-01-01\",\"employee\":{\"id\":" + an.getId() + "}}"
                    )
            )
            .andExpect(status().isCreated());
    }

    // ---- Xóa HĐ: chỉ bản nháp ----

    @Test
    void deleteDraftContractAllowed() throws Exception {
        EmploymentContract c = contract(an, ContractStatus.NHAP);
        mockMvc.perform(delete("/api/employment-contracts/{id}", c.getId())).andExpect(status().isNoContent());
        assertThat(contractRepository.findById(c.getId())).isEmpty();
    }

    @Test
    void deleteSignedOrEndedContractRejected() throws Exception {
        for (ContractStatus status : new ContractStatus[] {
            ContractStatus.DA_KY,
            ContractStatus.DA_KET_THUC,
            ContractStatus.DA_THANH_LY,
        }) {
            EmploymentContract c = contract(an, status);
            mockMvc
                .perform(delete("/api/employment-contracts/{id}", c.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.cannotdeletesigned"));
            assertThat(contractRepository.findById(c.getId())).isPresent();
        }
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminAlsoCannotDeleteSignedContract() throws Exception {
        EmploymentContract c = contract(an, ContractStatus.DA_KY);
        mockMvc.perform(delete("/api/employment-contracts/{id}", c.getId())).andExpect(status().isBadRequest());
    }

    // ---- Không chuyển bản ghi sang nhân viên khác ----

    @Test
    void contractCannotMoveToAnotherEmployee() throws Exception {
        EmploymentContract c = contract(an, ContractStatus.NHAP);
        String put =
            "{\"id\":" +
            c.getId() +
            ",\"type\":\"HDLD_KXDTH\",\"startDate\":\"2026-01-01\",\"status\":\"NHAP\",\"salary\":20000000,\"salaryBasis\":\"GROSS\"," +
            "\"employee\":{\"id\":" +
            binh.getId() +
            "}}";
        mockMvc
            .perform(put("/api/employment-contracts/{id}", c.getId()).contentType(MediaType.APPLICATION_JSON).content(put))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.cannotmoverecord"));
        mockMvc
            .perform(
                patch("/api/employment-contracts/{id}", c.getId())
                    .contentType(MERGE_PATCH)
                    .content("{\"id\":" + c.getId() + ",\"employee\":{\"id\":" + binh.getId() + "}}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.cannotmoverecord"));
        assertThat(contractRepository.findById(c.getId()).orElseThrow().getEmployee().getId()).isEqualTo(an.getId());

        // Sửa bình thường (cùng nhân viên) vẫn được.
        mockMvc
            .perform(
                put("/api/employment-contracts/{id}", c.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(put.replace("\"id\":" + binh.getId(), "\"id\":" + an.getId()).replace("20000000", "21000000"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.salary").value(21000000));
    }

    @Test
    void salaryChangeCannotMoveToAnotherEmployee() throws Exception {
        SalaryChange s = salaryChange(an);
        mockMvc
            .perform(
                put("/api/salary-changes/{id}", s.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"id\":" +
                            s.getId() +
                            ",\"effectiveDate\":\"2026-03-01\",\"amount\":22000000,\"basis\":\"GROSS\",\"employee\":{\"id\":" +
                            binh.getId() +
                            "}}"
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.cannotmoverecord"));
        mockMvc
            .perform(
                patch("/api/salary-changes/{id}", s.getId())
                    .contentType(MERGE_PATCH)
                    .content("{\"id\":" + s.getId() + ",\"employee\":{\"id\":" + binh.getId() + "}}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.cannotmoverecord"));
        assertThat(salaryChangeRepository.findById(s.getId()).orElseThrow().getEmployee().getId()).isEqualTo(an.getId());
    }

    @Test
    void allowanceCannotMoveToAnotherEmployee() throws Exception {
        Allowance a = allowance(an);
        mockMvc
            .perform(
                put("/api/allowances/{id}", a.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"id\":" +
                            a.getId() +
                            ",\"name\":\"Ăn trưa\",\"amountPerMonth\":730000,\"fromDate\":\"2026-01-01\",\"employee\":{\"id\":" +
                            binh.getId() +
                            "}}"
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.cannotmoverecord"));
        mockMvc
            .perform(
                patch("/api/allowances/{id}", a.getId())
                    .contentType(MERGE_PATCH)
                    .content("{\"id\":" + a.getId() + ",\"employee\":{\"id\":" + binh.getId() + "}}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.cannotmoverecord"));
        // PATCH không gửi employee: giữ nguyên, sửa được.
        mockMvc
            .perform(
                patch("/api/allowances/{id}", a.getId())
                    .contentType(MERGE_PATCH)
                    .content("{\"id\":" + a.getId() + ",\"amountPerMonth\":800000}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amountPerMonth").value(800000));
        assertThat(allowanceRepository.findById(a.getId()).orElseThrow().getEmployee().getId()).isEqualTo(an.getId());
    }

    // ---- Kỳ lương sai định dạng ----

    @Test
    void invalidMonthIsBadRequest() throws Exception {
        mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "20000000").param("month", "10/2026"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalidmonth"));
        mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "20000000").param("month", "2026-13"))
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "-1").param("month", "2026-10"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.negativeamount"));
        mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "20000000").param("month", "2026-10"))
            .andExpect(status().isOk());
    }
}
