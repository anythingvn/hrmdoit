package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.AttendancePunchStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.WorkSiteService;

/** Địa điểm chấm công trên work_site: toạ độ, bán kính, dải IP văn phòng. */
@IntegrationTest
@AutoConfigureMockMvc
class WorkSiteLocationIT {

    private static final RequestPostProcessor HR = user("hr-wsl").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor KE_TOAN = user("kt-wsl").authorities(() -> "ROLE_KE_TOAN");
    private static final String URL = "/api/work-sites";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkSiteService workSiteService;

    @Autowired
    private AttendancePunchRepository punchRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private ObjectNode body(Long id) {
        ObjectNode n = om.createObjectNode();
        if (id != null) n.put("id", id);
        n.put("name", "WSL-" + UUID.randomUUID());
        n.put("type", "VAN_PHONG");
        n.put("active", true);
        return n;
    }

    private long create(ObjectNode n) throws Exception {
        String res = mockMvc
            .perform(post(URL).with(HR).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(n)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return om.readTree(res).get("id").asLong();
    }

    private void putExpectError(ObjectNode n, long id, String key) throws Exception {
        n.put("id", id);
        mockMvc
            .perform(put(URL + "/" + id).with(HR).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(n)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error." + key));
    }

    @Test
    void saveLocation() throws Exception {
        long id = create(body(null));
        ObjectNode n = body(id);
        n.put("latitude", 21.028511);
        n.put("longitude", 105.804817);
        n.put("radiusM", 150);
        n.put("officeIps", "113.190.1.10\n10.0.0.0/8");
        mockMvc
            .perform(put(URL + "/" + id).with(HR).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(n)))
            .andExpect(status().isOk());
        String res = mockMvc.perform(get(URL + "/" + id).with(HR)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode j = om.readTree(res);
        assertThat(j.get("latitude").decimalValue()).isEqualByComparingTo("21.028511");
        assertThat(j.get("longitude").decimalValue()).isEqualByComparingTo("105.804817");
        assertThat(j.get("radiusM").asInt()).isEqualTo(150);
        assertThat(j.get("officeIps").asText()).isEqualTo("113.190.1.10\n10.0.0.0/8");
    }

    @Test
    void patchNameOnlyKeepsLocation() throws Exception {
        // Trang Danh mục → Nơi làm việc đổi tên bằng PATCH chỉ có tên: toạ độ, bán kính, IP văn phòng phải giữ nguyên.
        ObjectNode n = body(null);
        n.put("latitude", 21.028511);
        n.put("longitude", 105.804817);
        n.put("radiusM", 350);
        n.put("officeIps", "113.190.1.10");
        long id = create(n);
        ObjectNode p = om.createObjectNode();
        p.put("id", id);
        p.put("name", "WSL-doi-ten-" + UUID.randomUUID());
        mockMvc
            .perform(
                patch(URL + "/" + id).with(HR).contentType(MediaType.parseMediaType("application/merge-patch+json")).content(om.writeValueAsBytes(p))
            )
            .andExpect(status().isOk());
        String res = mockMvc.perform(get(URL + "/" + id).with(HR)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode j = om.readTree(res);
        assertThat(j.get("name").asText()).isEqualTo(p.get("name").asText());
        assertThat(j.get("latitude").decimalValue()).isEqualByComparingTo("21.028511");
        assertThat(j.get("longitude").decimalValue()).isEqualByComparingTo("105.804817");
        assertThat(j.get("radiusM").asInt()).isEqualTo(350);
        assertThat(j.get("officeIps").asText()).isEqualTo("113.190.1.10");
    }

    @Test
    void defaultRadius200() throws Exception {
        long id = create(body(null));
        String res = mockMvc.perform(get(URL + "/" + id).with(HR)).andReturn().getResponse().getContentAsString();
        JsonNode j = om.readTree(res);
        assertThat(j.get("radiusM").asInt()).isEqualTo(200);
        assertThat(j.get("latitude") == null || j.get("latitude").isNull()).isTrue();
    }

    @Test
    void ipsAreNormalized() throws Exception {
        long id = create(body(null));
        ObjectNode n = body(id);
        n.put("officeIps", "  1.2.3.4  \n\n   \n2001:db8::/32\r\n");
        mockMvc
            .perform(put(URL + "/" + id).with(HR).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(n)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.officeIps").value("1.2.3.4\n2001:db8::/32"));
    }

    @Test
    void halfCoordinateRejected() throws Exception {
        long id = create(body(null));
        ObjectNode n = body(id);
        n.put("latitude", 21.0);
        putExpectError(n, id, "worksitelocationinvalid");
    }

    @Test
    void outOfRangeRejected() throws Exception {
        long id = create(body(null));
        ObjectNode n = body(id);
        n.put("latitude", 91);
        n.put("longitude", 105.0);
        putExpectError(n, id, "worksitelocationinvalid");
        n = body(id);
        n.put("radiusM", 10);
        putExpectError(n, id, "worksiteradiusinvalid");
        n = body(id);
        n.put("radiusM", 5001);
        putExpectError(n, id, "worksiteradiusinvalid");
    }

    @Test
    void badIpRejected() throws Exception {
        long id = create(body(null));
        for (String bad : List.of("300.1.1.1", "abc", "1.2.3.4/33", "example.com", "1.2.3.4/", "::g")) {
            ObjectNode n = body(id);
            n.put("officeIps", "10.0.0.1\n" + bad);
            putExpectError(n, id, "worksiteipinvalid");
        }
    }

    @Test
    void hrMayEdit() throws Exception {
        long id = create(body(null));
        ObjectNode n = body(id);
        mockMvc
            .perform(put(URL + "/" + id).with(KE_TOAN).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(n)))
            .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void deleteSiteWithPunchesRefused() throws Exception {
        long id = create(body(null));
        Employee e = LeaveFixtures.employee(employeeRepository, "WSD" + UUID.randomUUID().toString().substring(0, 6), PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(e.getId());
        p.setWorkDay(LocalDate.of(2026, 10, 20));
        p.setPunchTime(LocalTime.of(8, 0));
        p.setKind(AttendancePunchKind.VAO);
        p.setSource(AttendancePunchSource.APP);
        p.setStatus(AttendancePunchStatus.CHAP_NHAN);
        p.setSiteId(id);
        p.setEnteredBy("x");
        p.setEnteredAt(Instant.parse("2026-10-20T01:00:00Z"));
        punchRepository.saveAndFlush(p);

        assertThatThrownBy(() -> workSiteService.delete(id))
            .isInstanceOf(EmployeeRuleException.class)
            .hasMessage("Địa điểm đã có lần chấm công, không xóa được; hãy ngừng sử dụng")
            .extracting(t -> ((EmployeeRuleException) t).getErrorKey())
            .isEqualTo("worksitehaspunches");
    }

    @Test
    void parseOfficeIps() {
        assertThat(WorkSiteService.parseOfficeIps(null)).isEmpty();
        assertThat(WorkSiteService.parseOfficeIps(" 1.2.3.4 \n\n10.0.0.0/8 ")).containsExactly("1.2.3.4", "10.0.0.0/8");
    }
}
