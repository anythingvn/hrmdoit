package vn.doit.hrm.web.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;

/**
 * Nhật ký thay đổi hồ sơ (Envers). Không dùng @Transactional vì revision chỉ ghi khi commit.
 */
@IntegrationTest
@AutoConfigureMockMvc
class EmployeeHistoryResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private WorkAssignmentRepository workAssignmentRepository;

    @Autowired
    private TransactionTemplate tx;

    private Long employeeId;

    private Long createEmployee() {
        return tx.execute(s ->
            employeeRepository
                .save(
                    new Employee()
                        .code("NV9100")
                        .personType(PersonType.NHAN_VIEN)
                        .fullName("Lê Thị Kiểm")
                        .searchText("nv9100 le thi kiem")
                        .dependants(0)
                        .status(EmploymentStatus.DANG_LAM_VIEC)
                        .position("Tester")
                        .bankAccount("111222333")
                )
                .getId()
        );
    }

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(s -> {
            workAssignmentRepository.deleteAll(workAssignmentRepository.findAll().stream().filter(w -> w.getEmployee().getId().equals(employeeId)).toList());
            if (employeeId != null) employeeRepository.deleteById(employeeId);
        });
    }

    @Test
    @WithMockUser(username = "hr-test", authorities = "ROLE_HR")
    void historyShowsCreateAndUpdateWithDiff() throws Exception {
        employeeId = createEmployee();
        tx.executeWithoutResult(s -> employeeRepository.findById(employeeId).orElseThrow().setPosition("BA"));

        mockMvc
            .perform(get("/api/employees/{id}/history", employeeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].action").value("SỬA"))
            .andExpect(jsonPath("$[0].entity").value("Employee"))
            .andExpect(jsonPath("$[0].login").value("hr-test"))
            .andExpect(jsonPath("$[0].at").exists())
            .andExpect(jsonPath("$[0].changes[?(@.field=='position')].before").value(hasItem("Tester")))
            .andExpect(jsonPath("$[0].changes[?(@.field=='position')].after").value(hasItem("BA")))
            .andExpect(jsonPath("$[0].changes[?(@.field=='searchText')]").isEmpty())
            .andExpect(jsonPath("$[1].action").value("TẠO"));
    }

    @Test
    @WithMockUser(username = "hr-test", authorities = "ROLE_HR")
    void sensitiveFieldsAreMasked() throws Exception {
        employeeId = createEmployee();
        tx.executeWithoutResult(s -> employeeRepository.findById(employeeId).orElseThrow().setBankAccount("999888777"));

        mockMvc
            .perform(get("/api/employees/{id}/history", employeeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].changes[?(@.field=='bankAccount')].before").value(hasItem("***")))
            .andExpect(jsonPath("$[0].changes[?(@.field=='bankAccount')].after").value(hasItem("***")))
            .andExpect(jsonPath("$[1].changes[?(@.field=='bankAccount')].after").value(hasItem("***")));
    }

    @Test
    @WithMockUser(username = "hr-test", authorities = "ROLE_HR")
    void historyIncludesChildRecords() throws Exception {
        employeeId = createEmployee();
        tx.executeWithoutResult(s ->
            workAssignmentRepository.save(
                new WorkAssignment()
                    .type(WorkLocationType.VAN_PHONG)
                    .unit("Chi nhánh A")
                    .fromDate(LocalDate.of(2026, 1, 1))
                    .employee(employeeRepository.findById(employeeId).orElseThrow())
            )
        );

        mockMvc
            .perform(get("/api/employees/{id}/history", employeeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].entity").value("WorkAssignment"))
            .andExpect(jsonPath("$[0].action").value("TẠO"))
            .andExpect(jsonPath("$[0].changes[?(@.field=='unit')].after").value(hasItem("Chi nhánh A")))
            .andExpect(jsonPath("$[0].changes[?(@.field=='unit')].before").value(hasItem(nullValue())));
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void bgdForbidden() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/history", 1L)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantForbidden() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/history", 1L)).andExpect(status().isForbidden());
    }
}
