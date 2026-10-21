package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;

/**
 * Mã nhân viên tự gán, đánh số cộng dồn: DOIT + 2 số năm vào làm + số thứ tự lớn nhất từng dùng + 1;
 * thực tập TT + năm + dãy riêng; mã HĐ dịch vụ và mã khác dạng không tính.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "ROLE_HR")
class EmployeeCodeIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    private void persist(String code, PersonType type, EmploymentStatus status) {
        employeeRepository.saveAndFlush(
            new Employee().code(code).fullName("Người " + code).searchText(code.toLowerCase()).personType(type).status(status).dependants(0)
        );
    }

    @BeforeEach
    void seed() {
        persist("DOIT2401", PersonType.NHAN_VIEN, EmploymentStatus.DANG_LAM_VIEC);
        // Người đã nghỉ giữ số lớn nhất: số này không được cấp lại.
        persist("DOIT2590", PersonType.NHAN_VIEN, EmploymentStatus.DA_NGHI);
        persist("TT2680", PersonType.THUC_TAP, EmploymentStatus.DANG_LAM_VIEC);
        persist("NV0999", PersonType.NHAN_VIEN, EmploymentStatus.DANG_LAM_VIEC);
    }

    private String create(String json) throws Exception {
        return JsonPath.read(
            mockMvc
                .perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.code"
        );
    }

    @Test
    void suggestsNextCumulativeCode() throws Exception {
        mockMvc
            .perform(get("/api/employees/next-code").param("personType", "NHAN_VIEN").param("joinDate", "2026-03-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("DOIT2691"));
        mockMvc
            .perform(get("/api/employees/next-code").param("personType", "THUC_TAP").param("joinDate", "2027-01-10"))
            .andExpect(jsonPath("$.code").value("TT2781"));
        mockMvc.perform(get("/api/employees/next-code").param("personType", "DICH_VU")).andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void blankCodeAssignedOnSaveAndNeverRepeated() throws Exception {
        String first = create(
            "{\"fullName\":\"Nguyễn Văn Mới\",\"personType\":\"NHAN_VIEN\",\"status\":\"DANG_LAM_VIEC\",\"joinDate\":\"2026-02-02\",\"dependants\":0}"
        );
        String second = create("{\"fullName\":\"Trần Thị Mới\",\"personType\":\"NHAN_VIEN\",\"joinDate\":\"2026-02-03\",\"dependants\":0}");
        assertThat(first).isEqualTo("DOIT2691");
        assertThat(second).isEqualTo("DOIT2692");
        String intern = create("{\"fullName\":\"Lê Học Việc\",\"personType\":\"THUC_TAP\",\"joinDate\":\"2026-09-01\",\"dependants\":0}");
        assertThat(intern).isEqualTo("TT2681");
        String typed = create("{\"fullName\":\"Gõ Tay\",\"personType\":\"NHAN_VIEN\",\"code\":\"doit2699\",\"dependants\":0}");
        assertThat(typed).isEqualTo("DOIT2699");
        assertThat(employeeRepository.findAll().stream().filter(e -> "TT2681".equals(e.getCode())).findFirst().orElseThrow().getPersonType())
            .isEqualTo(PersonType.THUC_TAP);
    }

    @Test
    @WithMockUser(authorities = "ROLE_NHAN_VIEN")
    void employeeRoleCannotAskNextCode() throws Exception {
        mockMvc.perform(get("/api/employees/next-code").param("personType", "NHAN_VIEN")).andExpect(status().isForbidden());
    }
}
