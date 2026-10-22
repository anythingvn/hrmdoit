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
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.TerminationService;
import vn.doit.hrm.service.dto.TerminationRequestDTO;
import vn.doit.hrm.service.leave.LeaveAccountingSummaryService;
import vn.doit.hrm.service.leave.LeaveAccrualJob;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.leave.LeaveLedgerService;
import vn.doit.hrm.service.leave.dto.LeaveAccountingDTO;
import vn.doit.hrm.service.reminder.ReminderMailer;

/**
 * Đợt sửa sau rà soát cuối: thu hồi phép tháng nghỉ việc (I1), khóa đơn nghỉ theo kỳ lương đã duyệt và số chốt hiện ở tháng ghi (I2),
 * hoàn phần phép năm trước đã hết hạn (I3), quyền xem theo quan hệ (M1), ghi chú quyết định (M2), từ chối nhiều đơn (M5).
 * Cùng cấu hình context với {@link LeaveRequestFlowIT}.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(LeaveRequestFlowIT.MutableClockConfig.class)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class LeaveSettlementLockIT {

    private static final RequestPostProcessor HR = user("hr-fx").authorities(() -> "ROLE_HR");

    @MockitoBean
    private ReminderMailer mailer;

    @Autowired
    private LeaveRequestFlowIT.MutableClock clock;

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
    private PayrollPeriodRepository periodRepository;

    @Autowired
    private TerminationRecordRepository recordRepository;

    @Autowired
    private LeaveLedgerService ledger;

    @Autowired
    private LeaveAccrualJob accrualJob;

    @Autowired
    private TerminationService terminationService;

    @Autowired
    private LeaveAccountingSummaryService accounting;

    private Department dept;
    private Employee tp;
    private Employee tn;
    private Employee nv;

    @BeforeEach
    void init() {
        clock.set(LocalDate.of(2026, 10, 22));
        when(mailer.configured()).thenReturn(false);
        dept = LeaveFixtures.department(departmentRepository, "Phòng thử sửa cuối");
        tp = person("TP-FX", "tp-fx");
        dept.setHead(tp);
        departmentRepository.saveAndFlush(dept);
        tn = person("TN-FX", "tn-fx");
        nv = person("NV-FX", "nv-fx");
        nv.setManager(tn);
        employeeRepository.saveAndFlush(nv);
        person("GD-FX", "gd-fx", "ROLE_BGD");
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    private Employee person(String code, String login, String... roles) {
        Employee e = LeaveFixtures.employee(employeeRepository, code, PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        User u = LeaveFixtures.user(userRepository, login);
        Set<Authority> auths = new HashSet<>();
        for (String r : roles) {
            auths.add(authorityRepository.findById(r).orElseThrow());
        }
        u.setAuthorities(auths);
        e.setUser(userRepository.saveAndFlush(u));
        e.setDepartment(dept);
        return employeeRepository.saveAndFlush(e);
    }

    private static RequestPostProcessor as(String login) {
        return user(login).authorities(() -> "ROLE_NHAN_VIEN");
    }

    private static final RequestPostProcessor NV = as("nv-fx");
    private static final RequestPostProcessor TN = as("tn-fx");
    private static final RequestPostProcessor TP = as("tp-fx");
    private static final RequestPostProcessor GD = user("gd-fx").authorities(() -> "ROLE_BGD");

    private void write(Employee e, Pool pool, Bucket bucket, String amount, LedgerReason reason, LocalDate effective) {
        ledger.post(e.getId(), pool, List.of(new Entry(pool, bucket, new BigDecimal(amount))), reason, null, null, "thử", effective);
    }

    private void opening(Employee e, String days) {
        write(e, Pool.PHEP_NAM, Bucket.NAM_NAY, days, LedgerReason.DAU_KY, LocalDate.now(clock));
    }

    private static String body(String type, String from, String to) {
        return (
            "{\"type\":\"" +
            type +
            "\",\"fromDate\":\"" +
            from +
            "\",\"toDate\":\"" +
            to +
            "\",\"firstPart\":\"CA_NGAY\",\"lastPart\":\"CA_NGAY\",\"reasonText\":\"Việc riêng\"}"
        );
    }

    private long submitOk(String type, String from, String to) throws Exception {
        String res = mockMvc
            .perform(post("/api/leave/requests").with(NV).contentType(MediaType.APPLICATION_JSON).content(body(type, from, to)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private ResultActions act(long id, String action, RequestPostProcessor who, String note) throws Exception {
        var req = post("/api/leave/requests/{id}/" + action, id).with(who);
        if (note != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"" + note + "\"}");
        }
        return mockMvc.perform(req);
    }

    private Balance balance(Employee e) {
        return ledger.balance(e.getId(), Pool.PHEP_NAM);
    }

    private List<LeaveLedgerEntry> rows(Employee e, LedgerReason reason) {
        return ledger.entries(e.getId()).stream().filter(r -> r.getReason() == reason).toList();
    }

    private static BigDecimal sum(List<LeaveLedgerEntry> rows, Pool pool) {
        return rows.stream().filter(r -> r.getPool() == pool).map(LeaveLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void terminate(Employee e, LocalDate lastWorkingDay) {
        terminationService.terminate(e.getId(), new TerminationRequestDTO(lastWorkingDay, TerminationType.TU_NGHI, "Chuyển việc", null, null));
    }

    private void approvedPeriod(String period, Instant approvedAt) {
        periodRepository.insertIfMissing(period);
        PayrollPeriod p = periodRepository.findByPeriod(period).orElseThrow();
        p.setStatus(PayrollPeriodStatus.DA_DUYET);
        p.setApprovedVersion(1);
        p.setApprovedBy("kt");
        p.setApprovedAt(approvedAt);
        periodRepository.saveAndFlush(p);
    }

    private LeaveAccountingDTO.Row summaryRow(String month, Employee e) {
        return accounting.summary(month).rows().stream().filter(r -> r.employeeId().equals(e.getId())).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ I1: phép tháng nghỉ việc

    /** Ngày làm việc cuối 09/10: 7/22 ngày làm việc (< 50%) → phép tháng 10 đã cộng bị thu hồi, số chốt giảm 1; hoàn tác trả lại. */
    @Test
    void leavingMonthUnderHalfIsRevokedAndUndoRestoresIt() throws Exception {
        clock.set(LocalDate.of(2026, 10, 1));
        accrualJob.runMonth(YearMonth.of(2026, 10));
        opening(nv, "5");
        assertThat(rows(nv, LedgerReason.CONG_THANG)).singleElement().satisfies(r -> assertThat(r.getAmount()).isEqualByComparingTo("1"));

        clock.set(LocalDate.of(2026, 10, 12));
        terminate(nv, LocalDate.of(2026, 10, 9));
        TerminationRecord record = recordRepository.findFirstByEmployeeIdAndStatusOrderByIdDesc(nv.getId(), TerminationRecordStatus.APPLIED).orElseThrow();
        assertThat(rows(nv, LedgerReason.DIEU_CHINH)).singleElement().satisfies(r -> {
            assertThat(r.getAmount()).isEqualByComparingTo("-1");
            assertThat(r.getRefType()).isEqualTo("ACCRUAL_REVOKE");
            assertThat(r.getRefId()).isEqualTo(record.getId() * 1_000_000L + 202610);
            assertThat(r.getNote()).contains("10/2026");
        });
        assertThat(sum(rows(nv, LedgerReason.NGHI_VIEC), Pool.PHEP_NAM)).isEqualByComparingTo("-5");
        assertThat(balance(nv).total()).isEqualByComparingTo("0");
        assertThat(summaryRow("2026-10", nv).settlementDays()).isEqualByComparingTo("5");

        terminationService.undo(nv.getId());
        assertThat(rows(nv, LedgerReason.HOAN_TAC)).anySatisfy(r -> {
            assertThat(r.getRefType()).isEqualTo("ACCRUAL_REVOKE");
            assertThat(r.getAmount()).isEqualByComparingTo("1");
        });
        assertThat(balance(nv).total()).isEqualByComparingTo("6");
        // Dòng trả lại phần thu hồi không phải số chốt: tháng 10 không còn số chốt nào.
        assertThat(summaryRow("2026-10", nv).settlementDays()).isNull();
    }

    /** Ngày làm việc cuối 16/10: 12/22 ngày (≥ 50%) → giữ phép tháng 10. */
    @Test
    void leavingMonthAtHalfOrMoreIsKept() throws Exception {
        clock.set(LocalDate.of(2026, 10, 1));
        accrualJob.runMonth(YearMonth.of(2026, 10));
        opening(nv, "5");
        clock.set(LocalDate.of(2026, 10, 19));
        terminate(nv, LocalDate.of(2026, 10, 16));
        assertThat(rows(nv, LedgerReason.DIEU_CHINH)).isEmpty();
        assertThat(sum(rows(nv, LedgerReason.NGHI_VIEC), Pool.PHEP_NAM)).isEqualByComparingTo("-6");
    }

    /** Lịch nghỉ 30/10 áp dụng muộn (03/11) sau khi việc tự động đã cộng tháng 11: thu hồi tháng 11, giữ tháng 10. */
    @Test
    void lateAppliedScheduledTerminationRevokesLaterMonths() throws Exception {
        clock.set(LocalDate.of(2026, 10, 1));
        accrualJob.runMonth(YearMonth.of(2026, 10));
        opening(nv, "5");
        clock.set(LocalDate.of(2026, 10, 27));
        terminate(nv, LocalDate.of(2026, 10, 30));
        TerminationRecord record = recordRepository.findFirstByEmployeeIdAndStatusOrderByIdDesc(nv.getId(), TerminationRecordStatus.SCHEDULED).orElseThrow();
        clock.set(LocalDate.of(2026, 11, 1));
        accrualJob.runMonth(YearMonth.of(2026, 11));
        assertThat(rows(nv, LedgerReason.CONG_THANG)).hasSize(2);

        clock.set(LocalDate.of(2026, 11, 3));
        assertThat(terminationService.applyScheduled(record.getId())).isTrue();
        assertThat(rows(nv, LedgerReason.DIEU_CHINH)).singleElement().satisfies(r -> {
            assertThat(r.getAmount()).isEqualByComparingTo("-1");
            assertThat(r.getRefId()).isEqualTo(record.getId() * 1_000_000L + 202611);
        });
        assertThat(sum(rows(nv, LedgerReason.NGHI_VIEC), Pool.PHEP_NAM)).isEqualByComparingTo("-6");
    }

    // ------------------------------------------------------------------ I2: kỳ lương đã duyệt

    @Test
    void approvingLeaveInPayrollApprovedMonthIsRejected() throws Exception {
        clock.set(LocalDate.of(2026, 10, 5));
        long sept = submitOk("KHONG_LUONG", "2026-09-28", "2026-09-29");
        long spanning = submitOk("KHONG_LUONG", "2026-09-30", "2026-10-01");
        long oct = submitOk("KHONG_LUONG", "2026-10-02", "2026-10-02");
        approvedPeriod("2026-09", Instant.parse("2026-10-01T02:00:00Z"));
        act(sept, "approve", TN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.leaveperiodlocked"));
        act(spanning, "approve", TN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.leaveperiodlocked"));
        act(oct, "approve", TN, null).andExpect(status().isOk());
        // Từ chối vẫn được (không đổi số liệu của kỳ).
        act(sept, "reject", TN, "Kỳ lương đã chốt").andExpect(status().isOk());
    }

    @Test
    void cancellingLeaveInPayrollApprovedMonthIsRejected() throws Exception {
        clock.set(LocalDate.of(2026, 9, 21));
        opening(nv, "5");
        long past = submitOk("PHEP_NAM", "2026-09-24", "2026-09-25");
        act(past, "approve", TN, null).andExpect(status().isOk());
        long future = submitOk("PHEP_NAM", "2026-11-02", "2026-11-02");
        act(future, "approve", TN, null).andExpect(status().isOk());
        approvedPeriod("2026-09", Instant.parse("2026-10-01T02:00:00Z"));
        approvedPeriod("2026-11", Instant.parse("2026-10-01T02:00:00Z"));
        clock.set(LocalDate.of(2026, 10, 22));

        act(past, "cancel", HR, "Không nghỉ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.leaveperiodlocked"));
        act(future, "cancel-request", NV, null).andExpect(status().isOk());
        act(future, "cancel-approve", TN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.leaveperiodlocked"));
        assertThat(rows(nv, LedgerReason.HUY_DON)).isEmpty();
        assertThat(balance(nv).total()).isEqualByComparingTo("2");
    }

    /**
     * Cho nghỉ việc lùi ngày vào tháng 09 đã duyệt lương: tháng 09 giữ nguyên số đã chốt (ngày nghỉ đã trả lương), số chốt khi nghỉ việc
     * và phần đơn bị rút ngắn / hủy hiện ở tháng ghi (10).
     */
    @Test
    void backdatedTerminationIntoApprovedMonthShowsInPostingMonth() throws Exception {
        clock.set(LocalDate.of(2026, 9, 10));
        opening(nv, "8");
        long trimmed = submitOk("PHEP_NAM", "2026-09-14", "2026-09-18");
        long cancelled = submitOk("PHEP_NAM", "2026-09-21", "2026-09-22");
        act(trimmed, "approve", GD, null).andExpect(status().isOk());
        act(cancelled, "approve", TN, null).andExpect(status().isOk());
        approvedPeriod("2026-09", Instant.parse("2026-10-01T02:00:00Z"));

        clock.set(LocalDate.of(2026, 10, 22));
        terminate(nv, LocalDate.of(2026, 9, 15));
        // 14–18/09 rút về 14–15 (hoàn 3), 21–22/09 hủy (hoàn 2): chốt 8 − 7 + 5 = 6.
        assertThat(sum(rows(nv, LedgerReason.NGHI_VIEC), Pool.PHEP_NAM)).isEqualByComparingTo("-6");

        LeaveAccountingDTO.Row sept = summaryRow("2026-09", nv);
        assertThat(sept.annualDays()).isEqualByComparingTo("7");
        assertThat(sept.settlementDays()).isNull();
        LeaveAccountingDTO.Row oct = summaryRow("2026-10", nv);
        assertThat(oct.settlementDays()).isEqualByComparingTo("6");
        assertThat(oct.annualDays()).isEqualByComparingTo("-5");
    }

    // ------------------------------------------------------------------ I3: hoàn phần năm trước sau hạn

    /** Đơn tháng 06 duyệt tháng 02 trừ phép năm trước; hủy tháng 05 (sau hạn 31/03): phần hoàn hết hạn ngay, số dùng được không đổi. */
    @Test
    void refundOfPreviousYearAfterExpiryExpiresAgain() throws Exception {
        write(nv, Pool.PHEP_NAM, Bucket.NAM_NAY, "3", LedgerReason.DAU_KY, LocalDate.of(2026, 6, 1));
        clock.set(LocalDate.of(2027, 2, 10));
        long id = submitOk("PHEP_NAM", "2027-06-07", "2027-06-08");
        act(id, "approve", TN, null).andExpect(status().isOk());
        assertThat(rows(nv, LedgerReason.DUYET_DON)).singleElement().satisfies(r -> assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC));

        clock.set(LocalDate.of(2027, 5, 10));
        BigDecimal before = balance(nv).total();
        act(id, "cancel-request", NV, null).andExpect(status().isOk());
        act(id, "cancel-approve", TN, null).andExpect(status().isOk());

        Balance after = balance(nv);
        assertThat(after.total()).isEqualByComparingTo(before);
        assertThat(after.namTruoc()).isEqualByComparingTo("0");
        List<LeaveLedgerEntry> refund = ledger.entries(nv.getId()).stream().filter(r -> Long.valueOf(id).equals(r.getRefId())).toList();
        assertThat(refund).anySatisfy(r -> {
            assertThat(r.getReason()).isEqualTo(LedgerReason.HUY_DON);
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("2");
        });
        assertThat(refund).anySatisfy(r -> {
            assertThat(r.getReason()).isEqualTo(LedgerReason.HET_HAN);
            assertThat(r.getRefType()).isEqualTo("LEAVE_REQUEST");
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("-2");
        });
    }

    // ------------------------------------------------------------------ M1, M2: quyền xem theo quan hệ, ghi chú quyết định

    @Test
    void relationViewNeedsWorkingViewerActiveDepartmentAndIgnoresLoginCase() throws Exception {
        opening(nv, "10");
        // Hơn 3 ngày → BGĐ duyệt: trưởng nhóm chỉ xem theo quan hệ.
        long id = submitOk("PHEP_NAM", "2026-11-02", "2026-11-05");
        mockMvc.perform(get("/api/leave/requests/{id}", id).with(as("TN-FX"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/leave/ledger/{id}", nv.getId()).with(as("TP-FX"))).andExpect(status().isOk());

        dept.setActive(false);
        departmentRepository.saveAndFlush(dept);
        mockMvc.perform(get("/api/leave/requests/{id}", id).with(TP)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leave/ledger/{id}", nv.getId()).with(TP)).andExpect(status().isForbidden());
        dept.setActive(true);
        departmentRepository.saveAndFlush(dept);

        tn.setStatus(EmploymentStatus.DA_NGHI);
        employeeRepository.saveAndFlush(tn);
        mockMvc.perform(get("/api/leave/requests/{id}", id).with(TN)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leave/ledger/{id}", nv.getId()).with(TN)).andExpect(status().isForbidden());
    }

    @Test
    void overtimeRelationViewFollowsSameRules() throws Exception {
        String res = mockMvc
            .perform(
                post("/api/leave/overtime")
                    .with(NV)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"workDate\":\"2026-10-24\",\"startTime\":\"08:00\",\"endTime\":\"12:00\",\"content\":\"Triển khai\"}")
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.id")).longValue();
        mockMvc.perform(get("/api/leave/overtime/{id}", id).with(as("TP-FX"))).andExpect(status().isOk());
        dept.setActive(false);
        departmentRepository.saveAndFlush(dept);
        mockMvc.perform(get("/api/leave/overtime/{id}", id).with(TP)).andExpect(status().isForbidden());
        dept.setActive(true);
        departmentRepository.saveAndFlush(dept);
        tp.setStatus(EmploymentStatus.DA_NGHI);
        employeeRepository.saveAndFlush(tp);
        mockMvc.perform(get("/api/leave/overtime/{id}", id).with(TP)).andExpect(status().isForbidden());
    }

    @Test
    void decisionNoteOnlyForThoseWhoSeeTheReason() throws Exception {
        opening(nv, "5");
        long id = submitOk("PHEP_NAM", "2026-11-02", "2026-11-02");
        act(id, "approve", TN, "Ghi chú riêng của người duyệt").andExpect(status().isOk());
        mockMvc
            .perform(get("/api/leave/requests/{id}", id).with(TP))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reasonText").doesNotExist())
            .andExpect(jsonPath("$.decisionNote").doesNotExist());
        mockMvc.perform(get("/api/leave/requests/{id}", id).with(TN)).andExpect(jsonPath("$.decisionNote").value("Ghi chú riêng của người duyệt"));
        mockMvc.perform(get("/api/leave/requests/{id}", id).with(HR)).andExpect(jsonPath("$.decisionNote").value("Ghi chú riêng của người duyệt"));
    }

    // ------------------------------------------------------------------ M5: từ chối nhiều đơn

    @Test
    void bulkRejectNeedsNoteAndReportsEachRequest() throws Exception {
        opening(nv, "10");
        long mine = submitOk("PHEP_NAM", "2026-11-02", "2026-11-02");
        long director = submitOk("PHEP_NAM", "2026-11-09", "2026-11-12");
        String noNote = "{\"ids\":[" + mine + "," + director + "]}";
        mockMvc
            .perform(post("/api/leave/requests/bulk-reject").with(TN).contentType(MediaType.APPLICATION_JSON).content(noNote))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leavenoterequired"));
        String withNote = "{\"ids\":[" + mine + "," + director + "],\"note\":\"Dự án gấp\"}";
        mockMvc
            .perform(post("/api/leave/requests/bulk-reject").with(TN).contentType(MediaType.APPLICATION_JSON).content(withNote))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].ok").value(true))
            .andExpect(jsonPath("$[1].ok").value(false))
            .andExpect(jsonPath("$[1].errorKey").value("error.http.403"));
        mockMvc.perform(get("/api/leave/requests/{id}", mine).with(HR)).andExpect(jsonPath("$.status").value("TU_CHOI")).andExpect(jsonPath("$.decisionNote").value("Dự án gấp"));
        mockMvc.perform(get("/api/leave/requests/{id}", director).with(HR)).andExpect(jsonPath("$.status").value("CHO_DUYET"));
    }
}
