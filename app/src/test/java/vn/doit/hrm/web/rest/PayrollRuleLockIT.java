package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.doit.hrm.web.rest.PayrollFlow.ADMIN;
import static vn.doit.hrm.web.rest.PayrollFlow.KE_TOAN;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.payroll.PayrollRuleTestSupport;
import vn.doit.hrm.service.payroll.PayrollTestData;

/** Mốc luật mà một phiên bản bảng lương đã dùng (kỳ nháp, kỳ đã mở lại) bị khóa; sửa, xóa mốc sau đó không đổi kỳ đã duyệt. */
@IntegrationTest
@AutoConfigureMockMvc
@Import({ PayrollTestData.class, PayrollRuleTestSupport.class })
class PayrollRuleLockIT {

    private static final String BODY =
        "{\"effectiveFrom\":\"%s\",\"personalDeduction\":%d,\"dependantDeduction\":6200000,\"socialInsuranceCap\":50600000," +
        "\"unemploymentInsuranceCap\":106200000,\"brackets\":[{\"upTo\":10000000,\"rate\":0.05},{\"upTo\":null,\"rate\":0.35}]}";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-payroll-rule-lock-it");
        registry.add("application.storage.root", storage::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollTestData data;

    @Autowired
    private PayrollRuleTestSupport rules;

    @Autowired
    private JdbcTemplate jdbc;

    private Map<String, Long> ids;

    @BeforeEach
    void seed() {
        data.clean();
        ids = new HashMap<>(data.seedProfiles());
        ids.put("TT2601", data.seedService("TT2601", "Mai Văn Phúc"));
        ids.put("HDDV9001", data.seedService("HDDV9001", "Tạ Văn Quý"));
        ids.put("HDDV9002", data.seedService("HDDV9002", "Hồ Thị Sen"));
    }

    @AfterEach
    void clean() {
        data.clean();
        rules.resetToSeed();
    }

    private void confirmJune() throws Exception {
        Integer draft = PayrollFlow.readyDraft(mockMvc, "2026-06", ids);
        mockMvc.perform(post("/api/payroll/imports/{id}/confirm", draft).with(KE_TOAN)).andExpect(status().isCreated());
    }

    /** Id mốc nạp sẵn có hiệu lực 2026-01 (mốc kỳ 2026-06 dùng). */
    private long milestone(String from) throws Exception {
        String json = mockMvc.perform(get("/api/payroll/rules").with(ADMIN)).andReturn().getResponse().getContentAsString();
        return ((Number) ((java.util.List<?>) JsonPath.read(json, "$.milestones[?(@.effectiveFrom=='" + from + "')].id")).get(0)).longValue();
    }

    private long add(String from) throws Exception {
        String json = mockMvc
            .perform(post("/api/payroll/rules").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(from, 19_000_000)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private void assertLocked(long id) throws Exception {
        mockMvc
            .perform(put("/api/payroll/rules/" + id).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("2027-01", 17_000_000)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.rulemilestoneused"));
        mockMvc
            .perform(delete("/api/payroll/rules/" + id).with(ADMIN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.rulemilestoneused"));
    }

    @Test
    void milestoneUsedByDraftPeriodVersionIsLocked() throws Exception {
        confirmJune();
        assertThat(jdbc.queryForObject("select status from payroll_period where period = '2026-06'", String.class)).isEqualTo("NHAP");
        long id = milestone("2026-01");
        assertLocked(id);
        mockMvc
            .perform(get("/api/payroll/rules").with(ADMIN))
            .andExpect(jsonPath("$.milestones[?(@.id==" + id + ")].used").value(true));
    }

    @Test
    void milestoneUsedByReopenedPeriodIsLocked() throws Exception {
        confirmJune();
        mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(ADMIN)).andExpect(status().isOk());
        mockMvc
            .perform(post("/api/payroll/periods/2026-06/reopen").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"sửa lại\"}"))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from payroll_period where period = '2026-06'", String.class)).isEqualTo("NHAP");
        assertLocked(milestone("2026-01"));
    }

    @Test
    void editingLaterMilestoneLeavesApprovedPeriodUntouched() throws Exception {
        confirmJune();
        mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(ADMIN)).andExpect(status().isOk());
        String rulesBefore = jdbc.queryForObject("select rules_snapshot from payroll_version where version = 1", String.class);
        String issuesBefore = jdbc.queryForObject("select issues_snapshot from payroll_version where version = 1", String.class);
        String viewBefore = mockMvc.perform(get("/api/payroll/periods/2026-06").with(KE_TOAN)).andReturn().getResponse().getContentAsString();
        String linesBefore = mockMvc
            .perform(get("/api/payroll/periods/2026-06/versions/1/lines").with(KE_TOAN))
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Hai mốc mới sau kỳ đã duyệt chưa dùng: sửa và xóa được (không đụng mốc nạp sẵn để các IT khác còn nguyên dữ liệu).
        long first = add("2027-01");
        long second = add("2027-06");
        mockMvc
            .perform(put("/api/payroll/rules/" + first).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("2027-03", 20_000_000)))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/payroll/rules/" + second).with(ADMIN)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/payroll/rules/" + first).with(ADMIN)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select rules_snapshot from payroll_version where version = 1", String.class)).isEqualTo(rulesBefore);
        assertThat(jdbc.queryForObject("select issues_snapshot from payroll_version where version = 1", String.class)).isEqualTo(issuesBefore);
        assertThat(mockMvc.perform(get("/api/payroll/periods/2026-06").with(KE_TOAN)).andReturn().getResponse().getContentAsString())
            .isEqualTo(viewBefore);
        assertThat(
            mockMvc.perform(get("/api/payroll/periods/2026-06/versions/1/lines").with(KE_TOAN)).andReturn().getResponse().getContentAsString()
        )
            .isEqualTo(linesBefore);
    }
}
