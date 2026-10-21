package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hrm.web.rest.PayrollFlow.ADMIN;
import static vn.doit.hrm.web.rest.PayrollFlow.KE_TOAN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import vn.doit.hrm.service.payroll.PayrollFixture;
import vn.doit.hrm.service.payroll.PayrollRuleTestSupport;
import vn.doit.hrm.service.payroll.PayrollTestData;

/** Xác nhận bản nháp thành phiên bản, duyệt và mở lại kỳ; lịch sử không đổi khi luật đổi. */
@IntegrationTest
@AutoConfigureMockMvc
@Import({ PayrollTestData.class, PayrollRuleTestSupport.class })
class PayrollPeriodIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-payroll-period-it");
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

    private org.springframework.test.web.servlet.ResultActions confirm(Integer importId) throws Exception {
        return mockMvc.perform(post("/api/payroll/imports/{id}/confirm", importId).with(KE_TOAN));
    }

    private org.springframework.test.web.servlet.ResultActions approve() throws Exception {
        return mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(ADMIN));
    }

    private org.springframework.test.web.servlet.ResultActions reopen(String reason) throws Exception {
        return mockMvc.perform(
            post("/api/payroll/periods/2026-06/reopen").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":" + reason + "}")
        );
    }

    private long sum(String column, int version) {
        return jdbc.queryForObject(
            "select coalesce(sum(l." + column + "), 0) from payroll_line l join payroll_version v on v.id = l.version_id " +
            "join payroll_period p on p.id = v.period_id where p.period = '2026-06' and v.version = ?",
            Long.class,
            version
        );
    }

    @Test
    void confirmCreatesVersionWithLinesAndTotals() throws Exception {
        confirm(PayrollFlow.readyDraft(mockMvc, "2026-06", ids))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.period").value("2026-06"))
            .andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select status from payroll_period where period = '2026-06'", String.class)).isEqualTo("NHAP");
        assertThat(sum("thuc_nhan", 1)).isEqualTo(PayrollFixture.TOTAL_NET);
        assertThat(sum("tong_chi_phi", 1)).isEqualTo(PayrollFixture.TOTAL_COST);
        Map<String, Object> row18 = jdbc.queryForMap(
            "select employee_id, mapped_manually, file_code from payroll_line where excel_row = 18"
        );
        assertThat(row18.get("employee_id")).isEqualTo(ids.get("DOIT2515"));
        assertThat(row18.get("mapped_manually")).isEqualTo(true);
        assertThat(row18.get("file_code")).isEqualTo("DOIT2508");
        assertThat(jdbc.queryForObject("select status from payroll_import", String.class)).isEqualTo("DA_XAC_NHAN");

        // Bản nháp đã xác nhận không xác nhận lại được.
        Integer second = PayrollFlow.readyDraft(mockMvc, "2026-06", ids);
        confirm(second).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2));
        assertThat(jdbc.queryForObject("select count(*) from payroll_line l join payroll_version v on v.id = l.version_id where v.version = 1", Integer.class)).isEqualTo(14);
        confirm(second).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.importclosed"));
    }

    @Test
    void approveBlocksImportUntilReopened() throws Exception {
        confirm(PayrollFlow.readyDraft(mockMvc, "2026-06", ids)).andExpect(status().isCreated());
        Integer pending = PayrollFlow.readyDraft(mockMvc, "2026-06", ids);
        confirm(PayrollFlow.readyDraft(mockMvc, "2026-06", ids)).andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(KE_TOAN)).andExpect(status().isForbidden());
        approve().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_DUYET")).andExpect(jsonPath("$.approvedVersion").value(2));
        approve().andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.alreadyapproved"));

        mockMvc
            .perform(
                multipart("/api/payroll/imports")
                    .file(PayrollImportResourceIT.file("a.xlsx", PayrollImportResourceIT.fixture()))
                    .param("period", "2026-06")
                    .with(KE_TOAN)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.periodapproved"));
        confirm(pending).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.periodapproved"));

        reopen("\"\"").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.reasonrequired"));
        mockMvc
            .perform(
                post("/api/payroll/periods/2026-06/reopen").with(KE_TOAN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")
            )
            .andExpect(status().isForbidden());
        reopen("\"Sai phụ cấp xăng xe\"").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NHAP"));
        reopen("\"lần hai\"").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.notapproved"));
        assertThat(jdbc.queryForList("select action from payroll_period_event order by id", String.class)).containsExactly("DUYET", "MO_LAI");

        confirm(pending).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void cannotConfirmWithErrorsOrApproveEmpty() throws Exception {
        Integer raw = PayrollFlow.upload(mockMvc, "2026-06");
        confirm(raw).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.importnotready"));
        approve().andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.noversion"));
    }

    @Test
    void historyFrozenAfterLawChange() throws Exception {
        confirm(PayrollFlow.readyDraft(mockMvc, "2026-06", ids)).andExpect(status().isCreated());
        approve().andExpect(status().isOk());
        String rulesBefore = jdbc.queryForObject("select rules_snapshot from payroll_version where version = 1", String.class);
        String issuesBefore = jdbc.queryForObject("select issues_snapshot from payroll_version where version = 1", String.class);
        assertThat(rulesBefore).contains("\"effectiveFrom\":\"2026-01\"").contains("15500000");
        assertThat(issuesBefore).contains("WARNING");

        String milestone =
            "{\"effectiveFrom\":\"%s\",\"personalDeduction\":17000000,\"dependantDeduction\":6200000,\"socialInsuranceCap\":50600000," +
            "\"unemploymentInsuranceCap\":106200000,\"brackets\":[{\"upTo\":10000000,\"rate\":0.05},{\"upTo\":null,\"rate\":0.35}]}";
        for (String month : List.of("2026-06", "2026-05")) {
            mockMvc
                .perform(post("/api/payroll/rules").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(milestone.formatted(month)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.rulesbeforeapproved"));
        }
        mockMvc
            .perform(post("/api/payroll/rules").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(milestone.formatted("2026-07")))
            .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select rules_snapshot from payroll_version where version = 1", String.class)).isEqualTo(rulesBefore);
        assertThat(jdbc.queryForObject("select issues_snapshot from payroll_version where version = 1", String.class)).isEqualTo(issuesBefore);
        assertThat(sum("thuc_nhan", 1)).isEqualTo(PayrollFixture.TOTAL_NET);
    }

    @Test
    void approveAndConfirmRaceNeverLeavesNewVersionUnapproved() throws Exception {
        confirm(PayrollFlow.readyDraft(mockMvc, "2026-06", ids)).andExpect(status().isCreated());
        Integer ready = PayrollFlow.readyDraft(mockMvc, "2026-06", ids);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            calls.add(() -> approve().andReturn().getResponse().getStatus());
            calls.add(() -> confirm(ready).andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
            Integer approved = jdbc.queryForObject("select approved_version from payroll_period where period = '2026-06'", Integer.class);
            Integer latest = jdbc.queryForObject(
                "select max(v.version) from payroll_version v join payroll_period p on p.id = v.period_id where p.period = '2026-06'",
                Integer.class
            );
            assertThat(statuses.get(0)).isEqualTo(200);
            if (statuses.get(1) == 201) {
                assertThat(latest).isEqualTo(2);
                assertThat(approved).isEqualTo(2);
            } else {
                assertThat(statuses.get(1)).isEqualTo(400);
                assertThat(latest).isEqualTo(1);
                assertThat(approved).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
