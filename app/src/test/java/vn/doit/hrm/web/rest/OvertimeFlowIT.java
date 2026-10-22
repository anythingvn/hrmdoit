package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hamcrest.Matchers;
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
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.Holiday;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.HolidayRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.leave.LeaveAccrualJob;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.leave.LeaveLedgerService;
import vn.doit.hrm.service.reminder.ReminderMailer;

/**
 * Đơn làm thêm giờ: giờ, hệ số, cờ đêm, giới hạn tháng / năm, người duyệt, duyệt với hình thức trả tiền / nghỉ bù, xác nhận giờ
 * thực tế (phần chênh duyệt lại), tự xác nhận ngày 05 tháng sau, đổi hình thức đến khi kỳ lương được duyệt (dòng đảo), quỹ nghỉ
 * bù. Hôm nay mặc định = thứ Năm 22/10/2026. Cùng cấu hình context với {@link LeaveRequestFlowIT} (dùng chung context).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(LeaveRequestFlowIT.MutableClockConfig.class)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class OvertimeFlowIT {

    private static final String API = "/api/leave/overtime";
    private static final RequestPostProcessor HR = user("hr-ot").authorities(() -> "ROLE_HR");

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
    private HolidayRepository holidayRepository;

    @Autowired
    private OvertimeRequestRepository overtimeRepository;

    @Autowired
    private PayrollPeriodRepository periodRepository;

    @Autowired
    private LeaveLedgerService ledger;

    @Autowired
    private LeaveAccrualJob accrualJob;

    @Autowired
    private EntityManager em;

    private Department dept;
    private Employee tp;
    private Employee tn;
    private Employee nv;
    private Employee nn;

    @BeforeEach
    void init() {
        clock.set(LocalDate.of(2026, 10, 22));
        dept = LeaveFixtures.department(departmentRepository, "Phòng thử làm thêm");
        tp = person("TP-OT", "tp-ot");
        dept.setHead(tp);
        departmentRepository.saveAndFlush(dept);
        tn = person("TN-OT", "tn-ot");
        nv = person("NV-OT", "nv-ot");
        nv.setManager(tn);
        employeeRepository.saveAndFlush(nv);
        nn = person("NN-OT", "nn-ot");
        person("GD-OT", "gd-ot", "ROLE_BGD");
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

    private static final RequestPostProcessor NV = as("nv-ot");
    private static final RequestPostProcessor TN = as("tn-ot");
    private static final RequestPostProcessor NN = as("nn-ot");

    private static String reg(String day, String start, String end) {
        return "{\"workDate\":\"" + day + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"content\":\"Triển khai bản mới\"}";
    }

    private ResultActions register(RequestPostProcessor who, String json) throws Exception {
        return mockMvc.perform(post(API).with(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long registerOk(String day, String start, String end) throws Exception {
        String res = register(NV, reg(day, start, end)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private ResultActions act(long id, String action, RequestPostProcessor who, String json) throws Exception {
        var req = post(API + "/{id}/" + action, id).with(who);
        if (json != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(req);
    }

    private ResultActions approve(long id, String compensation) throws Exception {
        return act(id, "approve", TN, "{\"compensation\":\"" + compensation + "\"}");
    }

    private ResultActions confirm(long id, String hours) throws Exception {
        return act(id, "confirm", NV, "{\"actualHours\":" + hours + "}");
    }

    private BigDecimal compHours(Employee e) {
        return ledger.balance(e.getId(), Pool.NGHI_BU).total();
    }

    private List<LeaveLedgerEntry> overtimeRows(Employee e) {
        return ledger.entries(e.getId()).stream().filter(r -> r.getReason() == LedgerReason.LAM_THEM).toList();
    }

    /** Đơn có sẵn (không qua API) để dựng tổng giờ đã dùng. */
    private void existing(LocalDate day, String planned, String actual, OvertimeStatus st) {
        OvertimeRequest r = new OvertimeRequest();
        r.setEmployee(nv);
        r.setWorkDate(day);
        r.setStartTime(LocalTime.of(8, 0));
        r.setEndTime(LocalTime.of(9, 0));
        r.setPlannedHours(new BigDecimal(planned));
        r.setActualHours(actual == null ? null : new BigDecimal(actual));
        r.setRate(200);
        r.setNight(false);
        r.setStatus(st);
        r.setApproverKind(ApproverKind.TRUONG_NHOM);
        r.setApproverEmployee(tn);
        r.setCreatedAt(clock.instant());
        r.setSubmittedBy("nv-ot");
        overtimeRepository.saveAndFlush(r);
    }

    private void approvedPeriod(String period) {
        periodRepository.insertIfMissing(period);
        PayrollPeriod p = periodRepository.findByPeriod(period).orElseThrow();
        p.setStatus(PayrollPeriodStatus.DA_DUYET);
        periodRepository.saveAndFlush(p);
    }

    // ------------------------------------------------------------------ đăng ký

    @Test
    void registerComputesHoursRateAndTeamLeadApprover() throws Exception {
        register(NV, reg("2026-10-22", "18:00", "21:00"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.plannedHours").value(3))
            .andExpect(jsonPath("$.rate").value(150))
            .andExpect(jsonPath("$.night").value(false))
            .andExpect(jsonPath("$.status").value("CHO_DUYET"))
            .andExpect(jsonPath("$.approverKind").value("TRUONG_NHOM"))
            .andExpect(jsonPath("$.approverEmployeeId").value(tn.getId()))
            .andExpect(jsonPath("$.compensation").doesNotExist());
        // Chủ nhật 200%, qua đêm 22:00–02:00 = 4 giờ có giờ đêm.
        register(NV, reg("2026-10-25", "22:00", "02:00"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.plannedHours").value(4))
            .andExpect(jsonPath("$.rate").value(200))
            .andExpect(jsonPath("$.night").value(true));
        // Ngày lễ (kể cả rơi vào ngày thường) 300%.
        Holiday h = new Holiday();
        h.setDay(LocalDate.of(2026, 11, 19));
        h.setName("Ngày lễ thử");
        holidayRepository.saveAndFlush(h);
        register(NV, reg("2026-11-19", "08:00", "12:30")).andExpect(status().isCreated()).andExpect(jsonPath("$.rate").value(300)).andExpect(jsonPath("$.plannedHours").value(4.5));
        mockMvc.perform(get(API + "/mine").with(NV)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get(API + "/inbox").with(TN)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get(API + "/inbox").with(NN)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void registerValidation() throws Exception {
        register(NV, reg("2026-10-22", "18:00", "18:00")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.overtimetimeinvalid"));
        register(NV, "{\"workDate\":\"2026-10-22\",\"content\":\"x\"}").andExpect(status().isBadRequest());
        register(NV, reg("2026-08-01", "18:00", "20:00")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.leavepasttoofar"));
        registerOk("2026-10-23", "18:00", "20:00");
        register(NV, reg("2026-10-23", "19:00", "21:00")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.overtimeoverlap"));
        registerOk("2026-10-23", "20:00", "21:00");
    }

    @Test
    void internAndServiceStaffForbidden() throws Exception {
        Employee tt = LeaveFixtures.employee(employeeRepository, "TT-OT", PersonType.THUC_TAP, LocalDate.of(2026, 9, 1));
        tt.setUser(LeaveFixtures.user(userRepository, "tt-ot"));
        employeeRepository.saveAndFlush(tt);
        register(as("tt-ot"), reg("2026-10-22", "18:00", "21:00"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.leavenotapplicable"));
        mockMvc.perform(get(API + "/mine").with(as("tt-ot"))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ giới hạn

    @Test
    void monthLimitCountsApprovedAndActualHours() throws Exception {
        existing(LocalDate.of(2026, 10, 3), "20", "30", OvertimeStatus.HOAN_TAT); // tính giờ thực tế 30
        existing(LocalDate.of(2026, 10, 4), "8", null, OvertimeStatus.DA_DUYET); // tính giờ đăng ký 8
        existing(LocalDate.of(2026, 10, 10), "10", null, OvertimeStatus.CHO_DUYET); // chưa duyệt: không tính
        existing(LocalDate.of(2026, 10, 11), "10", null, OvertimeStatus.TU_CHOI);
        existing(LocalDate.of(2026, 9, 26), "10", null, OvertimeStatus.DA_DUYET); // tháng khác
        register(NV, reg("2026-10-22", "18:00", "21:00"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.overtimelimit"))
            .andExpect(jsonPath("$.detail").value(Matchers.containsString("38")))
            .andExpect(jsonPath("$.detail").value(Matchers.containsString("40")));
        long id = registerOk("2026-10-22", "18:00", "20:00"); // 38 + 2 = 40: được
        // Duyệt kiểm tra lại: một đơn 2 giờ khác đã được duyệt trước thì đơn này vượt.
        long other = registerOk("2026-10-23", "18:00", "20:00");
        approve(other, "TRA_TIEN").andExpect(status().isOk());
        approve(id, "TRA_TIEN").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.overtimelimit"));
        // Giới hạn cấu hình được.
        em.createNativeQuery("update leave_setting set ot_month_cap = 50 where id = 1").executeUpdate();
        em.flush();
        em.clear();
        approve(id, "TRA_TIEN").andExpect(status().isOk());
    }

    @Test
    void yearLimit() throws Exception {
        for (int m = 1; m <= 9; m++) {
            existing(LocalDate.of(2026, m, 5), "22", null, OvertimeStatus.DA_DUYET); // 198 giờ
        }
        existing(LocalDate.of(2025, 12, 5), "30", null, OvertimeStatus.HOAN_TAT);
        register(NV, reg("2026-10-22", "18:00", "21:00"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.overtimelimit"))
            .andExpect(jsonPath("$.detail").value(Matchers.containsString("200")));
        registerOk("2026-10-22", "18:00", "20:00");
    }

    // ------------------------------------------------------------------ duyệt, xác nhận

    @Test
    void approveRequiresCompensation() throws Exception {
        long id = registerOk("2026-10-22", "18:00", "21:00");
        act(id, "approve", TN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.overtimecompensationrequired"));
        act(id, "approve", TN, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.overtimecompensationrequired"));
        act(id, "approve", TN, "{\"compensation\":\"KHAC\"}").andExpect(status().isBadRequest());
        approve(id, "NGHI_BU").andExpect(status().isOk()).andExpect(jsonPath("$.compensation").value("NGHI_BU")).andExpect(jsonPath("$.onBehalf").value(false));
    }

    @Test
    void confirmWithinPlannedCompletesAndCredits() throws Exception {
        long id = registerOk("2026-10-22", "18:00", "21:00");
        act(id, "approve", NV, "{\"compensation\":\"NGHI_BU\"}").andExpect(status().isForbidden());
        act(id, "approve", NN, "{\"compensation\":\"NGHI_BU\"}").andExpect(status().isForbidden());
        approve(id, "NGHI_BU").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_DUYET")).andExpect(jsonPath("$.compensation").value("NGHI_BU"));
        assertThat(compHours(nv)).isEqualByComparingTo("0");
        // Chưa làm xong thì chưa xác nhận được.
        confirm(id, "3").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.overtimeconfirmearly"));
        clock.set(LocalDate.of(2026, 10, 23));
        act(id, "confirm", TN, "{\"actualHours\":3}").andExpect(status().isForbidden());
        confirm(id, "0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.overtimehoursinvalid"));
        confirm(id, "3").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HOAN_TAT")).andExpect(jsonPath("$.actualHours").value(3));
        assertThat(compHours(nv)).isEqualByComparingTo("3");
        List<LeaveLedgerEntry> rows = overtimeRows(nv);
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.getBucket()).isEqualTo(Bucket.NAM_NAY);
            assertThat(r.getAmount()).isEqualByComparingTo("3");
            assertThat(r.getRefType()).isEqualTo("OVERTIME");
            assertThat(r.getRefId()).isEqualTo(id);
        });
        confirm(id, "3").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.overtimestatus"));
        assertThat(overtimeRows(nv)).hasSize(1);
    }

    @Test
    void confirmBelowPlannedUsesActualHoursPaidRequestPostsNothing() throws Exception {
        long id = registerOk("2026-10-22", "18:00", "21:00");
        approve(id, "TRA_TIEN").andExpect(status().isOk());
        clock.set(LocalDate.of(2026, 10, 23));
        confirm(id, "2.5").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HOAN_TAT")).andExpect(jsonPath("$.actualHours").value(2.5));
        assertThat(overtimeRows(nv)).isEmpty();
    }

    @Test
    void confirmAbovePlannedNeedsExtraApproval() throws Exception {
        long id = registerOk("2026-10-22", "18:00", "21:00");
        approve(id, "NGHI_BU").andExpect(status().isOk());
        clock.set(LocalDate.of(2026, 10, 23));
        confirm(id, "4").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHO_XAC_NHAN_THEM"));
        assertThat(compHours(nv)).isEqualByComparingTo("0");
        mockMvc.perform(get(API + "/inbox").with(TN)).andExpect(jsonPath("$[?(@.id==" + id + ")].status").value("CHO_XAC_NHAN_THEM"));
        act(id, "approve-extra", NV, null).andExpect(status().isForbidden());
        act(id, "approve-extra", TN, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HOAN_TAT")).andExpect(jsonPath("$.actualHours").value(4));
        assertThat(compHours(nv)).isEqualByComparingTo("4");

        long id2 = registerOk("2026-10-21", "18:00", "20:00");
        approve(id2, "NGHI_BU").andExpect(status().isOk());
        confirm(id2, "5").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHO_XAC_NHAN_THEM"));
        act(id2, "reject-extra", TN, "{\"note\":\"Chỉ đăng ký 2 giờ\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HOAN_TAT")).andExpect(jsonPath("$.actualHours").value(2));
        assertThat(compHours(nv)).isEqualByComparingTo("6");
    }

    @Test
    void rejectNeedsNoteAndWithdrawOnlyWhilePending() throws Exception {
        long id = registerOk("2026-10-22", "18:00", "21:00");
        act(id, "reject", TN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.leavenoterequired"));
        act(id, "reject", TN, "{\"note\":\"Không cần\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TU_CHOI"));
        long id2 = registerOk("2026-10-23", "18:00", "21:00");
        act(id2, "withdraw", TN, null).andExpect(status().isForbidden());
        act(id2, "withdraw", NV, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_RUT"));
        act(id2, "approve", TN, "{\"compensation\":\"TRA_TIEN\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.overtimestatus"));
    }

    @Test
    void hrDecidesOnBehalfAndOthersCannotSee() throws Exception {
        long id = registerOk("2026-10-22", "18:00", "21:00");
        mockMvc.perform(get(API + "/{id}", id).with(NN)).andExpect(status().isForbidden());
        mockMvc.perform(get(API + "/{id}", id).with(as("tp-ot"))).andExpect(status().isOk());
        act(id, "approve", HR, "{\"compensation\":\"TRA_TIEN\"}").andExpect(status().isOk()).andExpect(jsonPath("$.onBehalf").value(true));
    }

    // ------------------------------------------------------------------ tự xác nhận

    @Test
    void autoConfirmOnConfirmDayOfNextMonth() throws Exception {
        long comp = registerOk("2026-10-20", "18:00", "21:00");
        approve(comp, "NGHI_BU").andExpect(status().isOk());
        long paid = registerOk("2026-10-21", "18:00", "20:00");
        approve(paid, "TRA_TIEN").andExpect(status().isOk());
        long extra = registerOk("2026-10-19", "18:00", "20:00");
        approve(extra, "NGHI_BU").andExpect(status().isOk());
        clock.set(LocalDate.of(2026, 10, 23));
        confirm(extra, "3").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHO_XAC_NHAN_THEM"));
        clock.set(LocalDate.of(2026, 11, 2));
        long nextMonth = registerOk("2026-11-02", "08:00", "10:00");
        approve(nextMonth, "NGHI_BU").andExpect(status().isOk());

        clock.set(LocalDate.of(2026, 11, 4));
        accrualJob.autoConfirmOvertime();
        em.flush();
        em.clear();
        assertThat(overtimeRepository.findById(comp).orElseThrow().getStatus()).isEqualTo(OvertimeStatus.DA_DUYET);

        clock.set(LocalDate.of(2026, 11, 5));
        accrualJob.autoConfirmOvertime();
        em.flush();
        em.clear();
        OvertimeRequest c = overtimeRepository.findById(comp).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(OvertimeStatus.HOAN_TAT);
        assertThat(c.isAutoConfirmed()).isTrue();
        assertThat(c.getActualHours()).isEqualByComparingTo("3");
        assertThat(c.getConfirmedAt()).isNotNull();
        OvertimeRequest p = overtimeRepository.findById(paid).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(OvertimeStatus.HOAN_TAT);
        assertThat(p.isAutoConfirmed()).isTrue();
        // Phần chênh chưa được duyệt đến hạn: tính bằng giờ đăng ký.
        OvertimeRequest x = overtimeRepository.findById(extra).orElseThrow();
        assertThat(x.getStatus()).isEqualTo(OvertimeStatus.HOAN_TAT);
        assertThat(x.isAutoConfirmed()).isTrue();
        assertThat(x.getActualHours()).isEqualByComparingTo("2");
        assertThat(overtimeRepository.findById(nextMonth).orElseThrow().getStatus()).isEqualTo(OvertimeStatus.DA_DUYET);
        assertThat(compHours(nv)).isEqualByComparingTo("5");
        // Chạy lại không ghi trùng.
        accrualJob.autoConfirmOvertime();
        assertThat(compHours(nv)).isEqualByComparingTo("5");
        mockMvc.perform(get(API + "/{id}", comp).with(NV)).andExpect(jsonPath("$.autoConfirmed").value(true));
    }

    // ------------------------------------------------------------------ đổi hình thức

    @Test
    void switchCompensationWritesReversalAndBack() throws Exception {
        long id = registerOk("2026-10-22", "18:00", "21:00");
        approve(id, "NGHI_BU").andExpect(status().isOk());
        clock.set(LocalDate.of(2026, 10, 23));
        confirm(id, "3").andExpect(status().isOk());
        assertThat(compHours(nv)).isEqualByComparingTo("3");
        act(id, "compensation", NV, "{\"compensation\":\"TRA_TIEN\"}").andExpect(status().isForbidden());
        act(id, "compensation", TN, "{\"compensation\":\"TRA_TIEN\"}").andExpect(status().isOk()).andExpect(jsonPath("$.compensation").value("TRA_TIEN"));
        assertThat(compHours(nv)).isEqualByComparingTo("0");
        List<LeaveLedgerEntry> rows = overtimeRows(nv);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getAmount()).isEqualByComparingTo("-3");
        assertThat(rows.get(1).getRefType()).isEqualTo("OVERTIME_REVERSAL:1");
        assertThat(rows.get(1).getRefId()).isEqualTo(id);
        // Đổi lại nghỉ bù (HR), rồi trả tiền lần nữa: mỗi lần một dòng mới, không trùng chỉ mục.
        act(id, "compensation", HR, "{\"compensation\":\"NGHI_BU\"}").andExpect(status().isOk());
        assertThat(compHours(nv)).isEqualByComparingTo("3");
        act(id, "compensation", TN, "{\"compensation\":\"TRA_TIEN\"}").andExpect(status().isOk());
        assertThat(compHours(nv)).isEqualByComparingTo("0");
        assertThat(overtimeRows(nv)).extracting(LeaveLedgerEntry::getRefType).containsExactly("OVERTIME", "OVERTIME_REVERSAL:1", "OVERTIME:2", "OVERTIME_REVERSAL:2");
        // Đổi trước khi xác nhận: chỉ đổi hình thức, không ghi sổ.
        long id2 = registerOk("2026-10-21", "18:00", "20:00");
        approve(id2, "TRA_TIEN").andExpect(status().isOk());
        act(id2, "compensation", TN, "{\"compensation\":\"NGHI_BU\"}").andExpect(status().isOk());
        assertThat(overtimeRows(nv)).hasSize(4);
        confirm(id2, "2").andExpect(status().isOk());
        assertThat(compHours(nv)).isEqualByComparingTo("2");
    }

    @Test
    void switchBlockedAfterPayrollApprovedOrWhenHoursAlreadyUsed() throws Exception {
        long id = registerOk("2026-10-22", "18:00", "21:00");
        approve(id, "NGHI_BU").andExpect(status().isOk());
        clock.set(LocalDate.of(2026, 10, 23));
        confirm(id, "3").andExpect(status().isOk());
        // Giờ nghỉ bù đã dùng (HR điều chỉnh −2): không đủ để đảo.
        ledger.post(nv.getId(), Pool.NGHI_BU, List.of(new Entry(Pool.NGHI_BU, Bucket.NAM_NAY, new BigDecimal("-2"))), LedgerReason.DIEU_CHINH, null, null, "thử");
        act(id, "compensation", TN, "{\"compensation\":\"TRA_TIEN\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
        assertThat(compHours(nv)).isEqualByComparingTo("1");
        ledger.post(nv.getId(), Pool.NGHI_BU, List.of(new Entry(Pool.NGHI_BU, Bucket.NAM_NAY, new BigDecimal("2"))), LedgerReason.DIEU_CHINH, null, null, "thử");
        approvedPeriod("2026-10");
        act(id, "compensation", TN, "{\"compensation\":\"TRA_TIEN\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.overtimeperiodlocked"));
        act(id, "compensation", HR, "{\"compensation\":\"TRA_TIEN\"}").andExpect(status().isBadRequest());
        assertThat(compHours(nv)).isEqualByComparingTo("3");
    }

    // ------------------------------------------------------------------ dùng quỹ nghỉ bù

    @Test
    void compensatoryLeaveUsesOvertimeHours() throws Exception {
        long id = registerOk("2026-10-21", "17:00", "23:00");
        approve(id, "NGHI_BU").andExpect(status().isOk());
        clock.set(LocalDate.of(2026, 10, 23));
        confirm(id, "6").andExpect(status().isOk());
        assertThat(compHours(nv)).isEqualByComparingTo("6");
        String leave = "{\"type\":\"NGHI_BU\",\"fromDate\":\"2026-10-26\",\"toDate\":\"2026-10-26\",\"firstPart\":\"CA_NGAY\",\"lastPart\":\"CA_NGAY\"}";
        mockMvc
            .perform(post("/api/leave/requests").with(NV).contentType(MediaType.APPLICATION_JSON).content(leave))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
        long id2 = registerOk("2026-10-22", "18:00", "20:00");
        approve(id2, "NGHI_BU").andExpect(status().isOk());
        confirm(id2, "2").andExpect(status().isOk());
        assertThat(compHours(nv)).isEqualByComparingTo("8");
        String res = mockMvc
            .perform(post("/api/leave/requests").with(NV).contentType(MediaType.APPLICATION_JSON).content(leave))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long leaveId = ((Number) JsonPath.read(res, "$.id")).longValue();
        mockMvc.perform(post("/api/leave/requests/{id}/approve", leaveId).with(TN)).andExpect(status().isOk());
        assertThat(compHours(nv)).isEqualByComparingTo("0");
        // Quỹ đã dùng hết: không đảo được nữa.
        act(id2, "compensation", TN, "{\"compensation\":\"TRA_TIEN\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.insufficientbalance"));
    }
}
