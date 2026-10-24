package vn.doit.hrm.service.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentPeriod;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentPeriodRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.service.leave.dto.LeaveSettingDTO;

/**
 * Phép năm cộng thêm theo thâm niên: thâm niên tính đến ngày cuối của tháng được cộng, gồm các lần làm nhân viên chính thức trước;
 * chu kỳ và số ngày cộng chỉnh trong cài đặt, chỉ ảnh hưởng các tháng chưa cộng. Hôm nay 20/05/2026 (đồng hồ cố định).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(LeaveSeniorityIT.FixedClockConfig.class)
class LeaveSeniorityIT {

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-05-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final RequestPostProcessor ADMIN = user("admin-seniority").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor HR = user("hr-seniority").authorities(() -> "ROLE_HR");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LeaveAccrualService accrual;

    @Autowired
    private LeaveSettingService settings;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentPeriodRepository periodRepository;

    @Autowired
    private LeaveLedgerEntryRepository entries;

    private Employee staff(String code, LocalDate join) {
        return LeaveFixtures.employee(employeeRepository, code, PersonType.NHAN_VIEN, join);
    }

    private void period(Employee e, LocalDate join, LocalDate last, PersonType type) {
        EmploymentPeriod p = new EmploymentPeriod();
        p.setEmployeeId(e.getId());
        p.setJoinDate(join);
        p.setLastWorkingDay(last);
        p.setTerminationDate(last.plusDays(1));
        p.setPersonType(type);
        periodRepository.saveAndFlush(p);
    }

    private BigDecimal credited(Employee e, YearMonth month) {
        accrual.settle(e.getId(), month, month.atDay(1));
        long ref = month.getYear() * 100L + month.getMonthValue();
        List<LeaveLedgerEntry> rows = entries
            .findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId())
            .stream()
            .filter(r -> r.getReason() == LedgerReason.CONG_THANG && Long.valueOf(ref).equals(r.getRefId()))
            .toList();
        assertThat(rows).hasSize(1);
        return rows.get(0).getAmount();
    }

    private void cycle(int years, int extra) {
        LeaveSettingDTO s = settings.get();
        settings.update(
            new LeaveSettingDTO(
                s.advanceDays(),
                s.directorThresholdDays(),
                s.otMonthCap(),
                s.otYearCap(),
                s.carryExpiryMonth(),
                s.carryExpiryDay(),
                s.pastRequestDays(),
                s.otConfirmDay(),
                years,
                extra
            )
        );
    }

    @Test
    void monthOfAnniversaryGetsExtra() {
        Employee e = staff("NV9701", LocalDate.of(2022, 6, 15));

        assertThat(credited(e, YearMonth.of(2027, 5))).isEqualByComparingTo("1.00");
        // Đủ 5 năm ngày 15/06/2027: tháng 06 được cộng trọn 13/12.
        assertThat(credited(e, YearMonth.of(2027, 6))).isEqualByComparingTo("1.08");
    }

    @Test
    void seniorityIncludesPriorPeriods() {
        // Lần trước 01/01/2019–31/12/2021 (3 năm), lần này từ 15/05/2024: đến 31/05/2026 hơn 2 năm → tổng hơn 5 năm; đến 30/04/2026
        // thì chưa đủ. Chỉ tính lần này thì chưa tới 2 năm 1 tháng.
        Employee e = staff("NV9702", LocalDate.of(2024, 5, 15));
        period(e, LocalDate.of(2019, 1, 1), LocalDate.of(2021, 12, 31), PersonType.NHAN_VIEN);

        assertThat(credited(e, YearMonth.of(2026, 4))).isEqualByComparingTo("1.00");
        assertThat(credited(e, YearMonth.of(2026, 5))).isEqualByComparingTo("1.08");
    }

    @Test
    void periodWithoutPersonTypeCountsAsStaff() {
        Employee e = staff("NV9703", LocalDate.of(2024, 5, 15));
        period(e, LocalDate.of(2019, 1, 1), LocalDate.of(2021, 12, 31), null);

        assertThat(credited(e, YearMonth.of(2026, 5))).isEqualByComparingTo("1.08");
    }

    @Test
    void internPeriodsNotCounted() {
        Employee e = staff("NV9704", LocalDate.of(2024, 6, 1));
        period(e, LocalDate.of(2019, 3, 1), LocalDate.of(2021, 2, 28), PersonType.THUC_TAP);
        period(e, LocalDate.of(2021, 3, 1), LocalDate.of(2022, 2, 28), PersonType.DICH_VU);

        assertThat(credited(e, YearMonth.of(2026, 5))).isEqualByComparingTo("1.00");
    }

    @Test
    void settingChangeNotRetroactive() {
        // Vào 10/03/2023: tháng 05/2026 đủ 3 năm, chưa đủ 5.
        Employee e = staff("NV9705", LocalDate.of(2023, 3, 10));
        assertThat(credited(e, YearMonth.of(2026, 5))).isEqualByComparingTo("1.00");

        cycle(3, 1);

        // Chạy lại tháng 05 không ghi thêm, dòng đã cộng giữ nguyên; tháng 06 theo chu kỳ mới.
        assertThat(credited(e, YearMonth.of(2026, 5))).isEqualByComparingTo("1.00");
        assertThat(credited(e, YearMonth.of(2026, 6))).isEqualByComparingTo("1.08");
    }

    @Test
    void extraDaysPerCycleFromSetting() {
        Employee e = staff("NV9706", LocalDate.of(2020, 1, 1));
        cycle(3, 2);
        // 6 năm tại 31/05/2026 → 12 + 2 × 2 = 16 → 1,33/tháng.
        assertThat(credited(e, YearMonth.of(2026, 5))).isEqualByComparingTo("1.33");
    }

    @Test
    void zeroExtraDaysTurnsSeniorityOff() {
        Employee e = staff("NV9707", LocalDate.of(2010, 1, 1));
        cycle(5, 0);
        assertThat(credited(e, YearMonth.of(2026, 5))).isEqualByComparingTo("1.00");
    }

    private String body(Object cycleYears, Object extraDays) {
        return (
            "{\"advanceDays\":1,\"directorThresholdDays\":3,\"otMonthCap\":40,\"otYearCap\":200,\"carryExpiryMonth\":12," +
            "\"carryExpiryDay\":31,\"pastRequestDays\":30,\"otConfirmDay\":5,\"seniorityCycleYears\":" +
            cycleYears +
            ",\"seniorityExtraDays\":" +
            extraDays +
            "}"
        );
    }

    @Test
    void settingValidation() throws Exception {
        mockMvc
            .perform(get("/api/leave/settings").with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.seniorityCycleYears").value(5))
            .andExpect(jsonPath("$.seniorityExtraDays").value(1));
        for (String bad : List.of(body(0, 1), body(11, 1), body(5, 6), body(5, -1))) {
            mockMvc
                .perform(put("/api/leave/settings").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.leavesettinginvalid"));
        }
        mockMvc
            .perform(put("/api/leave/settings").with(HR).contentType(MediaType.APPLICATION_JSON).content(body(3, 2)))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(put("/api/leave/settings").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body(10, 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.seniorityCycleYears").value(10))
            .andExpect(jsonPath("$.seniorityExtraDays").value(0));
        mockMvc
            .perform(put("/api/leave/settings").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body(1, 5)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.seniorityCycleYears").value(1))
            .andExpect(jsonPath("$.seniorityExtraDays").value(5));
    }

    @Test
    void balanceShowsSeniorityFormula() throws Exception {
        // Vào 10/05/2021: đến 31/05/2026 đủ 5 năm → 12 + 1 = 13.
        Employee e = staff("NV9708", LocalDate.of(2021, 5, 10));
        mockMvc
            .perform(get("/api/leave/employees/{id}", e.getId()).with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance.annual.baseAnnualDays").value(12))
            .andExpect(jsonPath("$.balance.annual.seniorityYears").value(5))
            .andExpect(jsonPath("$.balance.annual.seniorityExtraDays").value(1))
            .andExpect(jsonPath("$.balance.annual.annualDays").value(13))
            .andExpect(jsonPath("$.balance.annual.advanceDays").value(1));
        Employee fresh = staff("NV9709", LocalDate.of(2025, 1, 2));
        mockMvc
            .perform(get("/api/leave/balances").with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.code == 'NV9709')].annual.seniorityYears").value(1))
            .andExpect(jsonPath("$[?(@.code == 'NV9709')].annual.seniorityExtraDays").value(0))
            .andExpect(jsonPath("$[?(@.code == 'NV9709')].annual.annualDays").value(12))
            .andExpect(jsonPath("$[?(@.code == 'NV9708')].annual.annualDays").value(13));
        assertThat(fresh.getId()).isNotNull();
    }
}
