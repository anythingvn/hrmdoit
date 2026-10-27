package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.AttendanceSnapshot;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollCalcDraft;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.PayrollCalcStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendanceSnapshotRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollCalcDraftRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;

/**
 * Bản tính lương nháp: tạo, sửa ô, tính lại từ hồ sơ. Tháng thử 03/2026 có 22 ngày làm việc; mức luật của kỳ: giảm trừ bản thân
 * 15,5 triệu, biểu thuế 5% đến 10 triệu.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class PayrollCalcResourceIT {

    private static final String P = "2026-03";
    private static final RequestPostProcessor KE_TOAN = user("ketoan-calc").authorities(() -> "ROLE_KE_TOAN");
    private static final RequestPostProcessor ADMIN = user("admin-calc").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor HR = user("hr-calc").authorities(() -> "ROLE_HR");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private AttendanceMonthRepository months;

    @Autowired
    private AttendanceSnapshotRepository snapshots;

    @Autowired
    private SalaryChangeRepository salaries;

    @Autowired
    private PayrollPeriodRepository periods;

    @Autowired
    private PayrollCalcDraftRepository drafts;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    private void closedMonth(String month) {
        AttendanceMonth m = new AttendanceMonth();
        m.setMonth(month);
        m.setStatus(AttendanceMonthStatus.DA_CHOT);
        months.saveAndFlush(m);
    }

    private Employee person(String code, PersonType type, String salaryFrom, long salary, String month, String work) {
        Employee e = employees.saveAndFlush(
            LeaveFixtures.employee(employees, code, type, LocalDate.of(2024, 1, 2)).fullName("Người thử " + code)
        );
        if (work != null) {
            AttendanceSnapshot s = new AttendanceSnapshot();
            s.setMonth(month);
            s.setEmployeeId(e.getId());
            s.setStandardDays(new BigDecimal("22"));
            s.setWorkDays(new BigDecimal(work));
            s.setPaidLeaveDays(BigDecimal.ZERO);
            s.setHolidayDays(BigDecimal.ZERO);
            s.setTripDays(BigDecimal.ZERO);
            s.setSickDays(BigDecimal.ZERO);
            snapshots.saveAndFlush(s);
        }
        if (salaryFrom != null) {
            salaries.saveAndFlush(
                new SalaryChange().employee(e).effectiveDate(LocalDate.parse(salaryFrom)).amount(salary).basis(SalaryBasis.GROSS)
            );
        }
        return e;
    }

    private Employee staff(String code, long salary) {
        return person(code, PersonType.NHAN_VIEN, "2025-01-01", salary, P, "22");
    }

    private ResultActions create(String period) throws Exception {
        return mockMvc.perform(post("/api/payroll/periods/" + period + "/calc").with(KE_TOAN));
    }

    private String createOk() throws Exception {
        return create(P).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions edit(Object draftId, Object lineId, String body) throws Exception {
        return mockMvc.perform(
            put("/api/payroll/calc/{d}/lines/{l}", draftId, lineId).with(KE_TOAN).contentType(MediaType.APPLICATION_JSON).content(body)
        );
    }

    private static Map<String, Object> line(String draft, String code) {
        List<Map<String, Object>> found = JsonPath.read(draft, "$.lines[?(@.code=='" + code + "')]");
        assertThat(found)
            .as("dòng " + code)
            .hasSize(1);
        return found.get(0);
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal amount(Map<String, Object> line, String field) {
        Object v = ((Map<String, Object>) line.get("amounts")).get(field);
        assertThat(v).as(field).isNotNull();
        return new BigDecimal(String.valueOf(v));
    }

    private static Number lineId(Map<String, Object> line) {
        return (Number) line.get("id");
    }

    private static Number draftId(String draft) {
        return JsonPath.read(draft, "$.id");
    }

    private String fetch(Number draftId) throws Exception {
        return mockMvc
            .perform(get("/api/payroll/calc/{id}", draftId).with(KE_TOAN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    @Test
    void failsWhenAttendanceNotClosedOrPeriodApproved() throws Exception {
        create(P).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.payrollattendancenotclosed"));
        closedMonth(P);
        // Dòng kỳ đã được lần gọi trước tạo và nằm trong phiên làm việc: đổi qua kho dữ liệu, không qua JDBC.
        PayrollPeriod period = periods.findByPeriod(P).orElseThrow();
        period.setStatus(PayrollPeriodStatus.DA_DUYET);
        periods.saveAndFlush(period);
        create(P).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.periodapproved"));
    }

    @Test
    void createsAllColumnsAndReopensTheSameDraft() throws Exception {
        closedMonth(P);
        staff("NV9901", 20_000_000L);
        String first = createOk();
        assertThat((String) JsonPath.read(first, "$.status")).isEqualTo("DANG_LAM");
        assertThat((String) JsonPath.read(first, "$.period")).isEqualTo(P);
        assertThat((List<?>) JsonPath.read(first, "$.columns")).hasSize(36);
        assertThat((List<String>) JsonPath.read(first, "$.columns[?(@.input==true)].field")).containsExactlyInAnyOrder(
            "THUONG_DU_AN",
            "THUONG_KPI",
            "TRO_CAP_THOI_VIEC",
            "THUA_THIEU",
            "QUYET_TOAN_THUE",
            "GIAM_TRU_KHAC"
        );
        Map<String, Object> l = line(first, "NV9901");
        @SuppressWarnings("unchecked")
        Map<String, Object> amounts = (Map<String, Object>) l.get("amounts");
        assertThat(amounts).hasSize(36);
        assertThat(amount(l, "LUONG_GOP")).isEqualByComparingTo("20000000");
        assertThat(amount(l, "TONG_BH_NLD")).isEqualByComparingTo("2100000");
        assertThat(amount(l, "THUE_TNCN")).isEqualByComparingTo("120000");
        assertThat(amount(l, "THUC_NHAN")).isEqualByComparingTo("17780000");
        Object total = JsonPath.read(first, "$.totals.THUC_NHAN");
        assertThat(new BigDecimal(String.valueOf(total))).isEqualByComparingTo("17780000");

        String second = createOk();
        assertThat(draftId(second).longValue()).isEqualTo(draftId(first).longValue());
        assertThat((List<?>) JsonPath.read(second, "$.lines")).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from payroll_calc_draft where status = 'DANG_LAM'", Integer.class)).isEqualTo(1);
    }

    @Test
    void manualBonusRaisesNetAfterTax() throws Exception {
        closedMonth(P);
        staff("NV9902", 20_000_000L);
        String draft = createOk();
        Map<String, Object> l = line(draft, "NV9902");
        String res = edit(draftId(draft), lineId(l), "{\"manual\":{\"THUONG_KPI\":5000000}}")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        // Lương gộp 25 tr, BH 2,1 tr, thu nhập tính thuế 7,4 tr, thuế 5% = 370.000, thực nhận 22.530.000.
        Map<String, Object> after = JsonPath.read(res, "$");
        assertThat(amount(after, "THUONG_KPI")).isEqualByComparingTo("5000000");
        assertThat(amount(after, "THUE_TNCN")).isEqualByComparingTo("370000");
        assertThat(amount(after, "THUC_NHAN")).isEqualByComparingTo("22530000");
        assertThat(new BigDecimal(String.valueOf(((Map<?, ?>) after.get("manual")).get("THUONG_KPI")))).isEqualByComparingTo("5000000");
        // null xóa ô nhập tay.
        Map<String, Object> cleared = JsonPath.read(
            edit(draftId(draft), lineId(l), "{\"manual\":{\"THUONG_KPI\":null}}").andReturn().getResponse().getContentAsString(),
            "$"
        );
        assertThat((Map<?, ?>) cleared.get("manual")).isEmpty();
        assertThat(amount(cleared, "THUC_NHAN")).isEqualByComparingTo("17780000");
    }

    @Test
    void overrideSurvivesRecalculateAndCanBeCleared() throws Exception {
        closedMonth(P);
        Employee e = staff("NV9903", 20_000_000L);
        String draft = createOk();
        Map<String, Object> l = line(draft, "NV9903");
        edit(draftId(draft), lineId(l), "{\"overrides\":{\"NGAY_CONG_THUC_TE\":11}}").andExpect(status().isOk());
        // Hồ sơ đổi lương (24 tr từ 01/02/2026) rồi tính lại từ hồ sơ.
        salaries.saveAndFlush(
            new SalaryChange()
                .employee(e)
                .effectiveDate(LocalDate.of(2026, 2, 1))
                .amount(24_000_000L)
                .basis(SalaryBasis.GROSS)
        );
        String res = mockMvc
            .perform(post("/api/payroll/calc/{id}/recalculate", draftId(draft)).with(KE_TOAN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Map<String, Object> after = line(res, "NV9903");
        assertThat(new BigDecimal(String.valueOf(((Map<?, ?>) after.get("overrides")).get("NGAY_CONG_THUC_TE")))).isEqualByComparingTo(
            "11"
        );
        assertThat(amount(after, "NGAY_CONG_THUC_TE")).isEqualByComparingTo("11");
        assertThat(amount(after, "LUONG_CO_BAN")).isEqualByComparingTo("24000000");
        assertThat(amount(after, "LUONG_GOP")).isEqualByComparingTo("12000000");
        assertThat(lineId(after).longValue()).isEqualTo(lineId(l).longValue());

        Map<String, Object> cleared = JsonPath.read(
            edit(draftId(draft), lineId(l), "{\"overrides\":{\"NGAY_CONG_THUC_TE\":null}}").andReturn().getResponse().getContentAsString(),
            "$"
        );
        assertThat((Map<?, ?>) cleared.get("overrides")).isEmpty();
        assertThat(amount(cleared, "NGAY_CONG_THUC_TE")).isEqualByComparingTo("22");
        assertThat(amount(cleared, "LUONG_GOP")).isEqualByComparingTo("24000000");
    }

    @Test
    void rejectsEditsOnTotalsAndUnknownLines() throws Exception {
        closedMonth(P);
        staff("NV9904", 20_000_000L);
        String draft = createOk();
        Map<String, Object> l = line(draft, "NV9904");
        edit(draftId(draft), lineId(l), "{\"overrides\":{\"TONG_LUONG_GOP\":1}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcfieldinvalid"));
        edit(draftId(draft), lineId(l), "{\"manual\":{\"LUONG_GOP\":1}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcfieldinvalid"));
        edit(draftId(draft), 999999999L, "{\"manual\":{\"THUONG_KPI\":1}}").andExpect(status().isNotFound());
    }

    @Test
    void deletesALine() throws Exception {
        closedMonth(P);
        staff("NV9905", 20_000_000L);
        staff("NV9906", 21_000_000L);
        String draft = createOk();
        assertThat((List<?>) JsonPath.read(draft, "$.lines")).hasSize(2);
        Map<String, Object> l = line(draft, "NV9905");
        mockMvc
            .perform(delete("/api/payroll/calc/{d}/lines/{l}", draftId(draft), lineId(l)).with(KE_TOAN))
            .andExpect(status().isNoContent());
        String after = fetch(draftId(draft));
        assertThat((List<?>) JsonPath.read(after, "$.lines")).hasSize(1);
        assertThat(line(after, "NV9906")).isNotNull();
    }

    @Test
    void missingSalaryIsAnErrorUntilTheLineIsRemoved() throws Exception {
        closedMonth(P);
        staff("NV9907", 20_000_000L);
        person("NV9908", PersonType.NHAN_VIEN, null, 0, P, "22");
        String draft = createOk();
        Map<String, Object> bad = line(draft, "NV9908");
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) bad.get("errors");
        assertThat(errors).containsExactly("Chưa có mức lương");
        assertThat((Boolean) JsonPath.read(draft, "$.canConfirm")).isFalse();
        // Lỗi không bị cảnh báo đã xem che đi.
        mockMvc.perform(post("/api/payroll/calc/{id}/acknowledge", draftId(draft)).with(KE_TOAN)).andExpect(status().isOk());
        assertThat((Boolean) JsonPath.read(fetch(draftId(draft)), "$.canConfirm")).isFalse();
        mockMvc
            .perform(delete("/api/payroll/calc/{d}/lines/{l}", draftId(draft), lineId(bad)).with(KE_TOAN))
            .andExpect(status().isNoContent());
        assertThat((Boolean) JsonPath.read(fetch(draftId(draft)), "$.canConfirm")).isTrue();
    }

    @Test
    void warningsMustBeAcknowledgedAndEditsResetIt() throws Exception {
        closedMonth(P);
        // Mức lương chỉ bắt đầu từ 10/03: cảnh báo "tạm tính theo mức này".
        person("NV9909", PersonType.NHAN_VIEN, "2026-03-10", 20_000_000L, P, "22");
        String draft = createOk();
        Map<String, Object> l = line(draft, "NV9909");
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) l.get("warnings");
        assertThat(warnings).isNotEmpty();
        assertThat((Boolean) JsonPath.read(draft, "$.warningsAcknowledged")).isFalse();
        assertThat((Boolean) JsonPath.read(draft, "$.canConfirm")).isFalse();

        String acked = mockMvc
            .perform(post("/api/payroll/calc/{id}/acknowledge", draftId(draft)).with(KE_TOAN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat((Boolean) JsonPath.read(acked, "$.warningsAcknowledged")).isTrue();
        assertThat((Boolean) JsonPath.read(acked, "$.canConfirm")).isTrue();

        edit(draftId(draft), lineId(l), "{\"manual\":{\"THUONG_KPI\":1000000}}").andExpect(status().isOk());
        String after = fetch(draftId(draft));
        assertThat((Boolean) JsonPath.read(after, "$.warningsAcknowledged")).isFalse();
        assertThat((Boolean) JsonPath.read(after, "$.canConfirm")).isFalse();
    }

    @Test
    void internWithoutTaxCodeGetsAWarning() throws Exception {
        closedMonth(P);
        Employee intern = person("TT9901", PersonType.THUC_TAP, "2025-01-01", 3_000_000L, P, "22");
        String draft = createOk();
        Map<String, Object> l = line(draft, "TT9901");
        assertThat(amount(l, "THUE_TNCN")).isEqualByComparingTo("300000");
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) l.get("warnings");
        assertThat(warnings).contains("Chưa có mã số thuế");

        intern.setTaxCode("8012345678");
        employees.saveAndFlush(intern);
        String res = mockMvc
            .perform(post("/api/payroll/calc/{id}/recalculate", draftId(draft)).with(KE_TOAN))
            .andReturn()
            .getResponse()
            .getContentAsString();
        @SuppressWarnings("unchecked")
        List<String> after = (List<String>) line(res, "TT9901").get("warnings");
        assertThat(after).doesNotContain("Chưa có mã số thuế");
    }

    @Test
    void recalculateAddsNewPeopleAndMarksThoseWhoLeft() throws Exception {
        closedMonth(P);
        Employee stays = staff("NV9910", 20_000_000L);
        Employee leaves = staff("NV9911", 20_000_000L);
        String draft = createOk();
        Map<String, Object> leaving = line(draft, "NV9911");
        edit(draftId(draft), lineId(leaving), "{\"manual\":{\"THUONG_KPI\":2000000}}").andExpect(status().isOk());

        leaves.setLastWorkingDay(LocalDate.of(2026, 2, 20));
        employees.saveAndFlush(leaves);
        staff("NV9912", 22_000_000L);
        String res = mockMvc
            .perform(post("/api/payroll/calc/{id}/recalculate", draftId(draft)).with(KE_TOAN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat((List<?>) JsonPath.read(res, "$.lines")).hasSize(3);
        Map<String, Object> gone = line(res, "NV9911");
        assertThat((Boolean) gone.get("stale")).isTrue();
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) gone.get("errors");
        assertThat(errors).containsExactly("Không còn thuộc kỳ");
        assertThat(new BigDecimal(String.valueOf(((Map<?, ?>) gone.get("manual")).get("THUONG_KPI")))).isEqualByComparingTo("2000000");
        assertThat((Boolean) line(res, "NV9910").get("stale")).isFalse();
        assertThat(line(res, "NV9912")).isNotNull();
        assertThat((Boolean) JsonPath.read(res, "$.canConfirm")).isFalse();
        assertThat(stays.getId()).isNotNull();
    }

    @Test
    void closedDraftAndApprovedPeriodRejectEdits() throws Exception {
        closedMonth(P);
        staff("NV9913", 20_000_000L);
        String draft = createOk();
        Map<String, Object> l = line(draft, "NV9913");

        PayrollPeriod period = periods.findByPeriod(P).orElseThrow();
        period.setStatus(PayrollPeriodStatus.DA_DUYET);
        periods.saveAndFlush(period);
        edit(draftId(draft), lineId(l), "{\"manual\":{\"THUONG_KPI\":1}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.periodapproved"));
        mockMvc
            .perform(post("/api/payroll/calc/{id}/recalculate", draftId(draft)).with(KE_TOAN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.periodapproved"));
        period.setStatus(PayrollPeriodStatus.NHAP);
        periods.saveAndFlush(period);

        PayrollCalcDraft entity = drafts.findById(draftId(draft).longValue()).orElseThrow();
        entity.setStatus(PayrollCalcStatus.DA_HUY);
        drafts.saveAndFlush(entity);
        edit(draftId(draft), lineId(l), "{\"manual\":{\"THUONG_KPI\":1}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcdraftclosed"));
        mockMvc
            .perform(post("/api/payroll/calc/{id}/acknowledge", draftId(draft)).with(KE_TOAN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcdraftclosed"));
        mockMvc
            .perform(delete("/api/payroll/calc/{d}/lines/{l}", draftId(draft), lineId(l)).with(KE_TOAN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcdraftclosed"));
        // Bản đã hủy không còn là bản đang làm: tính lương lại mở bản mới.
        String fresh = createOk();
        assertThat(draftId(fresh).longValue()).isNotEqualTo(draftId(draft).longValue());
    }

    @Test
    void adminAllowedAndHrForbiddenOnEveryEndpoint() throws Exception {
        closedMonth(P);
        staff("NV9914", 20_000_000L);
        mockMvc.perform(post("/api/payroll/periods/" + P + "/calc").with(ADMIN)).andExpect(status().isOk());
        String draft = createOk();
        Map<String, Object> l = line(draft, "NV9914");
        Number d = draftId(draft);
        mockMvc.perform(post("/api/payroll/periods/" + P + "/calc").with(HR)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payroll/calc/{id}", d).with(HR)).andExpect(status().isForbidden());
        mockMvc
            .perform(put("/api/payroll/calc/{d}/lines/{l}", d, lineId(l)).with(HR).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/payroll/calc/{d}/lines/{l}", d, lineId(l)).with(HR)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payroll/calc/{id}/recalculate", d).with(HR)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payroll/calc/{id}/acknowledge", d).with(HR)).andExpect(status().isForbidden());
    }

    /** Envers ghi bảng _aud khi commit: test này tự commit và tự dọn. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void envers_recordsLineEdits() throws Exception {
        String month = "2026-05";
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Long employeeId = null;
        try {
            employeeId = tx.execute(s -> {
                closedMonth(month);
                return person("NV9915", PersonType.NHAN_VIEN, "2025-01-01", 20_000_000L, month, "22").getId();
            });
            String draft = mockMvc
                .perform(post("/api/payroll/periods/" + month + "/calc").with(KE_TOAN))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
            Map<String, Object> l = line(draft, "NV9915");
            edit(draftId(draft), lineId(l), "{\"manual\":{\"THUONG_KPI\":3000000}}").andExpect(status().isOk());
            long lineId = lineId(l).longValue();
            assertThat(
                jdbc.queryForObject("select count(*) from payroll_calc_draft_line_aud where id = ? and revtype = 0", Integer.class, lineId)
            ).isEqualTo(1);
            assertThat(
                jdbc.queryForObject("select count(*) from payroll_calc_draft_line_aud where id = ? and revtype = 1", Integer.class, lineId)
            ).isGreaterThanOrEqualTo(1);
            assertThat(
                jdbc.queryForObject("select count(*) from payroll_calc_draft_aud where id = ?", Integer.class, draftId(draft).longValue())
            ).isGreaterThanOrEqualTo(1);
        } finally {
            tx.executeWithoutResult(s -> {
                jdbc.update(
                    "delete from payroll_calc_draft_line where draft_id in (select d.id from payroll_calc_draft d join payroll_period p on p.id = d.period_id where p.period = ?)",
                    month
                );
                jdbc.update("delete from payroll_calc_draft where period_id in (select id from payroll_period where period = ?)", month);
                jdbc.update("delete from payroll_period where period = ?", month);
                jdbc.update("delete from attendance_snapshot where month = ?", month);
                jdbc.update("delete from attendance_month where month = ?", month);
            });
            Long id = employeeId;
            if (id != null) {
                tx.executeWithoutResult(s -> {
                    jdbc.update("delete from salary_change where employee_id = ?", id);
                    jdbc.update("delete from employee where id = ?", id);
                });
            }
        }
    }
}
