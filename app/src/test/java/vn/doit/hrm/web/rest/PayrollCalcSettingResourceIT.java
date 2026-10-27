package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.payroll.calc.CalcSettings;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.payroll.calc.CalcSettingsService;

/** Thông số tính lương theo tháng hiệu lực; ô cam kết 08 trên hồ sơ; nguồn phiên bản lương. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class PayrollCalcSettingResourceIT {

    private static final String URL = "/api/payroll/calc-settings";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CalcSettingsService service;

    private static String body(String effectiveFrom, String bhxhEmployee, String hoursPerDay, String dayKinds) {
        return (
            "{\"effectiveFrom\":\"" +
            effectiveFrom +
            "\",\"bhxhEmployee\":" +
            bhxhEmployee +
            ",\"bhytEmployee\":0.015," +
            "\"bhtnEmployee\":0.01,\"bhxhCompany\":0.175,\"bhytCompany\":0.03,\"bhtnCompany\":0.01,\"unionFeeEnabled\":true," +
            "\"unionFeeRate\":0.02,\"hoursPerDay\":" +
            hoursPerDay +
            ",\"overtimeBaseIncludesInsuredAllowance\":false," +
            "\"overtimeAllNonTaxable\":false,\"actualDayKinds\":" +
            dayKinds +
            "," +
            "\"allowanceKeywords\":{\"PC_XANG_XE\":[\"xang\"]},\"withholdingThreshold\":2000000,\"withholdingRate\":0.10," +
            "\"payUnusedLeaveOnTermination\":true}"
        );
    }

    private static String valid(String effectiveFrom) {
        return body(effectiveFrom, "0.08", "8", "[\"WORK\",\"PAID_LEAVE\"]");
    }

    private ResultActions add(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long addOk(String json) throws Exception {
        String res = add(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private void approvePeriod(String period) {
        jdbc.update("insert into payroll_period (period, status) values (?, 'DA_DUYET')", period);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void noSettingsMeansDefaults() {
        assertThat(service.forPeriod(YearMonth.of(2026, 6))).isEqualTo(CalcSettings.defaults());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void settingAppliesFromItsMonthOnly() throws Exception {
        addOk(valid("2026-07"));
        assertThat(service.forPeriod(YearMonth.of(2026, 6))).isEqualTo(CalcSettings.defaults());
        CalcSettings july = service.forPeriod(YearMonth.of(2026, 7));
        assertThat(july.unionFeeEnabled()).isTrue();
        assertThat(july.actualDayKinds()).containsExactlyInAnyOrder(CalcSettings.DayKind.WORK, CalcSettings.DayKind.PAID_LEAVE);
        assertThat(july.bhxhCompany()).isEqualByComparingTo(new BigDecimal("0.175"));
        assertThat(service.forPeriod(YearMonth.of(2026, 9))).isEqualTo(july);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void laterSettingTakesOverFromItsMonth() throws Exception {
        addOk(valid("2026-07"));
        addOk(body("2026-10", "0.09", "8", "[\"WORK\"]"));
        assertThat(service.forPeriod(YearMonth.of(2026, 9)).bhxhEmployee()).isEqualByComparingTo("0.08");
        assertThat(service.forPeriod(YearMonth.of(2026, 10)).bhxhEmployee()).isEqualByComparingTo("0.09");
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void invalidValuesAreRejected() throws Exception {
        addOk(valid("2026-07"));
        for (String bad : new String[] {
            valid("2026-07"), // trùng tháng hiệu lực
            valid("2026-13"),
            valid("07/2026"),
            body("2026-08", "-0.01", "8", "[\"WORK\"]"),
            body("2026-08", "1.01", "8", "[\"WORK\"]"),
            body("2026-08", "0.08", "0", "[\"WORK\"]"),
            body("2026-08", "0.08", "24.5", "[\"WORK\"]"),
            body("2026-08", "0.08", "8", "[]"),
            valid("2026-08").replace("\"withholdingThreshold\":2000000", "\"withholdingThreshold\":-1"),
            body("2026-08", "0.08", "8", "[\"WORK\",null]"),
            body("2026-08", "0.08", "8", "[\"NO_SUCH_KIND\"]"),
            valid("2026-08").replace("{\"PC_XANG_XE\":[\"xang\"]}", "{\"PC_XANG_XE\":null}"),
            valid("2026-08").replace("{\"PC_XANG_XE\":[\"xang\"]}", "{\"PC_XANG_XE\":[null]}"),
            valid("2026-08").replace("{\"PC_XANG_XE\":[\"xang\"]}", "{\"PC_XANG_XE\":[\"  \"]}"),
            valid("2026-08").replace("{\"PC_XANG_XE\":[\"xang\"]}", "{\"NO_SUCH_FIELD\":[\"xang\"]}"),
        }) {
            add(bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.calcsettinginvalid"))
                .andExpect(jsonPath("$.detail").value("Thông số tính lương không hợp lệ."));
        }
        mockMvc.perform(get(URL)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void longUnpaidInsuranceRuleDefaultsOnAndCanBeTurnedOff() throws Exception {
        // Bộ lưu không có trường (dữ liệu cũ / máy khách cũ) → bật như mặc định.
        addOk(valid("2026-07"));
        assertThat(service.forPeriod(YearMonth.of(2026, 7)).insuranceSkipOnLongUnpaid()).isTrue();
        String off = valid("2026-08").replace(
            "\"payUnusedLeaveOnTermination\":true",
            "\"payUnusedLeaveOnTermination\":true,\"insuranceSkipOnLongUnpaid\":false"
        );
        addOk(off);
        assertThat(service.forPeriod(YearMonth.of(2026, 8)).insuranceSkipOnLongUnpaid()).isFalse();
        mockMvc.perform(get(URL)).andExpect(jsonPath("$[0].insuranceSkipOnLongUnpaid").value(false));
        mockMvc.perform(get(URL)).andExpect(jsonPath("$[1].insuranceSkipOnLongUnpaid").value(true));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void boundaryValuesAreAccepted() throws Exception {
        addOk(body("2026-08", "0", "24", "[\"WORK\"]"));
        addOk(body("2026-09", "1", "0.5", "[\"WORK\"]"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void settingWithApprovedPeriodInRangeIsLocked() throws Exception {
        long first = addOk(valid("2026-07"));
        long second = addOk(body("2026-10", "0.09", "8", "[\"WORK\"]"));
        approvePeriod("2026-08");
        mockMvc
            .perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + first + ")].locked").value(true))
            .andExpect(jsonPath("$[?(@.id == " + second + ")].locked").value(false));
        mockMvc
            .perform(
                put(URL + "/" + first)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(valid("2026-07"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcsettinglocked"))
            .andExpect(jsonPath("$.detail").value("Bộ thông số này đã dùng cho kỳ đã duyệt, hãy thêm bộ mới."));
        mockMvc
            .perform(delete(URL + "/" + first))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcsettinglocked"));
        // Bộ sau chưa có kỳ đã duyệt trong khoảng hiệu lực: sửa, xóa được.
        mockMvc
            .perform(
                put(URL + "/" + second)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("2026-11", "0.07", "8", "[\"WORK\"]"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bhxhEmployee").value(0.07))
            .andExpect(jsonPath("$.effectiveFrom").value("2026-11"));
        mockMvc.perform(delete(URL + "/" + second)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void approvedPeriodBeforeEffectiveMonthDoesNotLock() throws Exception {
        long id = addOk(valid("2026-07"));
        approvePeriod("2026-06");
        mockMvc.perform(get(URL)).andExpect(jsonPath("$[0].locked").value(false));
        mockMvc
            .perform(
                put(URL + "/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(valid("2026-08"))
            )
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void updateRejectsDuplicateMonthOfAnotherSettingButAllowsOwn() throws Exception {
        long first = addOk(valid("2026-07"));
        addOk(valid("2026-10"));
        mockMvc
            .perform(
                put(URL + "/" + first)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(valid("2026-10"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcsettinginvalid"));
        mockMvc
            .perform(
                put(URL + "/" + first)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("2026-07", "0.06", "8", "[\"WORK\"]"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bhxhEmployee").value(0.06));
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void hrReadsButCannotWrite() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isOk());
        add(valid("2026-07")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantReads() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isOk());
        mockMvc.perform(delete(URL + "/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_NHAN_VIEN")
    void employeeCannotRead() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void employeeRecordReadsAndWritesPitCommitment08() throws Exception {
        String res = mockMvc
            .perform(
                post("/api/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"fullName\":\"Nguyễn Cam Kết\",\"personType\":\"NHAN_VIEN\",\"joinDate\":\"2026-02-03\",\"dependants\":0,\"pitCommitment08\":true}"
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pitCommitment08").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
        int id = JsonPath.read(res, "$.id");
        String code = JsonPath.read(res, "$.code");
        mockMvc.perform(get("/api/employees/" + id)).andExpect(jsonPath("$.pitCommitment08").value(true));
        // PUT không gửi trường này: giữ giá trị đã lưu.
        mockMvc
            .perform(
                put("/api/employees/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"id\":" +
                            id +
                            ",\"code\":\"" +
                            code +
                            "\",\"fullName\":\"Nguyễn Cam Kết\",\"personType\":\"NHAN_VIEN\",\"joinDate\":\"2026-02-03\",\"dependants\":0}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pitCommitment08").value(true));
        mockMvc
            .perform(
                put("/api/employees/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"id\":" +
                            id +
                            ",\"code\":\"" +
                            code +
                            "\",\"fullName\":\"Nguyễn Cam Kết\",\"personType\":\"NHAN_VIEN\",\"joinDate\":\"2026-02-03\",\"dependants\":0,\"pitCommitment08\":false}"
                    )
            )
            .andExpect(jsonPath("$.pitCommitment08").value(false));
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void newEmployeeWithoutFieldDefaultsToFalse() throws Exception {
        mockMvc
            .perform(
                post("/api/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fullName\":\"Trần Mặc Định\",\"personType\":\"NHAN_VIEN\",\"joinDate\":\"2026-02-03\",\"dependants\":0}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pitCommitment08").value(false));
    }

    @Test
    void existingVersionsDefaultToImportedFile() {
        jdbc.update("insert into payroll_period (period, status) values ('2026-05', 'NHAP')");
        Long periodId = jdbc.queryForObject("select id from payroll_period where period = '2026-05'", Long.class);
        jdbc.update(
            "insert into payroll_version (id, period_id, version, file_name, storage_path, imported_at) values " +
                "(nextval('sequence_generator'), ?, 1, 'a.xlsx', 'x', now())",
            periodId
        );
        assertThat(jdbc.queryForObject("select source from payroll_version where period_id = ?", String.class, periodId)).isEqualTo(
            "NHAP_FILE"
        );
    }
}
