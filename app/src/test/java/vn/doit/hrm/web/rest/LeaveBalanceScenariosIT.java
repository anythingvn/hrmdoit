package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceSnapshot;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AttendanceSnapshotRepository;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.attendance.AttendanceDayService;
import vn.doit.hrm.service.leave.LeaveAccrualService;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.leave.LeaveLedgerService;
import vn.doit.hrm.service.reminder.ReminderMailer;

/**
 * Hai tình huống kiểm tra: hết phép mà xin nghỉ có lương; đơn bắc từ cuối tháng này sang đầu tháng sau (số dư, chia tháng trên bảng
 * công và tổng hợp kế toán). 30/11/2026 là thứ Hai, 01/12/2026 là thứ Ba.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(LeaveBalanceScenariosIT.ClockConfig.class)
class LeaveBalanceScenariosIT {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final RequestPostProcessor HR = user("hr-kt").authorities(() -> "ROLE_HR");

    static final class MutableClock extends Clock {

        private volatile Instant now = at(LocalDate.of(2026, 11, 20));

        static Instant at(LocalDate day) {
            return day.atTime(10, 0).atZone(VN).toInstant();
        }

        void set(LocalDate day) {
            now = at(day);
        }

        @Override
        public ZoneId getZone() {
            return VN;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock scenarioClock() {
            return new MutableClock();
        }
    }

    @MockitoBean
    private ReminderMailer mailer;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveLedgerService ledger;

    @Autowired
    private LeaveAccrualService accrual;

    @Autowired
    private AttendanceDayService dayService;

    @Autowired
    private AttendanceSnapshotRepository snapshots;

    private Employee nv;
    private RequestPostProcessor nvAs;
    private RequestPostProcessor tnAs;

    @BeforeEach
    void init() {
        clock.set(LocalDate.of(2026, 11, 20));
        when(mailer.configured()).thenReturn(true);
        Department dept = LeaveFixtures.department(departmentRepository, "Phòng kiểm tra phép");
        Employee tn = person("KTTN", "kt-tn", dept);
        nv = person("KTNV", "kt-nv", dept);
        nv.setManager(tn);
        employeeRepository.saveAndFlush(nv);
        nvAs = user("kt-nv").authorities(() -> "ROLE_NHAN_VIEN");
        tnAs = user("kt-tn").authorities(() -> "ROLE_NHAN_VIEN");
    }

    private Employee person(String code, String login, Department dept) {
        Employee e = LeaveFixtures.employee(employeeRepository, code, PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        User u = LeaveFixtures.user(userRepository, login);
        u.setEmail(login + "@doit.test");
        e.setUser(userRepository.saveAndFlush(u));
        e.setDepartment(dept);
        return employeeRepository.saveAndFlush(e);
    }

    private void balanceTo(String amount) {
        if (new BigDecimal(amount).signum() == 0) {
            return;
        }
        ledger.post(
            nv.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal(amount))),
            LedgerReason.DAU_KY,
            null,
            null,
            "thử"
        );
    }

    private BigDecimal total() {
        return ledger.balance(nv.getId(), Pool.PHEP_NAM).total();
    }

    private ResultActions submit(String type, String from, String to, String first, String last, String personal) throws Exception {
        String json = "{\"type\":\"%s\",\"fromDate\":\"%s\",\"toDate\":\"%s\",\"firstPart\":\"%s\",\"lastPart\":\"%s\"%s}".formatted(
            type,
            from,
            to,
            first,
            last,
            personal == null ? "" : ",\"personalReason\":\"" + personal + "\""
        );
        return mockMvc.perform(post("/api/leave/requests").with(nvAs).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long approved(String type, String from, String to, String first, String last, String personal) throws Exception {
        String res = submit(type, from, to, first, last, personal)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.id")).longValue();
        approve(id).andExpect(status().isOk());
        return id;
    }

    private ResultActions approve(long id) throws Exception {
        return mockMvc.perform(post("/api/leave/requests/{id}/approve", id).with(tnAs));
    }

    private void expectAccounting(String month, double annual) throws Exception {
        mockMvc
            .perform(get("/api/leave/accounting").param("month", month).with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[?(@.code=='KTNV')].annualDays").value(annual));
    }

    // ------------------------------------------------------------------ tình huống 1: hết phép

    @Test
    void outOfLeaveGetsOnlyTheOneAdvanceDayThenBlocked() throws Exception {
        balanceTo("0");
        submit("PHEP_NAM", "2026-11-23", "2026-11-24", "CA_NGAY", "CA_NGAY", null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
        approved("PHEP_NAM", "2026-11-23", "2026-11-23", "CA_NGAY", "CA_NGAY", null);
        assertThat(total()).isEqualByComparingTo("-1");
        assertThat(dayService.month(nv, YearMonth.of(2026, 11)).paidLeaveDays()).isEqualByComparingTo("1");
        submit("PHEP_NAM", "2026-11-24", "2026-11-24", "SANG", "SANG", null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
    }

    @Test
    void personalPaidLeaveIgnoresBalanceAndHasNoCumulativeLimit() throws Exception {
        balanceTo("-1");
        approved("VIEC_RIENG", "2026-11-23", "2026-11-25", "CA_NGAY", "CA_NGAY", "KET_HON");
        approved("VIEC_RIENG", "2026-11-26", "2026-11-27", "CA_NGAY", "CA_NGAY", "KET_HON");
        assertThat(total()).isEqualByComparingTo("-1");
        assertThat(dayService.month(nv, YearMonth.of(2026, 11)).paidLeaveDays()).isEqualByComparingTo("5");
    }

    // ------------------------------------------------------------------ tình huống 2: bắc qua tháng

    @Test
    void acrossMonthsNextMonthAccrualCoversTheAdvance() throws Exception {
        balanceTo("1");
        approved("PHEP_NAM", "2026-11-30", "2026-12-01", "CA_NGAY", "CA_NGAY", null);
        assertThat(total()).isEqualByComparingTo("-1");

        clock.set(LocalDate.of(2026, 12, 1));
        accrual.settle(nv.getId(), YearMonth.of(2026, 12), LocalDate.of(2026, 12, 1));
        assertThat(total()).isEqualByComparingTo("0");

        assertThat(dayService.month(nv, YearMonth.of(2026, 11)).paidLeaveDays()).isEqualByComparingTo("1");
        assertThat(dayService.month(nv, YearMonth.of(2026, 12)).paidLeaveDays()).isEqualByComparingTo("1");
        expectAccounting("2026-11", 1);
        expectAccounting("2026-12", 1);
    }

    @Test
    void acrossMonthsHalfDaysSplitByMonth() throws Exception {
        balanceTo("1");
        approved("PHEP_NAM", "2026-11-30", "2026-12-01", "CHIEU", "SANG", null);
        assertThat(total()).isEqualByComparingTo("0");
        assertThat(dayService.month(nv, YearMonth.of(2026, 11)).paidLeaveDays()).isEqualByComparingTo("0.5");
        assertThat(dayService.month(nv, YearMonth.of(2026, 12)).paidLeaveDays()).isEqualByComparingTo("0.5");
        expectAccounting("2026-11", 0.5);
        expectAccounting("2026-12", 0.5);
    }

    /** Số dư 0 (đã dùng phép tháng này): phép tháng sau chưa cộng nên chỉ còn 1 ngày ứng; đơn 2 ngày bị chặn dù tháng sau sẽ có thêm 1. */
    @Test
    void acrossMonthsNextMonthAccrualNotCountedBeforehand() throws Exception {
        balanceTo("0");
        submit("PHEP_NAM", "2026-11-30", "2026-12-01", "CA_NGAY", "CA_NGAY", null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
    }

    /**
     * Đơn gửi trước khi chốt công, duyệt sau khi tháng đầu đã chốt: nếu duyệt được thì sổ bị trừ mà ảnh chụp công (nguồn tính lương) không
     * có ngày nghỉ. Phải chặn, HR mở lại bảng công rồi mới duyệt; chốt lại thì ảnh chụp có đủ.
     */
    @Test
    void approvalBlockedWhileAttendanceClosedThenSnapshotCountsAfterReopen() throws Exception {
        balanceTo("2");
        String res = submit("PHEP_NAM", "2026-11-30", "2026-12-01", "CA_NGAY", "CA_NGAY", null)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.id")).longValue();

        clock.set(LocalDate.of(2026, 12, 2));
        closeNovember();
        approve(id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.leaveattendanceclosed"));
        assertThat(total()).isEqualByComparingTo("2");

        mockMvc
            .perform(
                post("/api/attendance/month/2026-11/reopen")
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Duyệt đơn nghỉ\"}")
            )
            .andExpect(status().isOk());
        approve(id).andExpect(status().isOk());
        closeNovember();
        assertThat(total()).isEqualByComparingTo("0");
        assertThat(novemberSnapshotPaid()).isEqualByComparingTo("1");
    }

    /** Đồng ý hủy sau khi chốt công: chặn như duyệt, không để sổ hoàn phép mà ảnh chụp vẫn tính ngày nghỉ có lương. */
    @Test
    void cancelApprovalBlockedWhileAttendanceClosed() throws Exception {
        balanceTo("2");
        long id = approved("PHEP_NAM", "2026-11-30", "2026-12-01", "CA_NGAY", "CA_NGAY", null);
        mockMvc.perform(post("/api/leave/requests/{id}/cancel-request", id).with(nvAs)).andExpect(status().isOk());

        clock.set(LocalDate.of(2026, 12, 2));
        closeNovember();
        mockMvc
            .perform(post("/api/leave/requests/{id}/cancel-approve", id).with(tnAs))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leaveattendanceclosed"));
        assertThat(total()).isEqualByComparingTo("0");
        assertThat(novemberSnapshotPaid()).isEqualByComparingTo("1");
    }

    private void closeNovember() throws Exception {
        mockMvc.perform(post("/api/attendance/month/2026-11/close").param("force", "true").with(HR)).andExpect(status().isOk());
    }

    private BigDecimal novemberSnapshotPaid() {
        return snapshots
            .findByMonth("2026-11")
            .stream()
            .filter(s -> s.getEmployeeId().equals(nv.getId()))
            .map(AttendanceSnapshot::getPaidLeaveDays)
            .findFirst()
            .orElseThrow();
    }
}
