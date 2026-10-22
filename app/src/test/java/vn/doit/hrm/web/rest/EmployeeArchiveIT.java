package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.repository.AllowanceRepository;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.repository.UserRepository;

/**
 * Lưu trữ, khôi phục, vùng lưu trữ và chặn sửa hồ sơ đã lưu trữ. Không cần đồng hồ cố định: dùng chung ngữ cảnh Spring mặc định để
 * không thêm một ngữ cảnh (container cơ sở dữ liệu dùng chung bị dừng khi bộ nhớ đệm ngữ cảnh đầy).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "ROLE_HR")
class EmployeeArchiveIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private SalaryChangeRepository salaryChangeRepository;

    @Autowired
    private AllowanceRepository allowanceRepository;

    @Autowired
    private UserRepository userRepository;

    private int seq;

    @BeforeEach
    void init() {
        seq = 0;
    }

    private User account(boolean activated) {
        User u = new User();
        u.setLogin("lt-acc-" + ++seq + "-" + (System.nanoTime() % 100000));
        u.setActivated(activated);
        u.setLangKey("vi");
        u.setPassword("$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234");
        return userRepository.saveAndFlush(u);
    }

    private Employee employee(String code, String name, EmploymentStatus status, User user) {
        Employee e = new Employee()
            .code(code)
            .personType(PersonType.NHAN_VIEN)
            .fullName(name)
            .searchText(vn.doit.hrm.service.VietnameseText.fold(code + " " + name))
            .dependants(0)
            .phone("0900000999")
            .idNumber("0123456789" + code.substring(code.length() - 2))
            .status(status)
            .joinDate(LocalDate.of(2025, 1, 1))
            .user(user);
        if (status == EmploymentStatus.DA_NGHI) {
            e.setLastWorkingDay(LocalDate.of(2026, 9, 30));
            e.setTerminationDate(LocalDate.of(2026, 10, 1));
            e.setTerminationType(TerminationType.TU_NGHI);
            e.setTerminationReason("Chuyển việc");
        }
        return employeeRepository.saveAndFlush(e);
    }

    /** Hồ sơ đã nghỉ có HĐ, thay đổi lương, phụ cấp và tài khoản đã khóa. */
    private Employee terminatedWithData(String code, String name) {
        Employee e = employee(code, name, EmploymentStatus.DA_NGHI, account(false));
        contractRepository.saveAndFlush(
            new EmploymentContract()
                .contractNo("HD" + code)
                .type(ContractType.HDLD_XDTH)
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .position("Kỹ sư")
                .salary(20000000L)
                .salaryBasis(SalaryBasis.GROSS)
                .status(ContractStatus.DA_KET_THUC)
                .employee(e)
        );
        salaryChangeRepository.saveAndFlush(
            new SalaryChange()
                .effectiveDate(LocalDate.of(2026, 1, 1))
                .amount(22000000L)
                .basis(SalaryBasis.GROSS)
                .employee(e)
        );
        allowanceRepository.saveAndFlush(
            new Allowance()
                .name("Ăn trưa")
                .amountPerMonth(730000L)
                .fromDate(LocalDate.of(2025, 1, 1))
                .employee(e)
        );
        return e;
    }

    private ResultActions archive(Employee e, String note) throws Exception {
        String body = note == null ? "{}" : "{\"note\":\"" + note + "\"}";
        return mockMvc.perform(post("/api/employees/{id}/archive", e.getId()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions restore(Employee e) throws Exception {
        return mockMvc.perform(post("/api/employees/{id}/restore", e.getId()));
    }

    private JsonNode profile(Employee e) throws Exception {
        String res = mockMvc
            .perform(get("/api/employees/{id}", e.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return om.readTree(res);
    }

    private String childCounts(Employee e) {
        return (
            contractRepository.findByEmployeeId(e.getId()).size() +
            "/" +
            salaryChangeRepository.findByEmployeeId(e.getId()).size() +
            "/" +
            allowanceRepository.findByEmployeeId(e.getId()).size()
        );
    }

    @Test
    void archiveTerminated() throws Exception {
        Employee e = terminatedWithData("NV9101", "Trần Văn Nghỉ");
        String before = childCounts(e);

        archive(e, "Nghỉ từ tháng 9").andExpect(status().isOk());

        JsonNode p = profile(e);
        assertThat(p.get("archivedAt").asText()).isNotBlank();
        assertThat(p.get("archivedBy").asText()).isEqualTo("user");
        Employee reloaded = employeeRepository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getArchiveNote()).isEqualTo("Nghỉ từ tháng 9");
        assertThat(reloaded.getStatus()).isEqualTo(EmploymentStatus.DA_NGHI);
        assertThat(childCounts(e)).isEqualTo(before);
        assertThat(userRepository.findById(e.getUser().getId()).orElseThrow().isActivated()).isFalse();
    }

    @Test
    void archiveActiveWithDataRejected() throws Exception {
        Employee e = employee("NV9102", "Lê Đang Làm", EmploymentStatus.DANG_LAM_VIEC, null);
        contractRepository.saveAndFlush(
            new EmploymentContract()
                .contractNo("HDNV9102")
                .type(ContractType.HDLD_XDTH)
                .startDate(LocalDate.of(2025, 1, 1))
                .salary(1L)
                .salaryBasis(SalaryBasis.GROSS)
                .status(ContractStatus.DA_KY)
                .employee(e)
        );

        archive(e, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.archivenotterminated"));

        assertThat(employeeRepository.findById(e.getId()).orElseThrow().getArchivedAt()).isNull();
    }

    @Test
    void archiveMistakenProfile() throws Exception {
        User acc = account(true);
        Employee e = employee("NV9103", "Phạm Tạo Nhầm", EmploymentStatus.DANG_LAM_VIEC, acc);

        archive(e, "Tạo nhầm").andExpect(status().isOk());

        Employee reloaded = employeeRepository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getArchivedAt()).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
        assertThat(userRepository.findById(acc.getId()).orElseThrow().isActivated()).isFalse();
    }

    @Test
    void archiveTwiceRejected() throws Exception {
        Employee e = terminatedWithData("NV9104", "Hồ Hai Lần");
        archive(e, null).andExpect(status().isOk());

        archive(e, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.alreadyarchived"));
    }

    @Test
    void archiveReportsReferences() throws Exception {
        Employee boss = terminatedWithData("NV9105", "Đỗ Quản Lý");
        Employee a = employee("NV9106", "Nhân Viên A", EmploymentStatus.DANG_LAM_VIEC, null);
        Employee b = employee("NV9107", "Nhân Viên B", EmploymentStatus.DANG_LAM_VIEC, null);
        a.setManager(boss);
        b.setManager(boss);
        employeeRepository.saveAll(java.util.List.of(a, b));
        Department d = departmentRepository.saveAndFlush(new Department().name("Phòng thử lưu trữ").active(true));
        d.setHead(boss);
        departmentRepository.saveAndFlush(d);

        archive(boss, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.managedEmployees", hasSize(2)))
            .andExpect(jsonPath("$.headedDepartments", hasSize(1)))
            .andExpect(jsonPath("$.headedDepartments[0].name").value("Phòng thử lưu trữ"));
    }

    @Test
    void restoreReturnsIdenticalProfile() throws Exception {
        Employee e = terminatedWithData("NV9108", "Vũ Khôi Phục");
        ObjectNode before = (ObjectNode) profile(e);

        archive(e, "Ghi chú").andExpect(status().isOk());
        restore(e).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_NGHI"));

        ObjectNode after = (ObjectNode) profile(e);
        for (String audit : new String[] { "createdBy", "createdDate", "lastModifiedBy", "lastModifiedDate", "archivedAt", "archivedBy" }) {
            before.remove(audit);
            after.remove(audit);
        }
        assertThat(after).isEqualTo(before);
        Employee reloaded = employeeRepository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getArchivedAt()).isNull();
        assertThat(reloaded.getArchivedBy()).isNull();
        assertThat(reloaded.getArchiveNote()).isNull();
        assertThat(userRepository.findById(e.getUser().getId()).orElseThrow().isActivated()).isFalse();
    }

    @Test
    void archivedListSearch() throws Exception {
        Employee e = terminatedWithData("NV9109", "Nguyễn Thị Lưu Trữ");
        Employee notArchived = terminatedWithData("NV9110", "Nguyễn Thị Chưa Lưu");
        archive(e, null).andExpect(status().isOk());

        mockMvc
            .perform(get("/api/employees/archived").param("q", "luu tru"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].code").value("NV9109"))
            .andExpect(jsonPath("$[0].fullName").value("Nguyễn Thị Lưu Trữ"))
            .andExpect(jsonPath("$[0].archivedBy").value("user"));
        mockMvc
            .perform(get("/api/employees/archived").param("q", "  " + e.getIdNumber() + " "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].code").value("NV9109"));
        mockMvc
            .perform(get("/api/employees/archived").param("q", "nguyen thi"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
        mockMvc
            .perform(get("/api/employees/archived").param("q", notArchived.getCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void archivedProfileIsReadOnly() throws Exception {
        Employee e = terminatedWithData("NV9111", "Bùi Chỉ Xem");
        archive(e, null).andExpect(status().isOk());
        ObjectNode dto = (ObjectNode) profile(e);
        dto.put("phone", "0911111111");
        String archivedError = "error.employeearchived";

        mockMvc
            .perform(put("/api/employees/{id}", e.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(archivedError));
        mockMvc
            .perform(
                post("/api/employment-contracts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"type\":\"HDLD_XDTH\",\"startDate\":\"2026-10-01\",\"status\":\"NHAP\",\"employee\":{\"id\":" + e.getId() + "}}"
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(archivedError));
        mockMvc
            .perform(
                post("/api/allowances")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Xăng xe\",\"amountPerMonth\":500000,\"fromDate\":\"2026-10-01\",\"employee\":{\"id\":" +
                            e.getId() +
                            "}}"
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(archivedError));
        mockMvc
            .perform(
                multipart("/api/employees/{id}/documents", e.getId()).file(
                    new MockMultipartFile("file", "scan.pdf", "application/pdf", "%PDF-1.4\n".getBytes())
                )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(archivedError));
        mockMvc
            .perform(
                post("/api/employees/{id}/terminate", e.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"lastWorkingDay\":\"2026-10-01\",\"terminationType\":\"TU_NGHI\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(archivedError));
        mockMvc
            .perform(post("/api/employees/{id}/termination/undo", e.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(archivedError));
        mockMvc
            .perform(
                post("/api/leave/adjustments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"employeeId\":" + e.getId() + ",\"pool\":\"PHEP_NAM\",\"amount\":1,\"note\":\"Thử\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(archivedError));

        mockMvc.perform(get("/api/employees/{id}", e.getId())).andExpect(status().isOk());
    }

    private static RequestPostProcessor role(String authority) {
        return user("other-" + authority).authorities(() -> authority);
    }

    @Test
    void forbiddenForOtherRoles() throws Exception {
        Employee e = terminatedWithData("NV9112", "Cấm Quyền");
        for (String authority : new String[] { "ROLE_KE_TOAN", "ROLE_NHAN_VIEN" }) {
            mockMvc
                .perform(
                    post("/api/employees/{id}/archive", e.getId())
                        .with(role(authority))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                )
                .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/employees/{id}/restore", e.getId()).with(role(authority))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/employees/archived").with(role(authority))).andExpect(status().isForbidden());
        }
        assertThat(employeeRepository.findById(e.getId()).orElseThrow().getArchivedAt()).isNull();
    }
}
