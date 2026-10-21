package vn.doit.hrm.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;

/** Ghi kết quả thử việc trên HĐ thử việc (HR, Quản trị) để dừng nhắc. */
@IntegrationTest
@AutoConfigureMockMvc
class ProbationResultIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    private Long employeeId;
    private Long probationId;
    private Long labourId;

    @BeforeEach
    void seed() {
        tx.executeWithoutResult(s -> {
            Employee e = employeeRepository.save(
                new Employee().code("TV-IT-1").fullName("Người Thử Việc").personType(PersonType.NHAN_VIEN).status(EmploymentStatus.DANG_LAM_VIEC).dependants(0)
            );
            employeeId = e.getId();
            probationId = contractRepository
                .save(new EmploymentContract().type(ContractType.HDTV).startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2026, 10, 31)).status(ContractStatus.DA_KY).employee(e))
                .getId();
            labourId = contractRepository
                .save(new EmploymentContract().type(ContractType.HDLD_KXDTH).startDate(LocalDate.of(2026, 11, 1)).status(ContractStatus.NHAP).employee(e))
                .getId();
        });
    }

    @AfterEach
    void clean() {
        tx.executeWithoutResult(s -> {
            contractRepository.findByEmployeeId(employeeId).forEach(contractRepository::delete);
            employeeRepository.deleteById(employeeId);
        });
    }

    private org.springframework.test.web.servlet.ResultActions patchContract(Long id, String fields, String role) throws Exception {
        return mockMvc.perform(
            patch("/api/employment-contracts/{id}", id)
                .with(user("u").authorities(() -> role))
                .contentType("application/merge-patch+json")
                .content("{\"id\":" + id + "," + fields + "}")
        );
    }

    @Test
    void hrRecordsProbationResultWithDefaultDate() throws Exception {
        String today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        patchContract(probationId, "\"probationResult\":\"DAT\"", "ROLE_HR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.probationResult").value("DAT"))
            .andExpect(jsonPath("$.probationResultDate").value(today));
        patchContract(probationId, "\"probationResult\":\"KHONG_DAT\",\"probationResultDate\":\"2026-10-30\"", "ROLE_HR")
            .andExpect(jsonPath("$.probationResult").value("KHONG_DAT"))
            .andExpect(jsonPath("$.probationResultDate").value("2026-10-30"));
    }

    @Test
    void onlyProbationContracts() throws Exception {
        patchContract(labourId, "\"probationResult\":\"DAT\"", "ROLE_HR")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.probationresultnothdtv"));
    }

    @Test
    void accountantCannotRecord() throws Exception {
        patchContract(probationId, "\"probationResult\":\"DAT\"", "ROLE_KE_TOAN").andExpect(status().isForbidden());
    }
}
