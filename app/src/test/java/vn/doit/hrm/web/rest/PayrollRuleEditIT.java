package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayrollRuleMilestoneRepository;
import vn.doit.hrm.service.payroll.PayrollRuleTestSupport;
import vn.doit.hrm.service.payroll.RuleBookService;

/** Mức luật định: sửa và xóa mốc chưa dùng (hiệu lực sau kỳ lương đã duyệt gần nhất), chỉ Quản trị. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(PayrollRuleTestSupport.class)
class PayrollRuleEditIT {

    private static final String BODY =
        "{\"effectiveFrom\":\"%s\",\"personalDeduction\":%d,\"dependantDeduction\":6200000," +
        "\"socialInsuranceCap\":50600000,\"unemploymentInsuranceCap\":106200000," +
        "\"brackets\":[{\"upTo\":10000000,\"rate\":0.05},{\"upTo\":30000000,\"rate\":0.10},{\"upTo\":null,\"rate\":0.35}]," +
        "\"legalBasis\":\"NQ thử nghiệm\"}";

    private static final String EDITED =
        "{\"effectiveFrom\":\"2027-08\",\"personalDeduction\":17000000,\"dependantDeduction\":6800000," +
        "\"socialInsuranceCap\":51000000,\"unemploymentInsuranceCap\":107000000," +
        "\"brackets\":[{\"upTo\":12000000,\"rate\":0.07},{\"upTo\":null,\"rate\":0.30}],\"legalBasis\":\"NQ sửa\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollRuleTestSupport support;

    @Autowired
    private PayrollPeriodRepository periodRepository;

    @Autowired
    private PayrollRuleMilestoneRepository milestoneRepository;

    @Autowired
    private RuleBookService ruleBookService;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void reset() {
        tx.executeWithoutResult(s -> jdbc.update("delete from payroll_period where period = '2027-02'"));
        support.resetToSeed();
    }

    private long add(String from) throws Exception {
        String json = mockMvc
            .perform(post("/api/payroll/rules").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(from, 16_000_000)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private void approve(String period) {
        PayrollPeriod p = new PayrollPeriod();
        p.setPeriod(period);
        p.setStatus(PayrollPeriodStatus.DA_DUYET);
        p.setApprovedBy("qt-le");
        p.setApprovedAt(Instant.parse("2027-03-05T03:00:00Z"));
        periodRepository.saveAndFlush(p);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void editUnusedMilestone() throws Exception {
        long id = add("2027-06");
        mockMvc
            .perform(
                put("/api/payroll/rules/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(EDITED)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.effectiveFrom").value("2027-08"))
            .andExpect(jsonPath("$.personalDeduction").value(17000000))
            .andExpect(jsonPath("$.brackets.length()").value(2))
            .andExpect(jsonPath("$.brackets[0].rate").value(0.07))
            .andExpect(jsonPath("$.brackets[1].upTo").doesNotExist())
            .andExpect(jsonPath("$.legalBasis").value("NQ sửa"));
        mockMvc
            .perform(get("/api/payroll/rules"))
            .andExpect(jsonPath("$.milestones[0].effectiveFrom").value("2027-08"))
            .andExpect(jsonPath("$.milestones[0].brackets.length()").value(2));
        assertThat(
            ruleBookService
                .current()
                .milestones()
                .get(ruleBookService.current().milestones().size() - 1)
                .rules()
                .personalDeduction()
        ).isEqualByComparingTo("17000000");
        // Envers ghi lại cả sửa lẫn bậc thuế mới.
        Integer mods = jdbc.queryForObject(
            "select count(*) from payroll_rule_milestone_aud where id = ? and revtype = 1",
            Integer.class,
            id
        );
        assertThat(mods).isGreaterThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void editUsedMilestoneRejected() throws Exception {
        long id = add("2027-01");
        approve("2027-02");
        mockMvc
            .perform(
                put("/api/payroll/rules/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(EDITED)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.rulemilestoneused"));
        mockMvc
            .perform(delete("/api/payroll/rules/" + id))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.rulemilestoneused"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void deleteUnused() throws Exception {
        long id = add("2027-06");
        mockMvc.perform(delete("/api/payroll/rules/" + id)).andExpect(status().isNoContent());
        assertThat(milestoneRepository.findById(id)).isEmpty();
        assertThat(ruleBookService.current().milestones()).hasSize(6);
        Integer dels = jdbc.queryForObject(
            "select count(*) from payroll_rule_milestone_aud where id = ? and revtype = 2",
            Integer.class,
            id
        );
        assertThat(dels).isEqualTo(1);
        mockMvc.perform(delete("/api/payroll/rules/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void deleteLastRejected() throws Exception {
        try {
            tx.executeWithoutResult(s -> {
                s.setRollbackOnly();
                var all = milestoneRepository.findAll();
                all.subList(1, all.size()).forEach(milestoneRepository::delete);
                milestoneRepository.flush();
                try {
                    mockMvc
                        .perform(delete("/api/payroll/rules/" + all.get(0).getId()))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value("error.rulemilestonelast"));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } finally {
            ruleBookService.refresh();
        }
        assertThat(milestoneRepository.count()).isEqualTo(6);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void editMovesBeforeApprovedRejected() throws Exception {
        long id = add("2027-06");
        approve("2027-02");
        mockMvc
            .perform(
                put("/api/payroll/rules/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.formatted("2027-02", 16_000_000))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.rulesbeforeapproved"));
        mockMvc
            .perform(
                put("/api/payroll/rules/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.formatted("2027-03", 16_000_000))
            )
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void invalidEditRejectedAndNothingChanged() throws Exception {
        long id = add("2027-06");
        mockMvc
            .perform(
                put("/api/payroll/rules/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.formatted("2027-08", 0))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.rulesinvalid"));
        mockMvc
            .perform(put("/api/payroll/rules/999999").contentType(MediaType.APPLICATION_JSON).content(EDITED))
            .andExpect(status().isNotFound());
        assertThat(milestoneRepository.findById(id).orElseThrow().getPersonalDeduction()).isEqualByComparingTo("16000000");
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getMarksUsed() throws Exception {
        add("2027-01");
        add("2027-06");
        approve("2027-02");
        mockMvc
            .perform(get("/api/payroll/rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.milestones[0].effectiveFrom").value("2027-06"))
            .andExpect(jsonPath("$.milestones[0].used").value(false))
            .andExpect(jsonPath("$.milestones[1].effectiveFrom").value("2027-01"))
            .andExpect(jsonPath("$.milestones[1].used").value(true))
            .andExpect(jsonPath("$.milestones[2].effectiveFrom").value("2026-07"))
            .andExpect(jsonPath("$.milestones[2].used").value(true));
    }

    @Test
    @WithMockUser(authorities = { "ROLE_HR", "ROLE_KE_TOAN" })
    void hrForbidden() throws Exception {
        mockMvc
            .perform(put("/api/payroll/rules/1").contentType(MediaType.APPLICATION_JSON).content(EDITED))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/payroll/rules/1")).andExpect(status().isForbidden());
    }
}
