package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentPeriod;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentPeriodRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.IdNumbers;
import vn.doit.hrm.service.leave.LeaveAccrualJob;
import vn.doit.hrm.service.leave.LeaveAccrualService;
import vn.doit.hrm.service.leave.LeaveLedgerService;

/**
 * Nhận làm lại người đã nghỉ: giữ mã, ghi lần làm việc trước, mở lại tài khoản, chặn hoàn tác nghỉ việc cũ, sổ phép tính từ ngày vào
 * làm mới. Hôm nay 03/11/2026 (đồng hồ cố định).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(EmployeeRehireIT.FixedClockConfig.class)
class EmployeeRehireIT {

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final RequestPostProcessor HR = user("hr-rehire").authorities(() -> "ROLE_HR");
    private static final LocalDate OLD_JOIN = LocalDate.of(2019, 1, 2);
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 5, 15);
    private static final LocalDate NEW_JOIN = LocalDate.of(2026, 11, 3);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmploymentPeriodRepository periodRepository;

    @Autowired
    private TerminationRecordRepository terminationRecordRepository;

    @Autowired
    private LeaveLedgerEntryRepository ledgerRepository;

    @Autowired
    private LeaveAccrualJob accrualJob;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private static int seq;

    private User account() {
        User u = new User();
        u.setLogin("rh-acc-" + ++seq + "-" + (System.nanoTime() % 100000));
        u.setActivated(true);
        u.setLangKey("vi");
        u.setPassword("$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234");
        return userRepository.saveAndFlush(u);
    }

    private Employee employee(String code, EmploymentStatus status, User user) {
        Employee e = new Employee()
            .code(code)
            .personType(PersonType.NHAN_VIEN)
            .fullName("Nguyễn Văn Quay Lại " + code)
            .searchText(code.toLowerCase() + " nguyen van quay lai")
            .dependants(0)
            .phone("0900000888")
            .status(status)
            .position("Kỹ sư")
            .joinDate(OLD_JOIN)
            .user(user);
        if (status == EmploymentStatus.DA_NGHI) {
            e.setLastWorkingDay(LAST_DAY);
            e.setTerminationDate(LAST_DAY.plusDays(1));
            e.setTerminationType(TerminationType.TU_NGHI);
            e.setTerminationReason("Chuyển việc");
        }
        return employeeRepository.saveAndFlush(e);
    }

    /** Hồ sơ đang làm, cho nghỉ qua API (ngày làm việc cuối 15/05/2026 → áp dụng ngay). */
    private Employee terminated(String code, User user) throws Exception {
        Employee e = employee(code, EmploymentStatus.DANG_LAM_VIEC, user);
        terminate(e, LAST_DAY.toString()).andExpect(status().isOk());
        return e;
    }

    private ResultActions terminate(Employee e, String lastWorkingDay) throws Exception {
        String body =
            "{\"lastWorkingDay\":\"" + lastWorkingDay + "\",\"terminationType\":\"TU_NGHI\",\"terminationReason\":\"Chuyển việc\"}";
        return mockMvc.perform(
            post("/api/employees/{id}/terminate", e.getId()).with(HR).contentType(MediaType.APPLICATION_JSON).content(body)
        );
    }

    private ResultActions undo(Employee e) throws Exception {
        return mockMvc.perform(post("/api/employees/{id}/termination/undo", e.getId()).with(HR));
    }

    private ResultActions rehire(Employee e, String body) throws Exception {
        return mockMvc.perform(
            post("/api/employees/{id}/rehire", e.getId()).with(HR).contentType(MediaType.APPLICATION_JSON).content(body)
        );
    }

    private ResultActions rehire(Employee e, LocalDate joinDate) throws Exception {
        return rehire(e, "{\"joinDate\":\"" + joinDate + "\"}");
    }

    @Test
    void rehireRestoresActive() throws Exception {
        User acc = account();
        Employee e = terminated("NV9301", acc);
        assertThat(userRepository.findById(acc.getId()).orElseThrow().isActivated()).isFalse();
        TerminationRecord record = terminationRecordRepository
            .findFirstByEmployeeIdAndStatusOrderByIdDesc(e.getId(), TerminationRecordStatus.APPLIED)
            .orElseThrow();

        rehire(e, NEW_JOIN)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DANG_LAM_VIEC"))
            .andExpect(jsonPath("$.joinDate").value("2026-11-03"))
            .andExpect(jsonPath("$.code").value("NV9301"));

        Employee reloaded = employeeRepository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
        assertThat(reloaded.getJoinDate()).isEqualTo(NEW_JOIN);
        assertThat(reloaded.getCode()).isEqualTo("NV9301");
        assertThat(reloaded.getLastWorkingDay()).isNull();
        assertThat(reloaded.getTerminationDate()).isNull();
        assertThat(reloaded.getTerminationType()).isNull();
        assertThat(reloaded.getTerminationReason()).isNull();
        assertThat(reloaded.getPosition()).isEqualTo("Kỹ sư");

        List<EmploymentPeriod> periods = periodRepository.findByEmployeeIdOrderByJoinDateAscIdAsc(e.getId());
        assertThat(periods).hasSize(1);
        EmploymentPeriod p = periods.get(0);
        assertThat(p.getJoinDate()).isEqualTo(OLD_JOIN);
        assertThat(p.getLastWorkingDay()).isEqualTo(LAST_DAY);
        assertThat(p.getTerminationDate()).isEqualTo(LAST_DAY.plusDays(1));
        assertThat(p.getTerminationType()).isEqualTo(TerminationType.TU_NGHI);
        assertThat(p.getTerminationReason()).isEqualTo("Chuyển việc");
        assertThat(p.getTerminationRecordId()).isEqualTo(record.getId());
        assertThat(p.getCreatedBy()).isEqualTo("hr-rehire");

        assertThat(userRepository.findById(acc.getId()).orElseThrow().isActivated()).isTrue();
    }

    @Test
    void rehireUpdatesOptionalFields() throws Exception {
        Employee e = terminated("NV9302", null);
        rehire(e, "{\"joinDate\":\"2026-11-03\",\"position\":\"Trưởng nhóm\",\"personType\":\"THUC_TAP\"}").andExpect(status().isOk());
        Employee reloaded = employeeRepository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getPosition()).isEqualTo("Trưởng nhóm");
        assertThat(reloaded.getPersonType()).isEqualTo(PersonType.THUC_TAP);
        assertThat(reloaded.getCode()).isEqualTo("NV9302");
        // Lần làm việc trước giữ loại hồ sơ trước khi đổi (R27).
        List<EmploymentPeriod> periods = periodRepository.findByEmployeeIdOrderByJoinDateAscIdAsc(e.getId());
        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).getPersonType()).isEqualTo(PersonType.NHAN_VIEN);
    }

    @Test
    void rehireDateBeforeLeaveRejected() throws Exception {
        Employee e = terminated("NV9303", null);
        rehire(e, LocalDate.of(2026, 5, 10))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.rehiredatebeforeleave"));
        rehire(e, LAST_DAY).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.rehiredatebeforeleave"));
        rehire(e, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.rehiredaterequired"));
        assertThat(employeeRepository.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(EmploymentStatus.DA_NGHI);
        assertThat(periodRepository.findByEmployeeIdOrderByJoinDateAscIdAsc(e.getId())).isEmpty();
    }

    @Test
    void rehireRequiresTerminatedNotArchived() throws Exception {
        Employee active = employee("NV9304", EmploymentStatus.DANG_LAM_VIEC, null);
        rehire(active, NEW_JOIN).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.rehirenotterminated"));

        Employee archived = employee("NV9305", EmploymentStatus.DA_NGHI, null);
        archived.setArchivedAt(Instant.parse("2026-10-01T00:00:00Z"));
        archived.setArchivedBy("hr");
        employeeRepository.saveAndFlush(archived);
        rehire(archived, NEW_JOIN).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.employeearchived"));
        assertThat(periodRepository.findByEmployeeIdOrderByJoinDateAscIdAsc(archived.getId())).isEmpty();
    }

    @Test
    void rehireWithScheduledTerminationRejected() throws Exception {
        Employee e = employee("NV9306", EmploymentStatus.DANG_LAM_VIEC, null);
        terminate(e, "2026-11-20").andExpect(status().isOk());
        rehire(e, NEW_JOIN).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.terminationscheduled"));
    }

    @Test
    void rehireForbiddenForAccountant() throws Exception {
        Employee e = terminated("NV9307", null);
        mockMvc
            .perform(
                post("/api/employees/{id}/rehire", e.getId())
                    .with(user("kt-rehire").authorities(() -> "ROLE_KE_TOAN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"joinDate\":\"2026-11-03\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void undoOldTerminationBlockedAfterRehire() throws Exception {
        Employee e = terminated("NV9308", account());
        rehire(e, NEW_JOIN).andExpect(status().isOk());

        undo(e)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.undoconflict"))
            .andExpect(jsonPath("$.detail").value(containsString("Nhân viên đã được nhận làm lại")));
        Employee reloaded = employeeRepository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
        assertThat(reloaded.getJoinDate()).isEqualTo(NEW_JOIN);
    }

    @Test
    void secondTerminationUndoWorks() throws Exception {
        Employee e = terminated("NV9309", account());
        // Nhận làm lại từ 01/06/2026, rồi nghỉ lần hai với ngày làm việc cuối 31/10/2026 (áp dụng ngay).
        rehire(e, LocalDate.of(2026, 6, 1)).andExpect(status().isOk());
        terminate(e, "2026-10-31").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_NGHI"));

        undo(e).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DANG_LAM_VIEC"));
        Employee reloaded = employeeRepository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
        assertThat(reloaded.getJoinDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(reloaded.getLastWorkingDay()).isNull();

        // Lần cũ vẫn bị chặn khi không còn lần nghỉ nào khác để hoàn tác.
        undo(e).andExpect(status().isBadRequest());
    }

    private static final long FACTOR = 1_000_000L;

    @Autowired
    private vn.doit.hrm.service.reminder.WorkCalendarService calendarService;

    /** Phần còn đang cộng của tháng: dòng cộng, cộng bù, thu hồi khi nghỉ việc và hoàn tác thu hồi. */
    private java.math.BigDecimal net(Long employeeId, long yyyymm) {
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (LeaveLedgerEntry r : ledgerRepository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(employeeId)) {
            if (r.getPool() != vn.doit.hr.leave.Pool.PHEP_NAM || r.getRefId() == null) {
                continue;
            }
            boolean month =
                r.getReason() == LedgerReason.CONG_THANG && LeaveLedgerService.REF_MONTH.equals(r.getRefType()) && r.getRefId() == yyyymm;
            boolean topUp = LeaveAccrualService.REF_ACCRUAL_TOPUP.equals(r.getRefType()) && r.getRefId() % FACTOR == yyyymm;
            boolean revoke = LeaveAccrualService.REF_ACCRUAL_REVOKE.equals(r.getRefType()) && r.getRefId() % FACTOR == yyyymm;
            if (month || topUp || revoke) {
                sum = sum.add(r.getAmount());
            }
        }
        return sum;
    }

    private List<LeaveLedgerEntry> topUps(Long employeeId, long yyyymm) {
        return ledgerRepository
            .findByEmployeeIdOrderByEffectiveDateAscIdAsc(employeeId)
            .stream()
            .filter(r -> LeaveAccrualService.REF_ACCRUAL_TOPUP.equals(r.getRefType()) && r.getRefId() % FACTOR == yyyymm)
            .toList();
    }

    /** Mức một tháng của lần làm việc mới (ngày vào làm mới, chưa nghỉ, mức 12). */
    private java.math.BigDecimal entitled(LocalDate join, YearMonth month) {
        return vn.doit.hr.leave.AnnualAccrual.monthly(12, join, null, month, calendarService.calendar(month.atDay(15)));
    }

    /** R8: ngày làm việc cuối 31/10, cho nghỉ ngày 02/11 sau khi tháng 11 đã được cộng (rồi bị thu hồi), nhận làm lại 03/11. */
    @Test
    void revokedMonthIsToppedUpOnceAfterRehire() throws Exception {
        Employee e = employee("NV9320", EmploymentStatus.DANG_LAM_VIEC, null);
        accrualJob.runMonth(YearMonth.of(2026, 11));
        assertThat(net(e.getId(), 202611L)).isPositive();
        terminate(e, "2026-10-31").andExpect(status().isOk());
        assertThat(net(e.getId(), 202611L)).isZero();

        rehire(e, NEW_JOIN).andExpect(status().isOk());

        // Cộng bù ngay khi nhận làm lại, đúng mức của lần làm việc mới (12 ngày/năm, không theo thâm niên lần trước).
        assertThat(net(e.getId(), 202611L)).isEqualByComparingTo("1");
        assertThat(topUps(e.getId(), 202611L)).hasSize(1);
        accrualJob.runMonth(YearMonth.of(2026, 11));
        accrualJob.runMonth(YearMonth.of(2026, 11));
        assertThat(topUps(e.getId(), 202611L)).hasSize(1);
        assertThat(net(e.getId(), 202611L)).isEqualByComparingTo("1");
    }

    /** Vào lại cùng tháng: tháng 10 bị thu hồi khi nghỉ (09/10), vào lại 12/10 thì tháng 10 được cộng theo quy tắc 50% của lần mới. */
    @Test
    void sameMonthRejoinAfterRevokedMonth() throws Exception {
        Employee e = employee("NV9321", EmploymentStatus.DANG_LAM_VIEC, null);
        accrualJob.runMonth(YearMonth.of(2026, 10));
        accrualJob.runMonth(YearMonth.of(2026, 11));
        terminate(e, "2026-10-09").andExpect(status().isOk());
        assertThat(net(e.getId(), 202610L)).isZero();

        LocalDate join = LocalDate.of(2026, 10, 12);
        rehire(e, join).andExpect(status().isOk());

        java.math.BigDecimal expectedOct = entitled(join, YearMonth.of(2026, 10));
        assertThat(expectedOct).isPositive();
        assertThat(net(e.getId(), 202610L)).isEqualByComparingTo(expectedOct);
        assertThat(net(e.getId(), 202611L)).isEqualByComparingTo("1");
        accrualJob.runMonth(YearMonth.of(2026, 11));
        assertThat(topUps(e.getId(), 202610L)).hasSize(1);
        assertThat(topUps(e.getId(), 202611L)).hasSize(1);
    }

    /** Vào lại cùng tháng khi lần trước đã được hưởng tháng đó (15/10 → 20/10): không cộng bù quá mức, không ghi số âm. */
    @Test
    void sameMonthRejoinAfterEarnedMonth() throws Exception {
        Employee e = employee("NV9322", EmploymentStatus.DANG_LAM_VIEC, null);
        accrualJob.runMonth(YearMonth.of(2026, 10));
        terminate(e, "2026-10-15").andExpect(status().isOk());
        java.math.BigDecimal before = net(e.getId(), 202610L);

        LocalDate join = LocalDate.of(2026, 10, 20);
        rehire(e, join).andExpect(status().isOk());
        accrualJob.runMonth(YearMonth.of(2026, 11));

        java.math.BigDecimal expected = before.max(entitled(join, YearMonth.of(2026, 10)));
        assertThat(net(e.getId(), 202610L)).isEqualByComparingTo(expected);
        assertThat(topUps(e.getId(), 202610L))
            .allSatisfy(r -> assertThat(r.getAmount()).isPositive())
            .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void normalEmployeeHasNoTopUp() throws Exception {
        Employee e = employee("NV9323", EmploymentStatus.DANG_LAM_VIEC, null);
        accrualJob.runMonth(YearMonth.of(2026, 11));
        accrualJob.runMonth(YearMonth.of(2026, 11));
        assertThat(topUps(e.getId(), 202611L)).isEmpty();
        assertThat(
            ledgerRepository
                .findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId())
                .stream()
                .filter(r -> r.getReason() == LedgerReason.CONG_THANG)
                .toList()
        ).hasSize(1);
    }

    @Test
    void leaveRestartsFromNewJoinDate() throws Exception {
        Employee e = terminated("NV9310", null);
        rehire(e, NEW_JOIN).andExpect(status().isOk());

        accrualJob.runMonth(YearMonth.of(2026, 10));
        accrualJob.runMonth(YearMonth.of(2026, 11));

        List<LeaveLedgerEntry> rows = ledgerRepository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId());
        List<LeaveLedgerEntry> november = rows
            .stream()
            .filter(
                r ->
                    r.getReason() == LedgerReason.CONG_THANG &&
                    LeaveLedgerService.REF_MONTH.equals(r.getRefType()) &&
                    Long.valueOf(202611L).equals(r.getRefId())
            )
            .toList();
        assertThat(november).hasSize(1);
        assertThat(november.get(0).getNote()).contains("mức 12 ngày/năm");
        assertThat(november.get(0).getAmount()).isEqualByComparingTo("1");
        for (LeaveLedgerEntry r : rows) {
            boolean inGap =
                !r.getEffectiveDate().isBefore(LocalDate.of(2026, 6, 1)) && !r.getEffectiveDate().isAfter(LocalDate.of(2026, 10, 31));
            assertThat(inGap).as("không có dòng nào cho tháng 06–10/2026: %s", r.getNote()).isFalse();
            if (r.getReason() == LedgerReason.CONG_THANG && r.getRefId() != null) {
                assertThat(r.getRefId() >= 202606L && r.getRefId() <= 202610L)
                    .as("cộng phép tháng 06–10/2026")
                    .isFalse();
            }
        }
    }

    @Test
    void adminAccountNotTouched() throws Exception {
        User admin = userRepository.findOneByLogin(IdNumbers.SYSTEM_ADMIN_LOGIN).orElseThrow();
        admin.setActivated(false);
        userRepository.saveAndFlush(admin);
        Employee e = employee("NV9311", EmploymentStatus.DA_NGHI, admin);

        rehire(e, NEW_JOIN).andExpect(status().isOk());

        assertThat(userRepository.findOneByLogin(IdNumbers.SYSTEM_ADMIN_LOGIN).orElseThrow().isActivated()).isFalse();
        // Hồ sơ cũ không có lịch sử nghỉ việc: lần làm việc vẫn được ghi, không gắn lần cho nghỉ.
        List<EmploymentPeriod> periods = periodRepository.findByEmployeeIdOrderByJoinDateAscIdAsc(e.getId());
        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).getTerminationRecordId()).isNull();
    }

    @Autowired
    private vn.doit.hrm.service.dashboard.HrReportService hrReportService;

    @Autowired
    private vn.doit.hrm.service.dashboard.DashboardService dashboardService;

    @Autowired
    private vn.doit.hrm.service.leave.LeaveAccountingSummaryService accountingSummaryService;

    /** Ảnh chụp số liệu lịch sử 2024–2025: biến động tháng, người nghỉ (mã, ngày vào, ngày nghỉ, thâm niên). */
    private List<String> history2024to2025() {
        List<vn.doit.hr.report.EmploymentRecord> records = hrReportService.employmentRecords();
        List<String> out = new java.util.ArrayList<>();
        YearMonth from = YearMonth.of(2024, 1);
        YearMonth to = YearMonth.of(2025, 12);
        vn.doit.hr.report.HeadcountReport.monthly(records, from, to).forEach(m -> out.add(m.toString()));
        vn.doit.hr.report.TurnoverReport
            .analyze(records, from, to)
            .leavers()
            .forEach(r -> out.add(r.code() + " " + r.hireDate() + " " + r.terminationDate() + " " + r.tenureMonthsAtExit() + " " + r.exitType()));
        return out;
    }

    /** R18: nhận làm lại không viết lại tuyển mới 2024, nghỉ việc 06/2025 và quân số 2024–2025. */
    @Test
    void rehireKeepsPastHeadcountAndTurnover() throws Exception {
        Employee e = employee("NV9330", EmploymentStatus.DANG_LAM_VIEC, null);
        e.setJoinDate(LocalDate.of(2024, 3, 4));
        e.setStatus(EmploymentStatus.DA_NGHI);
        e.setLastWorkingDay(LocalDate.of(2025, 6, 30));
        e.setTerminationDate(LocalDate.of(2025, 7, 1));
        e.setTerminationType(TerminationType.TU_NGHI);
        e.setTerminationReason("Chuyển việc");
        employeeRepository.saveAndFlush(e);
        List<String> before = history2024to2025();
        assertThat(before).anyMatch(l -> l.startsWith("NV9330 2024-03-04 2025-06-30"));
        var dashBefore = dashboardService.dashboard(false).movement();
        var accountingBefore = accountingSummaryService.summary("2025-03").rows().stream().map(r -> r.code()).toList();
        assertThat(accountingBefore).contains("NV9330");

        rehire(e, LocalDate.of(2026, 10, 1)).andExpect(status().isOk());

        assertThat(history2024to2025()).isEqualTo(before);
        // Lần làm việc trước và lần hiện tại: hai bản ghi.
        assertThat(hrReportService.employmentRecords().stream().filter(r -> r.code().equals("NV9330")).map(r -> r.hireDate()))
            .containsExactlyInAnyOrder(LocalDate.of(2024, 3, 4), LocalDate.of(2026, 10, 1));
        // Tổng quan: các tháng trước tháng nhận làm lại không đổi; tháng 10/2026 thêm một người vào.
        var dashAfter = dashboardService.dashboard(false).movement();
        for (int i = 0; i < dashBefore.size(); i++) {
            if (YearMonth.parse(dashBefore.get(i).month()).isBefore(YearMonth.of(2026, 10))) {
                assertThat(dashAfter.get(i)).isEqualTo(dashBefore.get(i));
            }
        }
        assertThat(accountingSummaryService.summary("2025-03").rows().stream().map(r -> r.code()).toList()).contains("NV9330");
        assertThat(accountingSummaryService.summary("2025-12").rows().stream().map(r -> r.code()).toList()).doesNotContain("NV9330");
    }

    /** R19: hồ sơ tạo nhầm đã lưu trữ không được cộng phép tháng; khôi phục vẫn y nguyên. */
    @Test
    void archivedMistakenProfileGetsNoAccrual() throws Exception {
        Employee e = employee("NV9331", EmploymentStatus.DANG_LAM_VIEC, null);
        e.setJoinDate(NEW_JOIN);
        employeeRepository.saveAndFlush(e);
        mockMvc
            .perform(post("/api/employees/{id}/archive", e.getId()).with(HR).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());

        accrualJob.runMonth(YearMonth.of(2026, 11));
        accrualJob.runMonth(YearMonth.of(2026, 12));
        assertThat(ledgerRepository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId())).isEmpty();

        mockMvc.perform(post("/api/employees/{id}/restore", e.getId()).with(HR)).andExpect(status().isOk());
        Employee reloaded = employeeRepository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
        assertThat(reloaded.getJoinDate()).isEqualTo(NEW_JOIN);
        assertThat(reloaded.isArchived()).isFalse();
        assertThat(ledgerRepository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId())).isEmpty();
    }

    /** R20: ngày vào làm mới lùi về 03/08/2026 – các tháng 08–11 đã cộng rồi bị thu hồi khi nghỉ đều được cộng đúng. */
    @Test
    void backdatedRehireSettlesEveryMonth() throws Exception {
        Employee e = employee("NV9332", EmploymentStatus.DANG_LAM_VIEC, null);
        for (int m = 8; m <= 11; m++) {
            accrualJob.runMonth(YearMonth.of(2026, m));
        }
        terminate(e, "2026-07-31").andExpect(status().isOk());
        for (long m = 202608L; m <= 202611L; m++) {
            assertThat(net(e.getId(), m)).as("tháng %d sau khi nghỉ", m).isZero();
        }

        LocalDate join = LocalDate.of(2026, 8, 3);
        rehire(e, join).andExpect(status().isOk());

        for (int m = 8; m <= 11; m++) {
            YearMonth month = YearMonth.of(2026, m);
            long yyyymm = 202600L + m;
            assertThat(net(e.getId(), yyyymm)).as("tháng %d", yyyymm).isEqualByComparingTo(entitled(join, month));
        }
        int rows = ledgerRepository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId()).size();
        accrualJob.runMonth(YearMonth.of(2026, 11));
        accrualJob.runMonth(YearMonth.of(2026, 11));
        assertThat(ledgerRepository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId())).hasSize(rows);
        for (long m = 202608L; m <= 202611L; m++) {
            assertThat(topUps(e.getId(), m)).as("tháng %d", m).hasSize(1);
        }
    }

    /** R21: tháng đã cộng của lần làm việc mới không bị tính lại khi HR đổi số ngày phép năm. */
    @Test
    void creditedMonthNotReRatedAfterRehire() throws Exception {
        Employee e = terminated("NV9333", null);
        rehire(e, NEW_JOIN).andExpect(status().isOk());
        assertThat(net(e.getId(), 202611L)).isEqualByComparingTo("1");
        int rows = ledgerRepository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId()).size();

        Employee reloaded = employeeRepository.findById(e.getId()).orElseThrow();
        reloaded.setAnnualLeaveDays(18);
        employeeRepository.saveAndFlush(reloaded);
        accrualJob.runMonth(YearMonth.of(2026, 11));

        assertThat(ledgerRepository.findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId())).hasSize(rows);
        assertThat(net(e.getId(), 202611L)).isEqualByComparingTo("1");
        assertThat(topUps(e.getId(), 202611L)).isEmpty();
    }

    /** Nhật ký thay đổi đọc bảng _aud (chỉ ghi khi commit): test này tự commit và tự dọn. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void historyShowsPeriods() throws Exception {
        Long[] ids = new Long[2];
        try {
            ids[0] = tx.execute(s -> employee("NV9312", EmploymentStatus.DA_NGHI, null).getId());
            ids[1] = tx.execute(s -> employee("NV9313", EmploymentStatus.DA_NGHI, null).getId());
            Employee rehired = employeeRepository.findById(ids[0]).orElseThrow();
            Employee archived = employeeRepository.findById(ids[1]).orElseThrow();

            rehire(rehired, NEW_JOIN).andExpect(status().isOk());
            mockMvc
                .perform(
                    post("/api/employees/{id}/archive", archived.getId()).with(HR).contentType(MediaType.APPLICATION_JSON).content("{}")
                )
                .andExpect(status().isOk());
            mockMvc.perform(post("/api/employees/{id}/restore", archived.getId()).with(HR)).andExpect(status().isOk());

            JsonNode h1 = history(rehired);
            boolean hasPeriod = false;
            for (JsonNode entry : h1) {
                if ("EmploymentPeriod".equals(entry.get("entity").asText())) {
                    hasPeriod = true;
                    assertThat(entry.get("action").asText()).isEqualTo("TẠO");
                    assertThat(entry.toString()).contains("2019-01-02").contains("2026-05-15");
                }
            }
            assertThat(hasPeriod).as("có mục lần làm việc").isTrue();

            JsonNode h2 = history(archived);
            List<String> actions = new java.util.ArrayList<>();
            for (JsonNode entry : h2) {
                if ("Employee".equals(entry.get("entity").asText())) {
                    actions.add(entry.get("action").asText());
                }
            }
            assertThat(actions).contains("LƯU TRỮ", "KHÔI PHỤC");
        } finally {
            // Kết nối của pool không tự commit: dọn trong một transaction.
            tx.executeWithoutResult(s -> {
                for (Long id : ids) {
                    if (id != null) {
                        jdbc.update("delete from leave_ledger_entry where employee_id = ?", id);
                        jdbc.update("delete from employment_period where employee_id = ?", id);
                        jdbc.update("delete from employee where id = ?", id);
                    }
                }
            });
        }
    }

    private JsonNode history(Employee e) throws Exception {
        String body = mockMvc
            .perform(get("/api/employees/{id}/history", e.getId()).with(HR))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return om.readTree(body);
    }
}
