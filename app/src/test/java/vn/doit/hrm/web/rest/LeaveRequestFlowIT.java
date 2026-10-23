package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.Holiday;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.HolidayRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.TerminationService;
import vn.doit.hrm.service.dto.TerminationRequestDTO;
import vn.doit.hrm.service.leave.LeaveAccrualJob;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.leave.LeaveLedgerService;
import vn.doit.hrm.service.reminder.ReminderMailer;

/**
 * Đơn nghỉ: người duyệt theo cấp, gửi / duyệt / từ chối / rút / hủy, kiểm tra số dư (cả khi duyệt song song), đơn bắc qua năm và
 * sau hạn phép năm trước, nghỉ việc, quyền xem lý do, email. Hôm nay mặc định = thứ Năm 22/10/2026.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(LeaveRequestFlowIT.MutableClockConfig.class)
// Bộ test mặc định chỉ có 1 kết nối, nên các luồng không bao giờ chạy song song thật; test duyệt song song cần nhiều kết nối.
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class LeaveRequestFlowIT {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final RequestPostProcessor HR = user("hr-don").authorities(() -> "ROLE_HR");

    static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(LocalDate day) {
            this.now = day.atTime(10, 0).atZone(VN).toInstant();
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
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(LocalDate.of(2026, 10, 22).atTime(10, 0).atZone(VN).toInstant());
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
    private AuthorityRepository authorityRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private LeaveLedgerService ledger;

    @Autowired
    private TerminationService terminationService;

    @Autowired
    private LeaveAccrualJob accrualJob;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private String sfx;
    private Department dept;
    private Employee tp;
    private Employee tn;
    private Employee nv;
    private Employee gd;
    private Employee nn;

    @BeforeEach
    void resetClock() {
        clock.set(LocalDate.of(2026, 10, 22));
        when(mailer.configured()).thenReturn(true);
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    private static RequestPostProcessor as(String login) {
        return user(login).authorities(() -> "ROLE_NHAN_VIEN");
    }

    private RequestPostProcessor nvAs() {
        return as("nv-don" + sfx);
    }

    private RequestPostProcessor tnAs() {
        return as("tn-don" + sfx);
    }

    private RequestPostProcessor tpAs() {
        return as("tp-don" + sfx);
    }

    private RequestPostProcessor gdAs() {
        return user("gd-don" + sfx).authorities(() -> "ROLE_BGD");
    }

    private Employee person(String code, String login, String... roles) {
        Employee e = LeaveFixtures.employee(employeeRepository, code + sfx, PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        User u = LeaveFixtures.user(userRepository, login + sfx);
        u.setEmail(login + sfx + "@doit.test");
        Set<Authority> auths = new HashSet<>();
        for (String r : roles) {
            auths.add(authorityRepository.findById(r).orElseThrow());
        }
        u.setAuthorities(auths);
        e.setUser(userRepository.saveAndFlush(u));
        e.setDepartment(dept);
        return employeeRepository.saveAndFlush(e);
    }

    /** Phòng có trưởng phòng tp; trưởng nhóm tn quản lý nv; giám đốc gd; người ngoài nn (phòng khác). */
    private void init(String suffix) {
        sfx = suffix;
        dept = LeaveFixtures.department(departmentRepository, "Phòng thử đơn " + sfx);
        tp = person("TP", "tp-don");
        dept.setHead(tp);
        departmentRepository.saveAndFlush(dept);
        tn = person("TN", "tn-don");
        nv = person("NV", "nv-don");
        nv.setManager(tn);
        employeeRepository.saveAndFlush(nv);
        gd = person("GD", "gd-don", "ROLE_BGD");
        nn = person("NN", "nn-don");
        nn.setDepartment(LeaveFixtures.department(departmentRepository, "Phòng khác " + sfx));
        employeeRepository.saveAndFlush(nn);
    }

    private void init() {
        init("");
    }

    private void opening(Employee e, Pool pool, String amount) {
        write(e, pool, Bucket.NAM_NAY, amount, LedgerReason.DAU_KY, LocalDate.now(clock));
    }

    private void write(Employee e, Pool pool, Bucket bucket, String amount, LedgerReason reason, LocalDate effective) {
        Runnable write = () ->
            ledger.post(e.getId(), pool, List.of(new Entry(pool, bucket, new BigDecimal(amount))), reason, null, null, "thử", effective);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            write.run();
        } else {
            tx.executeWithoutResult(s -> write.run());
        }
    }

    private static String body(String type, String from, String to) {
        return body(type, from, to, "CA_NGAY", "CA_NGAY", null);
    }

    private static String body(String type, String from, String to, String first, String last, String personal) {
        return (
            "{\"type\":\"" +
            type +
            "\",\"fromDate\":\"" +
            from +
            "\",\"toDate\":\"" +
            to +
            "\",\"firstPart\":\"" +
            first +
            "\",\"lastPart\":\"" +
            last +
            "\"" +
            (personal == null ? "" : ",\"personalReason\":\"" + personal + "\"") +
            ",\"reasonText\":\"Việc gia đình riêng\"}"
        );
    }

    private ResultActions submit(RequestPostProcessor who, String json) throws Exception {
        return mockMvc.perform(post("/api/leave/requests").with(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long submitOk(RequestPostProcessor who, String json) throws Exception {
        String res = submit(who, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private ResultActions act(long id, String action, RequestPostProcessor who, String note) throws Exception {
        var req = post("/api/leave/requests/{id}/" + action, id).with(who);
        if (note != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"" + note + "\"}");
        }
        return mockMvc.perform(req);
    }

    private BigDecimal total(Employee e, Pool pool) {
        return tx.execute(s -> ledger.balance(e.getId(), pool)).total();
    }

    private List<LeaveLedgerEntry> rows(Employee e, LedgerReason reason) {
        return ledger.entries(e.getId()).stream().filter(r -> r.getReason() == reason).toList();
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    // ------------------------------------------------------------------ định tuyến

    @Test
    void teamLeadApprovesUpToThreeDaysDirectorAbove() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "10");
        submit(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-28"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.days").value(3))
            .andExpect(jsonPath("$.status").value("CHO_DUYET"))
            .andExpect(jsonPath("$.approverKind").value("TRUONG_NHOM"))
            .andExpect(jsonPath("$.approverEmployeeId").value(tn.getId()));
        submit(nvAs(), body("PHEP_NAM", "2026-11-02", "2026-11-05", "CA_NGAY", "SANG", null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.days").value(3.5))
            .andExpect(jsonPath("$.approverKind").value("BGD"))
            .andExpect(jsonPath("$.approverEmployeeId").doesNotExist());
    }

    @Test
    void specialApproverCases() throws Exception {
        init();
        for (Employee e : List.of(nv, tp, gd)) {
            opening(e, Pool.PHEP_NAM, "5");
        }
        // Không có trưởng nhóm → trưởng phòng.
        submit(as("tp-don"), body("PHEP_NAM", "2026-10-26", "2026-10-26")).andExpect(jsonPath("$.approverKind").value("BGD"));
        nv.setManager(null);
        employeeRepository.saveAndFlush(nv);
        submit(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-26"))
            .andExpect(jsonPath("$.approverKind").value("TRUONG_PHONG"))
            .andExpect(jsonPath("$.approverEmployeeId").value(tp.getId()));
        // Quản lý trực tiếp ghi nhầm là chính mình → trưởng phòng.
        nv.setManager(nv);
        employeeRepository.saveAndFlush(nv);
        submit(nvAs(), body("PHEP_NAM", "2026-10-27", "2026-10-27"))
            .andExpect(jsonPath("$.approverKind").value("TRUONG_PHONG"))
            .andExpect(jsonPath("$.approverEmployeeId").value(tp.getId()));
        // Trưởng nhóm đã nghỉ việc → lên trưởng phòng.
        nv.setManager(tn);
        employeeRepository.saveAndFlush(nv);
        tn.setStatus(EmploymentStatus.DA_NGHI);
        employeeRepository.saveAndFlush(tn);
        submit(nvAs(), body("PHEP_NAM", "2026-10-28", "2026-10-28"))
            .andExpect(jsonPath("$.approverKind").value("TRUONG_PHONG"))
            .andExpect(jsonPath("$.approverEmployeeId").value(tp.getId()));
        // Giám đốc xin nghỉ → Quản trị.
        submit(gdAs(), body("PHEP_NAM", "2026-10-26", "2026-10-26"))
            .andExpect(jsonPath("$.approverKind").value("QUAN_TRI"))
            .andExpect(jsonPath("$.approverEmployeeId").doesNotExist());
    }

    // ------------------------------------------------------------------ vòng đời

    @Test
    void lifecycleSubmitApproveRejectWithdrawCancel() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "5");

        long a = submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-27"));
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("5");
        assertThat(rows(nv, LedgerReason.DUYET_DON)).isEmpty();

        // Người không phải người duyệt, kể cả trưởng phòng và chính người xin: 403.
        act(a, "approve", tpAs(), null).andExpect(status().isForbidden());
        act(a, "approve", nvAs(), null).andExpect(status().isForbidden());
        act(a, "approve", tnAs(), "Đồng ý").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_DUYET"));
        assertThat(rows(nv, LedgerReason.DUYET_DON)).hasSize(1);
        assertThat(rows(nv, LedgerReason.DUYET_DON).get(0).getRefType()).isEqualTo("LEAVE_REQUEST");
        assertThat(rows(nv, LedgerReason.DUYET_DON).get(0).getRefId()).isEqualTo(a);
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("3");
        // Duyệt lần hai không trừ thêm.
        act(a, "approve", tnAs(), null).andExpect(status().isBadRequest());
        assertThat(rows(nv, LedgerReason.DUYET_DON)).hasSize(1);

        long b = submitOk(nvAs(), body("PHEP_NAM", "2026-10-29", "2026-10-29"));
        act(b, "reject", tnAs(), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.leavenoterequired"));
        act(b, "reject", tnAs(), "  ").andExpect(status().isBadRequest());
        act(b, "reject", tnAs(), "Dự án gấp").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TU_CHOI"));

        long c = submitOk(nvAs(), body("PHEP_NAM", "2026-10-30", "2026-10-30"));
        act(c, "withdraw", tnAs(), null).andExpect(status().isForbidden());
        act(c, "withdraw", nvAs(), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_RUT"));
        assertThat(ledger.entries(nv.getId()).stream().filter(r -> Long.valueOf(c).equals(r.getRefId()))).isEmpty();

        act(a, "cancel-request", nvAs(), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHO_HUY"));
        act(a, "cancel-approve", nvAs(), null).andExpect(status().isForbidden());
        act(a, "cancel-approve", tnAs(), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_HUY"));
        assertThat(rows(nv, LedgerReason.HUY_DON)).hasSize(1);
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("5");

        // Hủy lại không hoàn thêm.
        act(a, "cancel", HR, "Hủy lại").andExpect(status().isBadRequest());
        assertThat(rows(nv, LedgerReason.HUY_DON)).hasSize(1);

        // Từ chối yêu cầu hủy: đơn về DA_DUYET, sổ giữ nguyên.
        long e = submitOk(nvAs(), body("PHEP_NAM", "2026-11-02", "2026-11-02"));
        act(e, "approve", tnAs(), null).andExpect(status().isOk());
        act(e, "cancel-request", nvAs(), null).andExpect(status().isOk());
        act(e, "cancel-reject", tnAs(), "Đã sắp xếp người thay").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_DUYET"));
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("4");

        mockMvc
            .perform(get("/api/leave/requests/mine").with(nvAs()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void pastRequestOnlyHrCancelsAndRefunds() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "5");
        long id = submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-26"));
        act(id, "approve", tnAs(), null).andExpect(status().isOk());
        clock.set(LocalDate.of(2026, 10, 28));
        // Ngày nghỉ đã qua: người gửi không yêu cầu hủy được.
        act(id, "cancel-request", nvAs(), null).andExpect(status().isBadRequest());
        act(id, "cancel", nvAs(), "x").andExpect(status().isForbidden());
        act(id, "cancel", HR, "Nhân viên vẫn đi làm hôm đó")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_HUY"));
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("5");
    }

    @Test
    void hrApprovesOnBehalfAndInboxListsWhatICanDecide() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "5");
        long a = submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-26"));
        long b = submitOk(nvAs(), body("PHEP_NAM", "2026-11-02", "2026-11-06"));
        mockMvc
            .perform(get("/api/leave/requests/inbox").with(tnAs()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id==" + a + ")]").isNotEmpty())
            .andExpect(jsonPath("$[?(@.id==" + b + ")]").isEmpty());
        mockMvc
            .perform(get("/api/leave/requests/inbox").with(gdAs()))
            .andExpect(jsonPath("$[?(@.id==" + b + ")]").isNotEmpty())
            .andExpect(jsonPath("$[?(@.id==" + a + ")]").isEmpty());
        mockMvc.perform(get("/api/leave/requests/inbox").with(tpAs())).andExpect(jsonPath("$.length()").value(0));
        act(a, "approve", HR, null).andExpect(status().isOk()).andExpect(jsonPath("$.onBehalf").value(true));
        mockMvc
            .perform(
                post("/api/leave/requests/bulk-approve")
                    .with(gdAs())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\":[" + b + "," + a + "]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(b))
            .andExpect(jsonPath("$[0].ok").value(true))
            .andExpect(jsonPath("$[1].ok").value(false));
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("-1");
    }

    // ------------------------------------------------------------------ số dư

    @Test
    void submitChecksRoundedBalancePlusAdvance() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "0.5");
        submit(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-27"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
        long ok = submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-27", "CA_NGAY", "SANG", null));
        act(ok, "approve", tnAs(), null).andExpect(status().isOk());
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("-1");
    }

    @Test
    void pendingRequestsCountAgainstRequestable() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "0.5");
        submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-26"));
        submit(nvAs(), body("PHEP_NAM", "2026-10-27", "2026-10-27"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
    }

    @Test
    void approveRechecksBalanceAndReportsRemaining() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "1");
        long a = submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-26"));
        long b = submitOk(nvAs(), body("PHEP_NAM", "2026-10-27", "2026-10-27"));
        write(nv, Pool.PHEP_NAM, Bucket.NAM_NAY, "-1", LedgerReason.DIEU_CHINH, LocalDate.now(clock));
        act(a, "approve", tnAs(), null).andExpect(status().isOk());
        act(b, "approve", tnAs(), null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"))
            .andExpect(jsonPath("$.remaining").value(-1));
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("-1");
    }

    @Test
    void compensatoryLeaveUsesHoursWithoutAdvance() throws Exception {
        init();
        opening(nv, Pool.NGHI_BU, "12");
        submit(nvAs(), body("NGHI_BU", "2026-10-26", "2026-10-27"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
        long id = submitOk(nvAs(), body("NGHI_BU", "2026-10-26", "2026-10-27", "CA_NGAY", "SANG", null));
        act(id, "approve", tnAs(), null).andExpect(status().isOk()).andExpect(jsonPath("$.hours").value(12));
        assertThat(total(nv, Pool.NGHI_BU)).isEqualByComparingTo("0");
    }

    /** Review Focus 5: duyệt sau hạn 31/03 không dùng phần phép năm trước đã hết hạn. */
    @Test
    void approvalAfterExpiryCannotUseExpiredPreviousYear() throws Exception {
        init();
        write(nv, Pool.PHEP_NAM, Bucket.NAM_NAY, "3", LedgerReason.DAU_KY, LocalDate.of(2026, 6, 1));
        write(nv, Pool.PHEP_NAM, Bucket.NAM_NAY, "1", LedgerReason.DIEU_CHINH, LocalDate.of(2027, 2, 1));
        clock.set(LocalDate.of(2027, 3, 25));
        long big = submitOk(nvAs(), body("PHEP_NAM", "2027-04-12", "2027-04-14"));
        long small = submitOk(nvAs(), body("PHEP_NAM", "2027-04-19", "2027-04-20"));
        clock.set(LocalDate.of(2027, 4, 5));
        act(big, "approve", tnAs(), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.remaining").value(1));
        act(small, "approve", tnAs(), null).andExpect(status().isOk());
        List<LeaveLedgerEntry> deducted = rows(nv, LedgerReason.DUYET_DON);
        assertThat(deducted).hasSize(1);
        assertThat(deducted.get(0).getBucket()).isEqualTo(Bucket.NAM_NAY);
        assertThat(deducted.get(0).getAmount()).isEqualByComparingTo("-2");
        Balance b = tx.execute(s -> ledger.balance(nv.getId(), Pool.PHEP_NAM));
        assertThat(b.namTruoc()).isEqualByComparingTo("0");
        assertThat(b.namNay()).isEqualByComparingTo("-1");
    }

    /**
     * Review Focus 5, I3: hoàn đơn đã trừ vào phép năm trước, sau ngày hạn: hoàn về phép năm trước rồi hết hạn ngay (hai dòng cùng liên
     * kết đơn), không thành phép năm nay dùng mãi.
     */
    @Test
    void refundAfterExpiryExpiresAgain() throws Exception {
        init();
        write(nv, Pool.PHEP_NAM, Bucket.NAM_NAY, "3", LedgerReason.DAU_KY, LocalDate.of(2026, 6, 1));
        clock.set(LocalDate.of(2027, 3, 10));
        long before = submitOk(nvAs(), body("PHEP_NAM", "2027-03-15", "2027-03-16"));
        long late = submitOk(nvAs(), body("PHEP_NAM", "2027-03-17", "2027-03-17"));
        act(before, "approve", tnAs(), null).andExpect(status().isOk());
        act(late, "approve", tnAs(), null).andExpect(status().isOk());
        assertThat(rows(nv, LedgerReason.DUYET_DON)).allMatch(r -> r.getBucket() == Bucket.NAM_TRUOC);
        // Hoàn trước hạn: về phép năm trước (vẫn hết hạn 31/03 như thường).
        act(before, "cancel", HR, "Không nghỉ").andExpect(status().isOk());
        assertThat(rows(nv, LedgerReason.HUY_DON)).singleElement().satisfies(r -> assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC));
        // Hoàn sau hạn: về phép năm trước rồi hết hạn ngay.
        clock.set(LocalDate.of(2027, 4, 10));
        accrualJob.runMonth(YearMonth.of(2027, 4));
        BigDecimal beforeCancel = total(nv, Pool.PHEP_NAM);
        act(late, "cancel", HR, "Không nghỉ").andExpect(status().isOk());
        List<LeaveLedgerEntry> refunds = ledger.entries(nv.getId()).stream().filter(r -> Long.valueOf(late).equals(r.getRefId()) && r.getReason() == LedgerReason.HUY_DON).toList();
        assertThat(refunds).singleElement().satisfies(r -> {
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("1");
        });
        List<LeaveLedgerEntry> expired = ledger.entries(nv.getId()).stream().filter(r -> Long.valueOf(late).equals(r.getRefId()) && r.getReason() == LedgerReason.HET_HAN).toList();
        assertThat(expired).singleElement().satisfies(r -> {
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("-1");
            assertThat(r.getRefType()).isEqualTo("LEAVE_REQUEST");
        });
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo(beforeCancel);
        Balance b = tx.execute(s -> ledger.balance(nv.getId(), Pool.PHEP_NAM));
        assertThat(b.namTruoc()).isEqualByComparingTo("0");
    }

    /** Review Focus 5: đơn 28/12/2026–05/01/2027 duyệt ngày 20/12 trừ đủ số ngày làm việc; sau 01/01 số dư vẫn đúng. */
    @Test
    void requestAcrossYearEnd() throws Exception {
        init();
        Holiday newYear = new Holiday();
        newYear.setDay(LocalDate.of(2027, 1, 1));
        newYear.setName("Tết Dương lịch");
        holidayRepository.saveAndFlush(newYear);
        clock.set(LocalDate.of(2026, 12, 20));
        opening(nv, Pool.PHEP_NAM, "8");
        long id = submitOk(nvAs(), body("PHEP_NAM", "2026-12-28", "2027-01-05"));
        // 28–31/12 (4 ngày) + 04–05/01 (2 ngày); 01/01 là ngày lễ, 02–03/01 cuối tuần.
        act(id, "approve", gdAs(), null).andExpect(status().isOk()).andExpect(jsonPath("$.days").value(6));
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("2");
        clock.set(LocalDate.of(2027, 1, 4));
        Balance b = tx.execute(s -> ledger.balance(nv.getId(), Pool.PHEP_NAM));
        assertThat(b.namTruoc()).isEqualByComparingTo("2");
        assertThat(b.namNay()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ kiểm tra khi gửi

    @Test
    void overlapIsRejectedButComplementaryHalvesAreFine() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "5");
        submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-28"));
        submit(nvAs(), body("KHONG_LUONG", "2026-10-28", "2026-10-29"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.overlap"));
        submitOk(nvAs(), body("PHEP_NAM", "2026-10-29", "2026-10-29", "SANG", "SANG", null));
        submitOk(nvAs(), body("KHONG_LUONG", "2026-10-29", "2026-10-29", "CHIEU", "CHIEU", null));
        submit(nvAs(), body("PHEP_NAM", "2026-10-29", "2026-10-29", "CHIEU", "CHIEU", null))
            .andExpect(jsonPath("$.message").value("error.overlap"));
    }

    @Test
    void validationErrors() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "5");
        // Chỉ ngày nghỉ hằng tuần: 0 ngày làm việc.
        submit(nvAs(), body("PHEP_NAM", "2026-10-24", "2026-10-25"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leavenoworkingdays"));
        submit(nvAs(), body("PHEP_NAM", "2026-10-28", "2026-10-26")).andExpect(status().isBadRequest());
        // Quá 30 ngày về trước.
        submit(nvAs(), body("KHONG_LUONG", "2026-09-21", "2026-09-21"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leavepasttoofar"));
        submitOk(nvAs(), body("KHONG_LUONG", "2026-09-22", "2026-09-22"));
        // Nửa ngày sai vị trí ở đơn nhiều ngày.
        submit(nvAs(), body("PHEP_NAM", "2026-11-02", "2026-11-03", "SANG", "CA_NGAY", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leavepartinvalid"));
    }

    @Test
    void requestAfterScheduledLastWorkingDayRejected() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "5");
        terminationService.terminate(nv.getId(), new TerminationRequestDTO(LocalDate.of(2026, 11, 10), TerminationType.TU_NGHI, "Chuyển việc", null, null));
        submitOk(nvAs(), body("PHEP_NAM", "2026-11-10", "2026-11-10"));
        submit(nvAs(), body("PHEP_NAM", "2026-11-10", "2026-11-11"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.pendingtermination"));
    }

    @Test
    void personalLeaveLimitedByReason() throws Exception {
        init();
        submit(nvAs(), body("VIEC_RIENG", "2026-10-26", "2026-10-29", "CA_NGAY", "CA_NGAY", "KET_HON"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.personalleaveexceeded"));
        submit(nvAs(), body("VIEC_RIENG", "2026-10-26", "2026-10-26"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.personalreasonrequired"));
        long id = submitOk(nvAs(), body("VIEC_RIENG", "2026-10-26", "2026-10-28", "CA_NGAY", "CA_NGAY", "KET_HON"));
        act(id, "approve", tnAs(), null).andExpect(status().isOk());
        assertThat(ledger.entries(nv.getId())).isEmpty();
    }

    @Test
    void sickLeaveOverLimitOnlyWarns() throws Exception {
        init();
        // 2 năm đóng BHXH → 30 ngày/năm. 26/10–11/12: 35 ngày làm việc.
        submit(nvAs(), body("OM", "2026-10-26", "2026-12-11"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sickOverLimit").value(true))
            .andExpect(jsonPath("$.approverKind").value("BGD"));
        submit(nvAs(), body("OM", "2026-12-14", "2026-12-14", "SANG", "SANG", null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sickOverLimit").value(true));
        submit(as("tn-don"), body("OM", "2026-12-14", "2026-12-14"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sickOverLimit").value(false));
    }

    @Test
    void internCannotSubmit() throws Exception {
        init();
        Employee tt = LeaveFixtures.employee(employeeRepository, "TT-DON", PersonType.THUC_TAP, LocalDate.of(2026, 9, 1));
        tt.setUser(LeaveFixtures.user(userRepository, "tt-don"));
        employeeRepository.saveAndFlush(tt);
        submit(as("tt-don"), body("KHONG_LUONG", "2026-10-26", "2026-10-26"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.leavenotapplicable"));
    }

    // ------------------------------------------------------------------ quyền xem lý do, giấy ốm

    @Test
    void reasonAndSickDocumentOnlyForRequesterApproverHrAdmin() throws Exception {
        init();
        long id = submitOk(nvAs(), body("OM", "2026-10-26", "2026-10-26"));
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
        mockMvc
            .perform(multipart("/api/leave/requests/{id}/sick-document", id).file(new MockMultipartFile("file", "giay-om.png", "image/png", png)).with(as("nn-don")))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                multipart("/api/leave/requests/{id}/sick-document", id)
                    .file(new MockMultipartFile("file", "giay-om.png", "image/png", "khong phai png".getBytes()))
                    .with(nvAs())
            )
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(multipart("/api/leave/requests/{id}/sick-document", id).file(new MockMultipartFile("file", "giay-om.png", "image/png", png)).with(nvAs()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sickDocId").isNumber());

        for (RequestPostProcessor allowed : List.of(nvAs(), tnAs(), HR, user("qt-don").authorities(() -> "ROLE_ADMIN"))) {
            mockMvc
                .perform(get("/api/leave/requests/{id}", id).with(allowed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonText").value("Việc gia đình riêng"))
                .andExpect(jsonPath("$.sickDocId").isNumber());
        }
        mockMvc.perform(get("/api/leave/requests/{id}/sick-document", id).with(tnAs())).andExpect(status().isOk());
        // Trưởng phòng và giám đốc thấy đơn (lịch) nhưng không thấy lý do, giấy ốm.
        for (RequestPostProcessor viewer : List.of(tpAs(), gdAs())) {
            mockMvc
                .perform(get("/api/leave/requests/{id}", id).with(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("OM"))
                .andExpect(jsonPath("$.reasonText").doesNotExist())
                .andExpect(jsonPath("$.sickDocId").doesNotExist());
        }
        mockMvc.perform(get("/api/leave/requests/{id}/sick-document", id).with(tpAs())).andExpect(status().isForbidden());
        // Người ngoài: không thấy gì.
        mockMvc
            .perform(get("/api/leave/requests/{id}", id).with(as("nn-don")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonText").doesNotExist());

        // API hồ sơ scan: Kế toán, BGĐ không thấy giấy ốm; HR vẫn thấy; không xóa được khi đang gắn với đơn.
        String res = mockMvc.perform(get("/api/leave/requests/{id}", id).with(HR)).andReturn().getResponse().getContentAsString();
        long docId = ((Number) JsonPath.read(res, "$.sickDocId")).longValue();
        RequestPostProcessor keToan = user("kt-don").authorities(() -> "ROLE_KE_TOAN");
        for (RequestPostProcessor viewer : List.of(keToan, gdAs())) {
            mockMvc
                .perform(get("/api/employees/{e}/documents", nv.getId()).with(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + docId + ")]").isEmpty());
            mockMvc.perform(get("/api/employee-documents/{d}/download", docId).with(viewer)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/employee-documents/{d}", docId).with(viewer)).andExpect(status().isNotFound());
        }
        mockMvc
            .perform(get("/api/employees/{e}/documents", nv.getId()).with(HR))
            .andExpect(jsonPath("$[?(@.id==" + docId + ")]").isNotEmpty());
        mockMvc
            .perform(MockMvcRequestBuilders.delete("/api/employee-documents/{d}", docId).with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.documentinuse"));
    }

    /** R13: giấy ốm bị thay vẫn là giấy ốm — Kế toán, BGĐ không thấy, không tải, không xóa được giấy cũ lẫn giấy mới. */
    @Test
    void replacedSickCertificateStaysHidden() throws Exception {
        init();
        long id = submitOk(nvAs(), body("OM", "2026-10-26", "2026-10-26"));
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
        byte[] pdf = "%PDF-1.4 giay om moi".getBytes();
        String first = mockMvc
            .perform(multipart("/api/leave/requests/{id}/sick-document", id).file(new MockMultipartFile("file", "giay-om.png", "image/png", png)).with(nvAs()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long oldDoc = ((Number) JsonPath.read(first, "$.sickDocId")).longValue();
        String second = mockMvc
            .perform(multipart("/api/leave/requests/{id}/sick-document", id).file(new MockMultipartFile("file", "giay-om-moi.pdf", "application/pdf", pdf)).with(HR))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long newDoc = ((Number) JsonPath.read(second, "$.sickDocId")).longValue();
        assertThat(newDoc).isNotEqualTo(oldDoc);

        RequestPostProcessor keToan = user("kt-don").authorities(() -> "ROLE_KE_TOAN");
        for (RequestPostProcessor viewer : List.of(keToan, gdAs())) {
            for (long doc : List.of(oldDoc, newDoc)) {
                mockMvc
                    .perform(get("/api/employees/{e}/documents", nv.getId()).with(viewer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id==" + doc + ")]").isEmpty());
                mockMvc.perform(get("/api/employee-documents").with(viewer)).andExpect(jsonPath("$[?(@.id==" + doc + ")]").isEmpty());
                mockMvc.perform(get("/api/employee-documents/{d}", doc).with(viewer)).andExpect(status().isNotFound());
                mockMvc.perform(get("/api/employee-documents/{d}/download", doc).with(viewer)).andExpect(status().isNotFound());
                mockMvc
                    .perform(MockMvcRequestBuilders.delete("/api/employee-documents/{d}", doc).with(viewer))
                    .andExpect(status().isForbidden());
            }
        }
        // HR thấy và tải được cả hai; không ai xóa được giấy ốm (kể cả giấy cũ).
        for (long doc : List.of(oldDoc, newDoc)) {
            mockMvc.perform(get("/api/employees/{e}/documents", nv.getId()).with(HR)).andExpect(jsonPath("$[?(@.id==" + doc + ")]").isNotEmpty());
            mockMvc.perform(get("/api/employee-documents/{d}/download", doc).with(HR)).andExpect(status().isOk());
            mockMvc
                .perform(MockMvcRequestBuilders.delete("/api/employee-documents/{d}", doc).with(HR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.documentinuse"));
        }
        // Người gửi vẫn tải được giấy hiện tại qua đơn.
        mockMvc
            .perform(get("/api/leave/requests/{id}/sick-document", id).with(nvAs()))
            .andExpect(status().isOk())
            .andExpect(MockMvcResultMatchers.content().contentType("application/pdf"));
    }

    /** R14: nghỉ việc trước hạn 31/03, hoàn tác sau hạn: phần năm trước khôi phục vẫn hết hạn, không bao giờ âm. */
    @Test
    void undoAfterExpiryKeepsPreviousYearExpired() throws Exception {
        init();
        write(nv, Pool.PHEP_NAM, Bucket.NAM_NAY, "3", LedgerReason.DAU_KY, LocalDate.of(2026, 6, 1));
        clock.set(LocalDate.of(2027, 3, 20));
        terminationService.terminate(nv.getId(), new TerminationRequestDTO(LocalDate.of(2027, 3, 19), TerminationType.TU_NGHI, "Chuyển việc", null, null));
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("0");
        clock.set(LocalDate.of(2027, 4, 1));
        accrualJob.runMonth(YearMonth.of(2027, 4));
        clock.set(LocalDate.of(2027, 4, 5));
        terminationService.undo(nv.getId());
        List<LeaveLedgerEntry> undone = rows(nv, LedgerReason.HOAN_TAC);
        assertThat(undone).singleElement().satisfies(r -> {
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getEffectiveDate()).isEqualTo(LocalDate.of(2027, 3, 20));
        });
        Balance b = tx.execute(s -> ledger.balance(nv.getId(), Pool.PHEP_NAM));
        assertThat(b.namTruoc()).isEqualByComparingTo("0");
        accrualJob.runMonth(YearMonth.of(2027, 4));
        b = tx.execute(s -> ledger.balance(nv.getId(), Pool.PHEP_NAM));
        assertThat(b.namTruoc()).isEqualByComparingTo("0");
        assertThat(rows(nv, LedgerReason.HET_HAN)).singleElement().satisfies(r -> assertThat(r.getAmount()).isEqualByComparingTo("-3"));
    }

    /** R14: chốt ngày 20/12, hoàn tác ngày 10/01: số dư khôi phục được chuyển sang phép năm trước. */
    @Test
    void undoAcrossYearEndIsCarriedOver() throws Exception {
        init();
        clock.set(LocalDate.of(2026, 12, 20));
        opening(nv, Pool.PHEP_NAM, "5");
        terminationService.terminate(nv.getId(), new TerminationRequestDTO(LocalDate.of(2026, 12, 19), TerminationType.TU_NGHI, "Chuyển việc", null, null));
        assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("0");
        clock.set(LocalDate.of(2027, 1, 1));
        accrualJob.runMonth(YearMonth.of(2027, 1));
        clock.set(LocalDate.of(2027, 1, 10));
        terminationService.undo(nv.getId());
        Balance b = tx.execute(s -> ledger.balance(nv.getId(), Pool.PHEP_NAM));
        assertThat(b.namTruoc()).isEqualByComparingTo("5");
        assertThat(b.namNay()).isEqualByComparingTo("0");
    }

    /** R15: cho nghỉ → hoàn tác → cho nghỉ lại với ngày làm việc cuối sớm hơn: lần rút ngắn thứ hai vẫn được hoàn. */
    @Test
    void secondTrimAfterUndoIsRefunded() throws Exception {
        init();
        opening(tn, Pool.PHEP_NAM, "6");
        long spanning = submitOk(tnAs(), body("PHEP_NAM", "2026-10-19", "2026-10-23"));
        act(spanning, "approve", gdAs(), null).andExpect(status().isOk());
        assertThat(total(tn, Pool.PHEP_NAM)).isEqualByComparingTo("1");

        terminationService.terminate(tn.getId(), new TerminationRequestDTO(LocalDate.of(2026, 10, 21), TerminationType.TU_NGHI, "Chuyển việc", null, null));
        terminationService.undo(tn.getId());
        // 19–21/10: 3 ngày, hoàn 2.
        assertThat(total(tn, Pool.PHEP_NAM)).isEqualByComparingTo("3");

        terminationService.terminate(tn.getId(), new TerminationRequestDTO(LocalDate.of(2026, 10, 20), TerminationType.TU_NGHI, "Chuyển việc", null, null));
        mockMvc
            .perform(get("/api/leave/requests/{id}", spanning).with(HR))
            .andExpect(jsonPath("$.toDate").value("2026-10-20"))
            .andExpect(jsonPath("$.days").value(2));
        // Lần hai hoàn thêm 1: chốt 4 ngày.
        List<LeaveLedgerEntry> settled = ledger
            .entries(tn.getId())
            .stream()
            .filter(r -> r.getReason() == LedgerReason.NGHI_VIEC && r.getCreatedAt() != null)
            .toList();
        assertThat(settled.get(settled.size() - 1).getAmount()).isEqualByComparingTo("-4");
        terminationService.undo(tn.getId());
        assertThat(total(tn, Pool.PHEP_NAM)).isEqualByComparingTo("4");
        // Hủy cả đơn sau đó: hoàn đúng phần còn lại (2), tổng về 6.
        act(spanning, "cancel", HR, "Không nghỉ").andExpect(status().isOk());
        assertThat(total(tn, Pool.PHEP_NAM)).isEqualByComparingTo("6");
    }

    // ------------------------------------------------------------------ nghỉ việc (Review Focus 4)

    @Test
    void terminationCancelsFutureRequestsReassignsAndSettlesThenUndo() throws Exception {
        init();
        opening(tn, Pool.PHEP_NAM, "6");
        opening(tn, Pool.NGHI_BU, "4");
        opening(nv, Pool.PHEP_NAM, "5");
        // Đơn của tn (không có trưởng nhóm → trưởng phòng duyệt).
        long future = submitOk(tnAs(), body("PHEP_NAM", "2026-10-26", "2026-10-27"));
        long spanning = submitOk(tnAs(), body("PHEP_NAM", "2026-10-19", "2026-10-23"));
        long pending = submitOk(tnAs(), body("KHONG_LUONG", "2026-11-02", "2026-11-02"));
        act(future, "approve", tpAs(), null).andExpect(status().isOk());
        act(spanning, "approve", gdAs(), null).andExpect(status().isOk());
        assertThat(total(tn, Pool.PHEP_NAM)).isEqualByComparingTo("-1");
        // Đơn nv chờ tn duyệt.
        long nvPending = submitOk(nvAs(), body("PHEP_NAM", "2026-11-03", "2026-11-03"));

        terminationService.terminate(tn.getId(), new TerminationRequestDTO(LocalDate.of(2026, 10, 21), TerminationType.TU_NGHI, "Chuyển việc", null, null));

        mockMvc.perform(get("/api/leave/requests/{id}", future).with(HR)).andExpect(jsonPath("$.status").value("DA_HUY"));
        mockMvc
            .perform(get("/api/leave/requests/{id}", pending).with(HR))
            .andExpect(jsonPath("$.status").value("DA_HUY"))
            .andExpect(jsonPath("$.decisionNote").value("Nhân viên nghỉ việc"));
        // Đơn 19–23/10 rút ngắn về ngày làm việc cuối 21/10: còn 3 ngày, hoàn 2.
        mockMvc
            .perform(get("/api/leave/requests/{id}", spanning).with(HR))
            .andExpect(jsonPath("$.status").value("DA_DUYET"))
            .andExpect(jsonPath("$.toDate").value("2026-10-21"))
            .andExpect(jsonPath("$.days").value(3));
        mockMvc
            .perform(get("/api/leave/requests/{id}", nvPending).with(HR))
            .andExpect(jsonPath("$.approverKind").value("TRUONG_PHONG"))
            .andExpect(jsonPath("$.approverEmployeeId").value(tp.getId()))
            .andExpect(jsonPath("$.systemNote").isString());
        assertThat(rows(tn, LedgerReason.HUY_DON)).isNotEmpty();
        // Trước khi chốt: 6 − 2 − 5 + 2 (hủy) + 2 (rút ngắn) = 3 ngày; 4 giờ nghỉ bù.
        List<LeaveLedgerEntry> settled = rows(tn, LedgerReason.NGHI_VIEC);
        assertThat(settled.stream().filter(r -> r.getPool() == Pool.PHEP_NAM).map(LeaveLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("-3");
        assertThat(settled.stream().filter(r -> r.getPool() == Pool.NGHI_BU).map(LeaveLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("-4");
        Balance after = tx.execute(s -> ledger.balance(tn.getId(), Pool.PHEP_NAM));
        assertThat(after.namTruoc()).isEqualByComparingTo("0");
        assertThat(after.namNay()).isEqualByComparingTo("0");
        assertThat(total(tn, Pool.NGHI_BU)).isEqualByComparingTo("0");
        // Hết hạn 01/04 năm sau không lấy mất gì nữa.
        clock.set(LocalDate.of(2027, 4, 2));
        assertThat(total(tn, Pool.PHEP_NAM)).isEqualByComparingTo("0");
        clock.set(LocalDate.of(2026, 10, 22));

        String notice = terminationService.undo(tn.getId());
        assertThat(notice).contains("đơn nghỉ");
        List<LeaveLedgerEntry> undone = rows(tn, LedgerReason.HOAN_TAC);
        assertThat(undone).hasSameSizeAs(settled);
        assertThat(total(tn, Pool.PHEP_NAM)).isEqualByComparingTo("3");
        assertThat(total(tn, Pool.NGHI_BU)).isEqualByComparingTo("4");
        mockMvc.perform(get("/api/leave/requests/{id}", future).with(HR)).andExpect(jsonPath("$.status").value("DA_HUY"));
    }

    // ------------------------------------------------------------------ việc tự động 00:20

    @Test
    void dailySweepMovesRequestsOfLockedApprover() throws Exception {
        init();
        opening(nv, Pool.PHEP_NAM, "5");
        long id = submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-26"));
        User u = tn.getUser();
        u.setActivated(false);
        userRepository.saveAndFlush(u);
        accrualJob.reassignOrphans();
        mockMvc
            .perform(get("/api/leave/requests/{id}", id).with(HR))
            .andExpect(jsonPath("$.approverKind").value("TRUONG_PHONG"))
            .andExpect(jsonPath("$.approverEmployeeId").value(tp.getId()))
            .andExpect(jsonPath("$.systemNote").isString());
    }

    // ------------------------------------------------------------------ ghi thật vào DB (song song, email sau commit)

    /** Review Focus 2: hai lần duyệt song song cho hai đơn của cùng người, tổng vượt → đúng một lần thành công. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentApprovalsNeverGoBelowAdvance() throws Exception {
        tx.executeWithoutResult(s -> init("-" + UUID.randomUUID().toString().substring(0, 6)));
        try {
            opening(nv, Pool.PHEP_NAM, "1");
            long a = submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-26"));
            long b = submitOk(nvAs(), body("PHEP_NAM", "2026-10-27", "2026-10-27"));
            write(nv, Pool.PHEP_NAM, Bucket.NAM_NAY, "-1", LedgerReason.DIEU_CHINH, LocalDate.now(clock));
            // Giữ khóa dòng hồ sơ trong lúc hai lần duyệt bắt đầu, để cả hai cùng chờ ở cùng một điểm rồi mới chạy: nếu việc duyệt
            // đọc số dư trước khi khóa, cả hai sẽ cùng thấy số dư 0 và cùng trừ.
            ExecutorService pool = Executors.newFixedThreadPool(3);
            try {
                CountDownLatch locked = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                Future<?> holder = pool.submit(() ->
                    tx.executeWithoutResult(s -> {
                        employeeRepository.findByIdForUpdate(nv.getId());
                        locked.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    })
                );
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                Future<Integer> fa = pool.submit(() -> act(a, "approve", tnAs(), null).andReturn().getResponse().getStatus());
                Future<Integer> fb = pool.submit(() -> act(b, "approve", tnAs(), null).andReturn().getResponse().getStatus());
                Thread.sleep(700);
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                assertThat(List.of(fa.get(10, TimeUnit.SECONDS), fb.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 400);
            } finally {
                pool.shutdownNow();
            }
            assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("-1");
            assertThat(rows(nv, LedgerReason.DUYET_DON)).hasSize(1);
        } finally {
            cleanup();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void emailsGoToApproverAndRequesterAndMailFailureDoesNotBlock() throws Exception {
        tx.executeWithoutResult(s -> init("-" + UUID.randomUUID().toString().substring(0, 6)));
        try {
            opening(nv, Pool.PHEP_NAM, "5");
            String tnMail = "tn-don" + sfx + "@doit.test";
            String nvMail = "nv-don" + sfx + "@doit.test";
            String tpMail = "tp-don" + sfx + "@doit.test";
            long a = submitOk(nvAs(), body("PHEP_NAM", "2026-10-26", "2026-10-26"));
            verify(mailer).send(eq(tnMail), contains("Đơn nghỉ"), anyString());
            verify(mailer, never()).send(eq(tpMail), anyString(), anyString());
            act(a, "approve", tnAs(), null).andExpect(status().isOk());
            verify(mailer).send(eq(nvMail), contains("duyệt"), anyString());

            doThrow(new MailSendException("SMTP hỏng")).when(mailer).send(anyString(), anyString(), anyString());
            long b = submitOk(nvAs(), body("PHEP_NAM", "2026-10-27", "2026-10-27"));
            act(b, "approve", tnAs(), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_DUYET"));
            assertThat(total(nv, Pool.PHEP_NAM)).isEqualByComparingTo("3");
        } finally {
            cleanup();
        }
    }

    /** Xóa dữ liệu đã ghi thật của các test không chạy trong transaction (kết nối không tự commit: phải chạy trong transaction). */
    private void cleanup() {
        tx.executeWithoutResult(s -> {
            String in = List.of(tp.getId(), tn.getId(), nv.getId(), gd.getId(), nn.getId())
                .stream()
                .map(String::valueOf)
                .reduce((x, y) -> x + "," + y)
                .orElseThrow();
            List<Long> users = jdbc.queryForList("select user_id from employee where id in (" + in + ") and user_id is not null", Long.class);
            List<Long> depts = jdbc.queryForList(
                "select distinct department_id from employee where id in (" + in + ") and department_id is not null",
                Long.class
            );
            jdbc.update("delete from leave_ledger_entry where employee_id in (" + in + ")");
            jdbc.update("delete from leave_request_aud where employee_id in (" + in + ")");
            jdbc.update("delete from leave_request where employee_id in (" + in + ")");
            jdbc.update("update department set head_id = null where head_id in (" + in + ")");
            jdbc.update("update employee set manager_id = null where id in (" + in + ")");
            jdbc.update("delete from employee where id in (" + in + ")");
            for (Long u : users) {
                jdbc.update("delete from jhi_user_authority where user_id = ?", u);
                jdbc.update("delete from jhi_user where id = ?", u);
            }
            for (Long d : depts) {
                jdbc.update("delete from department where id = ?", d);
            }
        });
    }
}
