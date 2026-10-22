package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Các sửa sau cùng trước khi gộp nhánh:
 * <ul>
 *   <li>F2: rút ngắn đơn khi nghỉ việc hoàn đúng ngăn đã trừ; phần năm trước hoàn sau hạn hết hạn ngay (không được trả tiền);</li>
 *   <li>F3: mỗi lần nghỉ việc đổi đơn được ghi riêng; tháng đã duyệt lương giữ khoảng ngày lúc duyệt, mỗi lần đổi hiện phần chênh ở
 *       tháng ghi của nó;</li>
 *   <li>F4: thu hồi phép tháng năm trước (nghỉ việc lùi ngày qua 01/01) lấy từ phép năm trước, không quá số đang còn.</li>
 * </ul>
 * Cùng cấu hình context với {@link LeaveRequestFlowIT}.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(LeaveRequestFlowIT.MutableClockConfig.class)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class LeaveTerminationFollowupIT {

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
    private Employee nv;

    private static final RequestPostProcessor NV = user("nv-fu").authorities(() -> "ROLE_NHAN_VIEN");
    private static final RequestPostProcessor TN = user("tn-fu").authorities(() -> "ROLE_NHAN_VIEN");
    private static final RequestPostProcessor GD = user("gd-fu").authorities(() -> "ROLE_BGD");

    @BeforeEach
    void init() {
        clock.set(LocalDate.of(2026, 10, 22));
        when(mailer.configured()).thenReturn(false);
        dept = LeaveFixtures.department(departmentRepository, "Phòng thử sửa tiếp");
        Employee tp = person("TP-FU", "tp-fu");
        dept.setHead(tp);
        departmentRepository.saveAndFlush(dept);
        Employee tn = person("TN-FU", "tn-fu");
        nv = person("NV-FU", "nv-fu");
        nv.setManager(tn);
        employeeRepository.saveAndFlush(nv);
        person("GD-FU", "gd-fu", "ROLE_BGD");
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

    private void write(Pool pool, Bucket bucket, String amount, LedgerReason reason, LocalDate effective) {
        ledger.post(nv.getId(), pool, List.of(new Entry(pool, bucket, new BigDecimal(amount))), reason, null, null, "thử", effective);
    }

    private long approved(String from, String to, RequestPostProcessor approver) throws Exception {
        String body =
            "{\"type\":\"PHEP_NAM\",\"fromDate\":\"" +
            from +
            "\",\"toDate\":\"" +
            to +
            "\",\"firstPart\":\"CA_NGAY\",\"lastPart\":\"CA_NGAY\",\"reasonText\":\"Việc riêng\"}";
        String res = mockMvc
            .perform(post("/api/leave/requests").with(NV).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.id")).longValue();
        mockMvc.perform(post("/api/leave/requests/{id}/approve", id).with(approver)).andExpect(status().isOk());
        return id;
    }

    private void terminate(LocalDate lastWorkingDay) {
        terminationService.terminate(nv.getId(), new TerminationRequestDTO(lastWorkingDay, TerminationType.TU_NGHI, "Chuyển việc", null, null));
    }

    private TerminationRecord lastRecord() {
        return recordRepository.findFirstByEmployeeIdAndStatusOrderByIdDesc(nv.getId(), TerminationRecordStatus.APPLIED).orElseThrow();
    }

    private Balance balance() {
        return ledger.balance(nv.getId(), Pool.PHEP_NAM);
    }

    private List<LeaveLedgerEntry> rows(LedgerReason reason) {
        return ledger.entries(nv.getId()).stream().filter(r -> r.getReason() == reason && r.getPool() == Pool.PHEP_NAM).toList();
    }

    private static BigDecimal sum(List<LeaveLedgerEntry> rows) {
        return rows.stream().map(LeaveLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private BigDecimal annualDays(String month) {
        return accounting
            .summary(month)
            .rows()
            .stream()
            .filter(r -> r.employeeId().equals(nv.getId()))
            .findFirst()
            .map(LeaveAccountingDTO.Row::annualDays)
            .orElse(BigDecimal.ZERO);
    }

    private void runMonths(YearMonth from, YearMonth to) {
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            clock.set(m.atDay(1));
            accrualJob.runMonth(m);
        }
    }

    // ------------------------------------------------------------------ F2: rút ngắn đơn khi nghỉ việc

    /**
     * Đơn 06/04–09/04/2027 (4 ngày) duyệt tháng 02 trừ phép năm trước (R30). Nghỉ việc ngày làm việc cuối 06/04, áp dụng 10/04 (sau hạn
     * 31/03): 3 ngày hoàn về phép năm trước rồi hết hạn ngay, không vào số chốt; phép năm trước về 0.
     */
    @Test
    void trimAfterExpiryReturnsPreviousYearDaysThatExpireAndAreNotPaid() throws Exception {
        write(Pool.PHEP_NAM, Bucket.NAM_NAY, "5", LedgerReason.DAU_KY, LocalDate.of(2026, 6, 1));
        clock.set(LocalDate.of(2027, 2, 10));
        long id = approved("2027-04-06", "2027-04-09", GD);
        assertThat(rows(LedgerReason.DUYET_DON)).singleElement().satisfies(r -> {
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("-4");
        });

        clock.set(LocalDate.of(2027, 4, 10));
        terminate(LocalDate.of(2027, 4, 6));
        String trimRef = "LEAVE_TRIM:" + lastRecord().getId();

        assertThat(rows(LedgerReason.HUY_DON)).singleElement().satisfies(r -> {
            assertThat(r.getRefType()).isEqualTo(trimRef);
            assertThat(r.getRefId()).isEqualTo(id);
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("3");
        });
        assertThat(rows(LedgerReason.HET_HAN)).anySatisfy(r -> {
            assertThat(r.getRefType()).isEqualTo(trimRef);
            assertThat(r.getRefId()).isEqualTo(id);
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("-3");
        });
        // Còn 1 ngày năm trước chưa dùng hết hạn 01/04; không có gì để trả.
        assertThat(sum(rows(LedgerReason.NGHI_VIEC))).isEqualByComparingTo("0");
        assertThat(balance().namTruoc()).isEqualByComparingTo("0");
        assertThat(balance().total()).isEqualByComparingTo("0");
    }

    /**
     * Cùng kiểu đơn (4 ngày, trừ phép năm trước) nhưng rút ngắn trước 31/03: 3 ngày hoàn về phép năm trước, dùng được, và được chốt
     * (trả tiền) cùng ngày năm trước còn lại.
     */
    @Test
    void trimBeforeExpiryReturnsUsablePreviousYearDays() throws Exception {
        write(Pool.PHEP_NAM, Bucket.NAM_NAY, "5", LedgerReason.DAU_KY, LocalDate.of(2026, 6, 1));
        clock.set(LocalDate.of(2027, 2, 10));
        approved("2027-03-23", "2027-03-26", GD);

        clock.set(LocalDate.of(2027, 3, 25));
        terminate(LocalDate.of(2027, 3, 23));
        String trimRef = "LEAVE_TRIM:" + lastRecord().getId();

        assertThat(rows(LedgerReason.HUY_DON)).singleElement().satisfies(r -> {
            assertThat(r.getRefType()).isEqualTo(trimRef);
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("3");
        });
        assertThat(rows(LedgerReason.HET_HAN)).noneSatisfy(r -> assertThat(r.getRefType()).isEqualTo(trimRef));
        assertThat(rows(LedgerReason.NGHI_VIEC)).singleElement().satisfies(r -> {
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("-4");
        });
    }

    // ------------------------------------------------------------------ F3: nhiều lần nghỉ việc đổi cùng một đơn

    /**
     * Đơn 21/09–09/10 (8 + 7 ngày). Nghỉ việc lần 1 (08/10, ngày cuối 07/10) rút còn 21/09–07/10 (8 + 5), hoàn tác. Kỳ lương 09 duyệt
     * 10/10, kỳ 10 duyệt 02/11. Nghỉ việc lần 2 (03/11, ngày cuối 25/09) rút còn 21–25/09 (5). Tháng 09 và 10 giữ khoảng lúc duyệt
     * (8 và 5); tháng 11 hiện phần chênh của lần 2: (5 − 8) + (0 − 5) = −8.
     */
    @Test
    void eachTerminationChangeFreezesApprovedMonthsAndShowsItsOwnDifference() throws Exception {
        clock.set(LocalDate.of(2026, 9, 1));
        write(Pool.PHEP_NAM, Bucket.NAM_NAY, "20", LedgerReason.DAU_KY, LocalDate.of(2026, 9, 1));
        approved("2026-09-21", "2026-10-09", GD);
        assertThat(annualDays("2026-09")).isEqualByComparingTo("8");
        assertThat(annualDays("2026-10")).isEqualByComparingTo("7");

        clock.set(LocalDate.of(2026, 10, 8));
        terminate(LocalDate.of(2026, 10, 7));
        terminationService.undo(nv.getId());
        assertThat(annualDays("2026-09")).isEqualByComparingTo("8");
        assertThat(annualDays("2026-10")).isEqualByComparingTo("5");

        approvedPeriod("2026-09", Instant.parse("2026-10-10T03:00:00Z"));
        approvedPeriod("2026-10", Instant.parse("2026-11-02T03:00:00Z"));

        clock.set(LocalDate.of(2026, 11, 3));
        terminate(LocalDate.of(2026, 9, 25));

        assertThat(annualDays("2026-09")).isEqualByComparingTo("8");
        assertThat(annualDays("2026-10")).isEqualByComparingTo("5");
        assertThat(annualDays("2026-11")).isEqualByComparingTo("-8");
    }

    // ------------------------------------------------------------------ F4: thu hồi phép tháng năm trước

    /** Ngày làm việc cuối 09/12/2026, áp dụng 15/01/2027 sau khi đã chuyển năm: thu hồi tháng 12 từ phép năm trước; hoàn tác trả đúng ngăn. */
    @Test
    void priorYearRevokeComesFromPreviousYearBucketAndUndoRestoresSameBuckets() throws Exception {
        write(Pool.PHEP_NAM, Bucket.NAM_NAY, "4", LedgerReason.DAU_KY, LocalDate.of(2026, 6, 1));
        runMonths(YearMonth.of(2026, 12), YearMonth.of(2027, 1));
        assertThat(sum(rows(LedgerReason.CHUYEN_NAM).stream().filter(r -> r.getBucket() == Bucket.NAM_TRUOC).toList())).isEqualByComparingTo("5");

        clock.set(LocalDate.of(2027, 1, 15));
        terminate(LocalDate.of(2026, 12, 9));
        long t = lastRecord().getId();

        List<LeaveLedgerEntry> revokes = rows(LedgerReason.DIEU_CHINH);
        assertThat(revokes).hasSize(2);
        assertThat(revokes).anySatisfy(r -> {
            assertThat(r.getRefId()).isEqualTo(t * 1_000_000L + 202612);
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("-1");
        });
        assertThat(revokes).anySatisfy(r -> {
            assertThat(r.getRefId()).isEqualTo(t * 1_000_000L + 202701);
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_NAY);
            assertThat(r.getAmount()).isEqualByComparingTo("-1");
        });
        assertThat(rows(LedgerReason.NGHI_VIEC)).singleElement().satisfies(r -> {
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("-4");
        });

        terminationService.undo(nv.getId());
        List<LeaveLedgerEntry> undone = rows(LedgerReason.HOAN_TAC).stream().filter(r -> "ACCRUAL_REVOKE".equals(r.getRefType())).toList();
        assertThat(undone).hasSize(2);
        assertThat(undone).anySatisfy(r -> {
            assertThat(r.getRefId()).isEqualTo(t * 1_000_000L + 202612);
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_TRUOC);
            assertThat(r.getAmount()).isEqualByComparingTo("1");
        });
        assertThat(undone).anySatisfy(r -> {
            assertThat(r.getRefId()).isEqualTo(t * 1_000_000L + 202701);
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_NAY);
            assertThat(r.getAmount()).isEqualByComparingTo("1");
        });
        assertThat(balance().namTruoc()).isEqualByComparingTo("5");
        assertThat(balance().namNay()).isEqualByComparingTo("1");
    }

    /**
     * Ngày làm việc cuối 09/12/2026, áp dụng 10/04/2027 khi phần chuyển năm đã hết hạn: không thu hồi lại ngày tháng 12 đã hết hạn, số
     * chốt không bị trừ thêm.
     */
    @Test
    void priorYearRevokeSkipsDaysThatAlreadyExpired() throws Exception {
        write(Pool.PHEP_NAM, Bucket.NAM_NAY, "4", LedgerReason.DAU_KY, LocalDate.of(2026, 6, 1));
        runMonths(YearMonth.of(2026, 12), YearMonth.of(2027, 4));
        assertThat(rows(LedgerReason.HET_HAN)).singleElement().satisfies(r -> assertThat(r.getAmount()).isEqualByComparingTo("-5"));

        clock.set(LocalDate.of(2027, 4, 10));
        terminate(LocalDate.of(2026, 12, 9));
        long t = lastRecord().getId();

        List<LeaveLedgerEntry> revokes = rows(LedgerReason.DIEU_CHINH);
        assertThat(revokes).noneSatisfy(r -> assertThat(r.getRefId()).isEqualTo(t * 1_000_000L + 202612));
        // Tháng 01–04/2027 sau ngày làm việc cuối: thu hồi từ phép năm nay.
        assertThat(revokes).hasSize(4).allSatisfy(r -> assertThat(r.getBucket()).isEqualTo(Bucket.NAM_NAY));
        assertThat(sum(rows(LedgerReason.NGHI_VIEC))).isEqualByComparingTo("0");
        assertThat(balance().total()).isEqualByComparingTo("0");
    }
}
