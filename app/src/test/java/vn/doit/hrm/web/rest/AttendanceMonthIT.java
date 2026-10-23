package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.leave.DayPart;
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.PoiSheetGrid;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceExplanation;
import vn.doit.hrm.domain.AttendanceMonthEvent;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.AttendanceSnapshot;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentPeriod;
import vn.doit.hrm.domain.OnsiteLead;
import vn.doit.hrm.domain.Holiday;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.AttendanceMonthAction;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.ExplanationStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AttendanceExplanationRepository;
import vn.doit.hrm.repository.AttendanceMonthEventRepository;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.AttendanceSnapshotRepository;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentPeriodRepository;
import vn.doit.hrm.repository.OnsiteLeadRepository;
import vn.doit.hrm.repository.HolidayRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.attendance.AttendanceAccess;
import vn.doit.hrm.service.attendance.AttendanceDayService;
import vn.doit.hrm.service.attendance.AttendanceDayService.DayView;
import vn.doit.hrm.service.attendance.AttendanceDayService.MonthView;
import vn.doit.hrm.service.attendance.AttendanceMonthService;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Bảng công tháng (kiểu B), bảng công của tôi, chốt và mở lại. Hôm nay: thứ Năm 10/12/2026, 10:00 (Asia/Ho_Chi_Minh). Tuần làm việc
 * thứ Hai–thứ Sáu; tháng 11/2026 có 21 ngày làm việc.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(AttendanceMonthIT.FixedClockConfig.class)
class AttendanceMonthIT {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-12-10T03:00:00Z");

    /** Đồng hồ đổi được trong một test (đặt lại 10/12/2026 10:00 trước mỗi test). */
    static final class MutableClock extends Clock {

        private volatile Instant now = NOW;

        void set(Instant now) {
            this.now = now;
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
    static class FixedClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    private static Instant at(int y, int mo, int d, int h, int mi) {
        return java.time.LocalDateTime.of(y, mo, d, h, mi).atZone(VN).toInstant();
    }

    private static final RequestPostProcessor HR = user("hr-cm").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor QUAN_TRI = user("qt-cm").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor BGD = user("bgd-cm").authorities(() -> "ROLE_BGD");
    private static final RequestPostProcessor KE_TOAN = user("kt-cm").authorities(() -> "ROLE_KE_TOAN");
    private static final RequestPostProcessor HEAD = user("head-cm").authorities(() -> "ROLE_NHAN_VIEN");
    private static final RequestPostProcessor P = user("p-cm").authorities(() -> "ROLE_NHAN_VIEN");

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final String M = "2026-11";
    private static final String IDS = "$.rows[*].employee.id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private WorkSiteRepository workSiteRepository;

    @Autowired
    private WorkAssignmentRepository assignmentRepository;

    @Autowired
    private AttendancePunchRepository punchRepository;

    @Autowired
    private AttendanceSnapshotRepository snapshotRepository;

    @Autowired
    private AttendanceExplanationRepository explanationRepository;

    @Autowired
    private AttendanceMonthEventRepository eventRepository;

    @Autowired
    private LeaveRequestRepository leaveRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private PayrollPeriodRepository periodRepository;

    @Autowired
    private WorkCalendarService calendarService;

    @Autowired
    private AttendanceMonthService monthService;

    @Autowired
    private AttendanceDayService dayService;

    @Autowired
    private AttendanceAccess access;

    @Autowired
    private OnsiteLeadRepository onsiteLeadRepository;

    @Autowired
    private EmploymentPeriodRepository employmentPeriodRepository;

    private Department deptA;
    private Department deptB;
    private WorkSite customer;
    private Employee head; // trưởng phòng A
    private Employee p; // phòng A
    private Employee q; // phòng B
    private Employee intern; // phòng A, thực tập
    private Employee archived; // phòng A, đã lưu trữ, làm tới 10/11
    private Employee leaver; // phòng A, nghỉ việc, làm tới 13/11
    private Employee joiner; // vào làm 01/12

    private Employee person(String code, PersonType type, String login, Department dept) {
        Employee e = LeaveFixtures.employee(employeeRepository, code, type, LocalDate.of(2024, 1, 2));
        if (login != null) {
            e.setUser(LeaveFixtures.user(userRepository, login));
        }
        e.setDepartment(dept);
        return employeeRepository.saveAndFlush(e);
    }

    @BeforeEach
    void init() {
        clock.set(NOW);
        calendarService.setWorkingDays(
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        );
        deptA = LeaveFixtures.department(departmentRepository, "Phòng A chấm công");
        deptB = LeaveFixtures.department(departmentRepository, "Phòng B chấm công");
        customer = workSiteRepository.saveAndFlush(new WorkSite().name("Khách hàng CM").type(WorkLocationType.KHACH_HANG).active(true));
        head = person("CM0001", PersonType.NHAN_VIEN, "head-cm", deptA);
        deptA.setHead(head);
        departmentRepository.saveAndFlush(deptA);
        p = person("CM0002", PersonType.NHAN_VIEN, "p-cm", deptA);
        q = person("CM0003", PersonType.NHAN_VIEN, null, deptB);
        intern = person("CM0004", PersonType.THUC_TAP, null, deptA);
        archived = person("CM0005", PersonType.NHAN_VIEN, null, deptA);
        archived.setStatus(EmploymentStatus.DA_NGHI);
        archived.setLastWorkingDay(LocalDate.of(2026, 11, 10));
        archived.setArchivedAt(Instant.parse("2026-11-20T00:00:00Z"));
        employeeRepository.saveAndFlush(archived);
        leaver = person("CM0006", PersonType.NHAN_VIEN, null, deptA);
        leaver.setStatus(EmploymentStatus.DA_NGHI);
        leaver.setLastWorkingDay(LocalDate.of(2026, 11, 13));
        employeeRepository.saveAndFlush(leaver);
        joiner = person("CM0007", PersonType.NHAN_VIEN, null, deptA);
        joiner.setJoinDate(LocalDate.of(2026, 12, 1));
        employeeRepository.saveAndFlush(joiner);
    }

    // ------------------------------------------------------------------ dữ liệu

    private void punch(Employee who, LocalDate day, String in, String out) {
        for (Object[] x : new Object[][] { { in, AttendancePunchKind.VAO }, { out, AttendancePunchKind.RA } }) {
            if (x[0] == null) continue;
            AttendancePunch pu = new AttendancePunch();
            pu.setEmployeeId(who.getId());
            pu.setWorkDay(day);
            pu.setPunchTime(LocalTime.parse((String) x[0]));
            pu.setKind((AttendancePunchKind) x[1]);
            pu.setSource(AttendancePunchSource.GO_TAY);
            pu.setEnteredBy("test");
            pu.setEnteredAt(Instant.parse("2026-11-30T10:00:00Z"));
            punchRepository.save(pu);
        }
    }

    /** Gõ đủ 08:00–17:00 mọi ngày làm việc trong khoảng làm việc của tháng 11, trừ các ngày {@code except}. */
    private void fullMonth(Employee who, LocalDate... except) {
        Set<LocalDate> skip = Set.of(except);
        LocalDate last = who.getLastWorkingDay() == null ? NOV.atEndOfMonth() : who.getLastWorkingDay();
        for (LocalDate d = NOV.atDay(1); !d.isAfter(last); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5 && !skip.contains(d)) {
                punch(who, d, "08:00", "17:00");
            }
        }
        punchRepository.flush();
    }

    private void leave(Employee who, LeaveRequestType type, LocalDate day) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(who);
        r.setType(type);
        r.setFromDate(day);
        r.setToDate(day);
        r.setFirstPart(DayPart.CA_NGAY);
        r.setLastPart(DayPart.CA_NGAY);
        r.setDays(BigDecimal.ONE);
        r.setReasonText("thử");
        r.setStatus(LeaveRequestStatus.DA_DUYET);
        r.setApproverKind(ApproverKind.TRUONG_NHOM);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        leaveRepository.saveAndFlush(r);
    }

    private ResultActions board(RequestPostProcessor who, String... params) throws Exception {
        var req = get("/api/attendance/month").param("month", M).with(who);
        for (int i = 0; i + 1 < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(req);
    }

    private ResultActions close(RequestPostProcessor who, boolean force) throws Exception {
        return mockMvc.perform(post("/api/attendance/month/" + M + "/close").param("force", String.valueOf(force)).with(who));
    }

    private ResultActions reopen(RequestPostProcessor who, String reason) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        return mockMvc.perform(
            post("/api/attendance/month/" + M + "/reopen").with(who).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))
        );
    }

    private AttendanceSnapshot snap(Employee who) {
        return snapshotRepository
            .findByMonth(M)
            .stream()
            .filter(s -> s.getEmployeeId().equals(who.getId()))
            .findFirst()
            .orElse(null);
    }

    private static int id(Employee e) {
        return e.getId().intValue();
    }

    private String row(Employee e) {
        return "$.rows[?(@.employee.id == " + e.getId() + ")]";
    }

    // ------------------------------------------------------------------ tests

    @Test
    void closeWithUnresolvedNeedsForce() throws Exception {
        fullMonth(p);
        // q: thiếu giờ ra 05/11, chưa nhập 06/11.
        fullMonth(q, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6));
        punch(q, LocalDate.of(2026, 11, 5), "08:00", null);
        punchRepository.flush();

        close(HR, false)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendanceunresolved"))
            .andExpect(jsonPath("$.unresolved[?(@.code == 'CM0003')].name").value(hasItem("Người thử CM0003")))
            .andExpect(jsonPath("$.unresolved[?(@.code == 'CM0003')].dates[*]").value(hasItem("2026-11-05")))
            .andExpect(jsonPath("$.unresolved[?(@.code == 'CM0003')].dates[*]").value(hasItem("2026-11-06")))
            .andExpect(jsonPath("$.unresolved[*].code").value(not(hasItem("CM0002"))))
            // Hồ sơ đã lưu trữ (không có trên bảng, HR không sửa được): không nằm trong danh sách ô đỏ (R19).
            .andExpect(jsonPath("$.unresolved[*].code").value(not(hasItem("CM0005"))))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CM0003")));
        assertThat(monthService.isClosed(NOV)).isFalse();
        assertThat(snapshotRepository.findByMonth(M)).isEmpty();

        close(HR, true).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_CHOT"));
        assertThat(monthService.isClosed(NOV)).isTrue();
        AttendanceSnapshot sq = snap(q);
        assertThat(sq.getMissingDays()).isEqualTo(2);
        // ... nhưng vẫn có ảnh chụp, ô đỏ tính 0 công.
        assertThat(snap(archived)).isNotNull();
        assertThat(snap(archived).getMissingDays()).isEqualTo(7);
        assertThat(sq.getActualDays()).isEqualByComparingTo("19");

        // Đã chốt rồi thì không chốt lại.
        close(HR, true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancealreadyclosed"));
    }

    @Test
    void closeWarnsAboutPendingExplanationAndRejectsItOnClose() throws Exception {
        LocalDate day = LocalDate.of(2026, 11, 5);
        fullMonth(p);
        fullMonth(q);
        // Ngày không đỏ (đủ giờ) nhưng có giải trình đang chờ.
        AttendanceExplanation x = new AttendanceExplanation();
        x.setEmployeeId(p.getId());
        x.setWorkDay(day);
        x.setProposedIn(LocalTime.of(8, 0));
        x.setReason("Quên chấm");
        x.setStatus(ExplanationStatus.DANG_CHO);
        x.setCreatedAt(Instant.parse("2026-11-05T10:00:00Z"));
        x = explanationRepository.saveAndFlush(x);

        close(HR, false)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendanceunresolved"))
            .andExpect(jsonPath("$.unresolved[?(@.code == 'CM0002')].dates[*]").value(hasItem("2026-11-05")))
            .andExpect(jsonPath("$.unresolved[*].code").value(not(hasItem("CM0003"))));
        assertThat(explanationRepository.findById(x.getId()).orElseThrow().getStatus()).isEqualTo(ExplanationStatus.DANG_CHO);

        close(HR, true).andExpect(status().isOk());
        AttendanceExplanation after = explanationRepository.findById(x.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ExplanationStatus.TU_CHOI);
        assertThat(after.getDecisionNote()).isEqualTo("Tháng đã chốt công");
        assertThat(after.getDecidedBy()).isEqualTo("system");
        assertThat(after.getDecidedAt()).isNotNull();
        mockMvc
            .perform(get("/api/attendance/explanations/mine").param("month", M).with(P))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("TU_CHOI"))
            .andExpect(jsonPath("$[0].decisionNote").value("Tháng đã chốt công"));
    }

    @Test
    void closeWithoutUnresolvedAlsoRejectsPendingExplanationOfArchived() throws Exception {
        fullMonth(p);
        fullMonth(q);
        // Hồ sơ đã lưu trữ: không vào cảnh báo, nhưng giải trình đang chờ vẫn bị từ chối khi chốt.
        AttendanceExplanation x = new AttendanceExplanation();
        x.setEmployeeId(archived.getId());
        x.setWorkDay(LocalDate.of(2026, 11, 5));
        x.setProposedOut(LocalTime.of(17, 0));
        x.setReason("Quên chấm");
        x.setStatus(ExplanationStatus.DANG_CHO);
        x.setCreatedAt(Instant.parse("2026-11-05T10:00:00Z"));
        x = explanationRepository.saveAndFlush(x);

        close(HR, true).andExpect(status().isOk());
        assertThat(explanationRepository.findById(x.getId()).orElseThrow().getStatus()).isEqualTo(ExplanationStatus.TU_CHOI);
    }

    @Test
    void closeWritesSnapshotWithExactTotals() throws Exception {
        LocalDate late = LocalDate.of(2026, 11, 5);
        LocalDate annual = LocalDate.of(2026, 11, 6);
        fullMonth(p, late, annual);
        punch(p, late, "09:50", "17:00");
        punchRepository.flush();
        leave(p, LeaveRequestType.PHEP_NAM, annual);
        fullMonth(archived);
        fullMonth(leaver);

        close(HR, true).andExpect(status().isOk());

        AttendanceSnapshot s = snap(p);
        assertThat(s).isNotNull();
        assertThat(s.getStandardDays()).isEqualByComparingTo("21");
        assertThat(s.getActualDays()).isEqualByComparingTo("20.5");
        assertThat(s.getWorkDays()).isEqualByComparingTo("19.5");
        assertThat(s.getPaidLeaveDays()).isEqualByComparingTo("1");
        assertThat(s.getUnpaidLeaveDays()).isEqualByComparingTo("0");
        assertThat(s.getSickDays()).isEqualByComparingTo("0");
        assertThat(s.getHolidayDays()).isEqualByComparingTo("0");
        assertThat(s.getTripDays()).isEqualByComparingTo("0");
        assertThat(s.getLateCount()).isEqualTo(1);
        assertThat(s.getLateMinutes()).isEqualTo(110);
        assertThat(s.getEarlyCount()).isEqualTo(0);
        assertThat(s.getHalfDays()).isEqualTo(1);
        assertThat(s.getAbsentDays()).isEqualTo(0);
        assertThat(s.getMissingDays()).isEqualTo(0);

        // Phạm vi ảnh chụp: NHAN_VIEN có ngày làm trong tháng, kể cả hồ sơ đã lưu trữ và người nghỉ giữa tháng.
        assertThat(snap(archived)).isNotNull();
        assertThat(snap(archived).getStandardDays()).isEqualByComparingTo("21");
        assertThat(snap(archived).getActualDays()).isEqualByComparingTo("7"); // 02–06, 09–10
        assertThat(snap(leaver).getActualDays()).isEqualByComparingTo("10"); // 02–13
        assertThat(snap(intern)).isNull();
        assertThat(snap(joiner)).isNull();
        assertThat(monthService.snapshot(NOV)).containsKeys(p.getId(), archived.getId(), leaver.getId());

        // Bảng tháng đã chốt lấy tổng từ ảnh chụp.
        board(HR)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_CHOT"))
            .andExpect(jsonPath(row(p) + ".totals.actualDays").value(hasItem(20.5)))
            .andExpect(jsonPath(row(p) + ".totals.lateMinutes").value(hasItem(110)))
            .andExpect(jsonPath(row(p) + ".changedAfterClose").value(hasItem(false)))
            .andExpect(jsonPath(row(p) + ".days.length()").value(hasItem(30)))
            .andExpect(jsonPath(IDS).value(hasItem(id(leaver))))
            .andExpect(jsonPath(IDS).value(not(hasItem(id(archived)))))
            .andExpect(jsonPath(IDS).value(not(hasItem(id(intern)))))
            .andExpect(jsonPath(IDS).value(not(hasItem(id(joiner)))));
    }

    @Test
    void standardDaysIncludeHoliday() throws Exception {
        LocalDate wed = LocalDate.of(2026, 11, 18);
        if (!holidayRepository.existsByDay(wed)) {
            Holiday h = new Holiday();
            h.setDay(wed);
            h.setName("Lễ thử bảng công");
            holidayRepository.saveAndFlush(h);
        }
        fullMonth(q, wed);
        close(HR, true).andExpect(status().isOk());
        AttendanceSnapshot s = snap(q);
        assertThat(s.getStandardDays()).isEqualByComparingTo("21");
        assertThat(s.getHolidayDays()).isEqualByComparingTo("1");
        assertThat(s.getWorkDays()).isEqualByComparingTo("20");
        assertThat(s.getActualDays()).isEqualByComparingTo("21");
        assertThat(s.getMissingDays()).isEqualTo(0);
    }

    @Test
    void closedMonthRejectsEdits() throws Exception {
        fullMonth(p);
        close(HR, true).andExpect(status().isOk());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("employeeId", p.getId());
        entry.put("in", "08:30");
        entry.put("out", "17:00");
        entry.put("absent", false);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", "2026-11-10");
        body.put("entries", List.of(entry));
        mockMvc
            .perform(put("/api/attendance/day").with(HR).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancemonthclosed"));
        // Tháng chưa hết thì chưa chốt được.
        mockMvc
            .perform(post("/api/attendance/month/2026-12/close").param("force", "true").with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancemonthnotover"));
    }

    @Test
    void reopenFollowsPayrollPeriodState() throws Exception {
        fullMonth(p);
        close(HR, true).andExpect(status().isOk());
        PayrollPeriod period = new PayrollPeriod();
        period.setPeriod(M);
        period.setStatus(PayrollPeriodStatus.DA_DUYET);
        period.setApprovedBy("qt-cm");
        period.setApprovedAt(Instant.parse("2026-12-05T03:00:00Z"));
        periodRepository.saveAndFlush(period);

        reopen(HR, "Sửa giờ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancepayrollapproved"));
        assertThat(monthService.isClosed(NOV)).isTrue();

        // Quản trị mở lại kỳ lương → bảng công mở lại được.
        Map<String, Object> body = Map.of("reason", "Sai lương");
        mockMvc
            .perform(
                post("/api/payroll/periods/" + M + "/reopen").with(QUAN_TRI).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))
            )
            .andExpect(status().isOk());

        reopen(HR, "  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancereasonrequired"));
        reopen(HR, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancereasonrequired"));
        reopen(BGD, "Sửa giờ").andExpect(status().isForbidden());
        reopen(HR, "Sửa giờ").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DANG_MO"));
        assertThat(monthService.isClosed(NOV)).isFalse();
        assertThat(monthService.snapshot(NOV)).isEmpty();
        // Mở rồi thì không mở lại nữa.
        reopen(HR, "Sửa giờ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancenotclosed"));

        // Chưa có kỳ lương: mở lại được.
        mockMvc.perform(post("/api/attendance/month/2026-10/close").param("force", "true").with(QUAN_TRI)).andExpect(status().isOk());
        mockMvc
            .perform(
                post("/api/attendance/month/2026-10/reopen").with(QUAN_TRI).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"thử\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void leaveChangeAfterCloseFlagsButKeepsSnapshot() throws Exception {
        fullMonth(p);
        fullMonth(q);
        close(HR, true).andExpect(status().isOk());
        assertThat(snap(p).getActualDays()).isEqualByComparingTo("21");

        // Đơn nghỉ không lương được duyệt sau khi chốt, cho một ngày đã đi làm.
        leave(p, LeaveRequestType.KHONG_LUONG, LocalDate.of(2026, 11, 10));

        assertThat(snap(p).getActualDays()).isEqualByComparingTo("21");
        assertThat(snap(p).getUnpaidLeaveDays()).isEqualByComparingTo("0");
        board(HR)
            .andExpect(status().isOk())
            .andExpect(jsonPath(row(p) + ".changedAfterClose").value(hasItem(true)))
            .andExpect(jsonPath(row(p) + ".totals.actualDays").value(hasItem(21)))
            .andExpect(jsonPath(row(q) + ".changedAfterClose").value(hasItem(false)));
        mockMvc
            .perform(get("/api/attendance/my").param("month", M).with(P))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].totals.actualDays").value(21))
            .andExpect(jsonPath("$.rows[0].changedAfterClose").value(true));
    }

    @Test
    void scheduleChangeAfterCloseDoesNotAlterSnapshot() throws Exception {
        fullMonth(p);
        close(HR, true).andExpect(status().isOk());

        Map<String, Object> sched = new LinkedHashMap<>();
        sched.put("effectiveFrom", "2026-11-20");
        sched.put("startTime", "07:00");
        sched.put("endTime", "16:00");
        sched.put("breakStart", "12:00");
        sched.put("breakEnd", "13:00");
        sched.put("graceMinutes", 5);
        sched.put("halfDayThresholdMinutes", 90);
        sched.put("workSiteId", null);
        mockMvc
            .perform(post("/api/attendance/schedules").with(HR).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(sched)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleclosedmonth"));
        sched.put("effectiveFrom", "2026-12-01");
        mockMvc
            .perform(post("/api/attendance/schedules").with(HR).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(sched)))
            .andExpect(status().isCreated());
        board(HR).andExpect(jsonPath(row(p) + ".changedAfterClose").value(hasItem(false)));

        // Đổi ngày làm việc trong tuần sau khi chốt: ảnh chụp giữ nguyên, bảng đánh dấu thay đổi.
        calendarService.setWorkingDays(
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        );
        AttendanceSnapshot s = snap(p);
        assertThat(s.getStandardDays()).isEqualByComparingTo("21");
        assertThat(s.getActualDays()).isEqualByComparingTo("21");
        assertThat(s.getLateCount()).isEqualTo(0);
        board(HR)
            .andExpect(jsonPath(row(p) + ".totals.standardDays").value(hasItem(21)))
            .andExpect(jsonPath(row(p) + ".changedAfterClose").value(hasItem(true)));
    }

    @Test
    void viewRights() throws Exception {
        fullMonth(p);
        fullMonth(q);
        WorkAssignment w = new WorkAssignment()
            .type(WorkLocationType.KHACH_HANG)
            .fromDate(LocalDate.of(2026, 11, 16))
            .toDate(LocalDate.of(2026, 11, 20))
            .employee(q);
        w.setSite(customer);
        assignmentRepository.saveAndFlush(w);

        // Trưởng phòng (chỉ ROLE_NHAN_VIEN): phòng mình.
        board(HEAD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DANG_MO"))
            .andExpect(jsonPath(IDS).value(hasItem(id(p))))
            .andExpect(jsonPath(IDS).value(hasItem(id(head))))
            .andExpect(jsonPath(IDS).value(not(hasItem(id(q)))));
        board(HEAD, "departmentId", deptB.getId().toString()).andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(0));

        // BGD: toàn công ty; lọc phòng, lọc khách hàng.
        board(BGD).andExpect(status().isOk()).andExpect(jsonPath(IDS).value(hasItem(id(p)))).andExpect(jsonPath(IDS).value(hasItem(id(q))));
        board(BGD, "departmentId", deptB.getId().toString())
            .andExpect(jsonPath(IDS).value(hasItem(id(q))))
            .andExpect(jsonPath(IDS).value(not(hasItem(id(p)))));
        board(BGD, "siteId", customer.getId().toString())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath(IDS).value(hasItem(id(q))));
        board(HR).andExpect(status().isOk()).andExpect(jsonPath(IDS).value(hasItem(id(q))));

        // Nhân viên thường: chỉ /my.
        board(P).andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/attendance/my").param("month", M).with(P))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].employee.id").value(id(p)))
            .andExpect(jsonPath("$.rows[0].totals.actualDays").value(21))
            .andExpect(jsonPath("$.rows[0].days.length()").value(30));
        mockMvc.perform(get("/api/attendance/month/" + M + "/export.xlsx").with(P)).andExpect(status().isForbidden());
        close(P, true).andExpect(status().isForbidden());
        close(BGD, true).andExpect(status().isForbidden());
        close(HEAD, true).andExpect(status().isForbidden());

        // Kế toán: tháng chưa chốt → 403; đã chốt → 200.
        board(KE_TOAN).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/attendance/month/" + M + "/export.xlsx").with(KE_TOAN)).andExpect(status().isForbidden());
        close(KE_TOAN, true).andExpect(status().isForbidden());
        close(HR, true).andExpect(status().isOk());
        board(KE_TOAN).andExpect(status().isOk()).andExpect(jsonPath(IDS).value(hasItem(id(q))));

        byte[] xlsx = mockMvc
            .perform(get("/api/attendance/month/" + M + "/export.xlsx").with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            boolean found = false;
            for (var r : sheet) {
                if (r.getCell(0) != null && "CM0002".equals(r.getCell(0).getStringCellValue())) {
                    found = true;
                    // Mã, họ tên, phòng, rồi ngày 1..30: 02/11 là ô thứ 3 + 1.
                    assertThat(r.getCell(3 + 1).getStringCellValue()).isEqualTo("08:00–17:00");
                    assertThat(r.getCell(3).getStringCellValue()).isEqualTo("Nghỉ");
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Test
    void eventsLogged() throws Exception {
        fullMonth(p);
        close(HR, true).andExpect(status().isOk());
        reopen(HR, "Sửa giờ ngày 10").andExpect(status().isOk());
        close(QUAN_TRI, true).andExpect(status().isOk());

        List<AttendanceMonthEvent> events = eventRepository.findByMonthOrderByAtAscIdAsc(M);
        assertThat(events).extracting(AttendanceMonthEvent::getAction).containsExactly(
            AttendanceMonthAction.CHOT,
            AttendanceMonthAction.MO_LAI,
            AttendanceMonthAction.CHOT
        );
        assertThat(events).extracting(AttendanceMonthEvent::getActor).containsExactly("hr-cm", "hr-cm", "qt-cm");
        assertThat(events.get(1).getReason()).isEqualTo("Sửa giờ ngày 10");
        assertThat(events.get(0).getReason()).isNull();
        assertThat(events).allSatisfy(e -> assertThat(e.getAt()).isEqualTo(Instant.parse("2026-12-10T03:00:00Z")));
        // Chốt lại thay ảnh chụp cũ, không trùng dòng.
        assertThat(snapshotRepository.findByMonth(M).stream().filter(s -> s.getEmployeeId().equals(p.getId()))).hasSize(1);
    }

    @Test
    void accountFlagShowsBoardMenu() throws Exception {
        mockMvc
            .perform(get("/api/account").with(HEAD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewsAttendanceBoard").value(true))
            .andExpect(jsonPath("$.ownsAttendance").value(true));
        mockMvc
            .perform(get("/api/account").with(P))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewsAttendanceBoard").value(false))
            .andExpect(jsonPath("$.ownsAttendance").value(true));
        // Trưởng phòng đã nghỉ việc: không còn xem bảng phòng mình.
        head.setStatus(EmploymentStatus.DA_NGHI);
        employeeRepository.saveAndFlush(head);
        mockMvc.perform(get("/api/account").with(HEAD)).andExpect(jsonPath("$.viewsAttendanceBoard").value(false));
        board(HEAD).andExpect(status().isForbidden());
        // Thực tập sinh: không có bảng công của mình.
        intern.setUser(LeaveFixtures.user(userRepository, "intern-cm"));
        employeeRepository.saveAndFlush(intern);
        RequestPostProcessor internUser = user("intern-cm").authorities(() -> "ROLE_NHAN_VIEN");
        mockMvc.perform(get("/api/account").with(internUser)).andExpect(jsonPath("$.ownsAttendance").value(false));
        mockMvc
            .perform(get("/api/attendance/my").param("month", M).with(internUser))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(0));
    }

    // ------------------------------------------------------------------ đợt sửa cuối (R16–R24)

    @Test
    void closeOnlyAfterMonthHasEnded() throws Exception {
        fullMonth(p);
        // Ngày cuối tháng (kể cả buổi tối): tháng chưa hết.
        clock.set(at(2026, 11, 30, 22, 0));
        close(HR, true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancemonthnotover"));
        assertThat(monthService.isClosed(NOV)).isFalse();
        // Ngày đầu tháng sau: chốt được.
        clock.set(at(2026, 12, 1, 0, 5));
        close(HR, true).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_CHOT"));
    }

    private Employee rehired(String code, LocalDate previousLastDay, LocalDate newJoin) {
        return rehired(code, previousLastDay, newJoin, null);
    }

    private Employee rehired(String code, LocalDate previousLastDay, LocalDate newJoin, PersonType previousType) {
        Employee r = person(code, PersonType.NHAN_VIEN, null, deptA);
        r.setManager(head);
        r.setJoinDate(newJoin);
        r.setStatus(EmploymentStatus.DANG_LAM_VIEC);
        employeeRepository.saveAndFlush(r);
        EmploymentPeriod ep = new EmploymentPeriod();
        ep.setEmployeeId(r.getId());
        ep.setJoinDate(LocalDate.of(2024, 1, 2));
        ep.setLastWorkingDay(previousLastDay);
        ep.setTerminationDate(previousLastDay);
        ep.setCreatedAt(Instant.parse("2026-10-20T03:00:00Z"));
        ep.setCreatedBy("test");
        ep.setPersonType(previousType);
        employmentPeriodRepository.saveAndFlush(ep);
        return r;
    }

    private void weekdays(Employee who, LocalDate from, LocalDate to) {
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                punch(who, d, "08:00", "17:00");
            }
        }
        punchRepository.flush();
    }

    private static DayView dayOf(MonthView v, LocalDate d) {
        return v.days().stream().filter(x -> x.day().equals(d)).findFirst().orElseThrow();
    }

    @Test
    void rehireKeepsAttendanceOfEarlierEmployment() throws Exception {
        // Làm tới 05/10, nhận làm lại từ 20/10.
        Employee r = rehired("CM0012", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 20));
        weekdays(r, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));
        weekdays(r, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 31));
        YearMonth oct = YearMonth.of(2026, 10);

        MonthView v = dayService.month(r, oct);
        assertThat(dayOf(v, LocalDate.of(2026, 10, 1)).credit()).isEqualByComparingTo("1");
        assertThat(dayOf(v, LocalDate.of(2026, 10, 5)).credit()).isEqualByComparingTo("1");
        assertThat(dayOf(v, LocalDate.of(2026, 10, 6)).autoKind()).isEqualTo("NGOAI_HD");
        assertThat(dayOf(v, LocalDate.of(2026, 10, 12)).autoKind()).isEqualTo("NGOAI_HD");
        assertThat(dayOf(v, LocalDate.of(2026, 10, 19)).autoKind()).isEqualTo("NGOAI_HD");
        assertThat(dayOf(v, LocalDate.of(2026, 10, 20)).credit()).isEqualByComparingTo("1");
        assertThat(v.actualDays()).isEqualByComparingTo("12"); // 01, 02, 05 + 20–23, 26–30
        assertThat(v.missingDays()).isZero();
        // Trưởng nhóm gõ được ngày của lần làm trước, không gõ được khoảng nghỉ giữa hai lần.
        assertThat(access.canEnter("head-cm", r, LocalDate.of(2026, 10, 2))).isTrue();
        assertThat(access.canEnter("head-cm", r, LocalDate.of(2026, 10, 12))).isFalse();
        assertThat(access.canEnter("head-cm", r, LocalDate.of(2026, 10, 21))).isTrue();

        mockMvc.perform(post("/api/attendance/month/2026-10/close").param("force", "true").with(HR)).andExpect(status().isOk());
        AttendanceSnapshot s = snapshotRepository
            .findByMonth("2026-10")
            .stream()
            .filter(x -> x.getEmployeeId().equals(r.getId()))
            .findFirst()
            .orElse(null);
        assertThat(s).isNotNull();
        assertThat(s.getActualDays()).isEqualByComparingTo("12");
        assertThat(s.getMissingDays()).isZero();
    }

    @Test
    void rehireRightAfterMonthEndKeepsSnapshotAndPrefill() throws Exception {
        // Làm tới 30/09, nhận làm lại 01/10; tháng 9 chốt ngày 03/10.
        Employee r = rehired("CM0013", LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1));
        weekdays(r, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        clock.set(at(2026, 10, 3, 10, 0));
        mockMvc.perform(post("/api/attendance/month/2026-09/close").param("force", "true").with(HR)).andExpect(status().isOk());
        AttendanceSnapshot s = snapshotRepository
            .findByMonth("2026-09")
            .stream()
            .filter(x -> x.getEmployeeId().equals(r.getId()))
            .findFirst()
            .orElse(null);
        assertThat(s).isNotNull();
        assertThat(s.getActualDays()).isEqualByComparingTo("22");

        byte[] xlsx = mockMvc
            .perform(get("/api/payroll/imports/template.xlsx").param("period", "2026-09").with(QUAN_TRI))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        ParsedPayroll parsed = new PayrollSheetParser().parse(PoiSheetGrid.read(new ByteArrayInputStream(xlsx)));
        PayrollLine line = parsed.lines().stream().filter(l -> "CM0013".equals(l.employeeCode())).findFirst().orElse(null);
        assertThat(line).isNotNull();
        assertThat(line.amounts().get(PayrollField.NGAY_CONG_THUC_TE)).isEqualByComparingTo("22");
    }

    @Test
    void closedBoardKeepsSnapshotPeopleMissingLive() throws Exception {
        fullMonth(leaver);
        close(HR, true).andExpect(status().isOk());
        // Sửa ngày làm việc cuối về trước tháng 11: người này không còn trong khoảng làm việc của tháng, nhưng có ảnh chụp.
        leaver.setLastWorkingDay(LocalDate.of(2026, 10, 30));
        employeeRepository.saveAndFlush(leaver);
        board(HR)
            .andExpect(status().isOk())
            .andExpect(jsonPath(IDS).value(hasItem(id(leaver))))
            .andExpect(jsonPath(row(leaver) + ".changedAfterClose").value(hasItem(true)))
            .andExpect(jsonPath(row(leaver) + ".totals.actualDays").value(hasItem(10)))
            .andExpect(jsonPath(IDS).value(not(hasItem(id(archived)))));
        // Người đã lưu trữ có ảnh chụp vẫn không hiện.
    }

    @Test
    void teamLeadAndOnsiteLeadSeeScopedBoard() throws Exception {
        fullMonth(p);
        fullMonth(q);
        Employee tl = person("CM0010", PersonType.NHAN_VIEN, "tl-cm", deptB);
        p.setManager(tl);
        employeeRepository.saveAndFlush(p);
        Employee ol = person("CM0011", PersonType.NHAN_VIEN, "ol-cm", deptB);
        onsiteLeadRepository.saveAndFlush(new OnsiteLead(customer.getId(), ol.getId()));
        WorkAssignment w = new WorkAssignment()
            .type(WorkLocationType.KHACH_HANG)
            .fromDate(LocalDate.of(2026, 11, 16))
            .toDate(LocalDate.of(2026, 11, 20))
            .employee(q);
        w.setSite(customer);
        assignmentRepository.saveAndFlush(w);
        RequestPostProcessor tlUser = user("tl-cm").authorities(() -> "ROLE_NHAN_VIEN");
        RequestPostProcessor olUser = user("ol-cm").authorities(() -> "ROLE_NHAN_VIEN");

        mockMvc.perform(get("/api/account").with(tlUser)).andExpect(jsonPath("$.viewsAttendanceBoard").value(true));
        mockMvc.perform(get("/api/account").with(olUser)).andExpect(jsonPath("$.viewsAttendanceBoard").value(true));

        // Trưởng nhóm: chỉ nhóm mình, đủ cả tháng, sửa được các ngày đã qua.
        board(tlUser)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath(IDS).value(hasItem(id(p))))
            .andExpect(jsonPath(row(p) + ".days.length()").value(hasItem(30)))
            .andExpect(jsonPath(row(p) + ".totals.actualDays").value(hasItem(21)))
            .andExpect(jsonPath(row(p) + ".self").value(hasItem(false)))
            .andExpect(jsonPath(row(p) + ".editableDays[*]").value(hasItem("2026-11-10")));

        // Trưởng nhóm onsite: chỉ người onsite ở khách hàng của mình; ngày không onsite để trống, không sửa được; không có cột tổng.
        board(olUser)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath(IDS).value(hasItem(id(q))))
            .andExpect(jsonPath("$.rows[0].days.length()").value(30))
            .andExpect(jsonPath("$.rows[0].days[?(@.day == '2026-11-17')].in").value(hasItem("08:00")))
            .andExpect(jsonPath("$.rows[0].days[?(@.day == '2026-11-10')].in").value(hasItem(org.hamcrest.Matchers.nullValue())))
            .andExpect(jsonPath("$.rows[0].days[?(@.day == '2026-11-10')].credit").value(hasItem(org.hamcrest.Matchers.nullValue())))
            .andExpect(jsonPath("$.rows[0].totals.actualDays").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.rows[0].editableDays[*]").value(hasItem("2026-11-16")))
            .andExpect(jsonPath("$.rows[0].editableDays[*]").value(not(hasItem("2026-11-10"))));

        // Không có quyền chốt / mở lại.
        close(tlUser, true).andExpect(status().isForbidden());
        close(olUser, true).andExpect(status().isForbidden());
        // Tháng đã chốt: vẫn xem trong phạm vi, không sửa được ngày nào.
        close(HR, true).andExpect(status().isOk());
        board(tlUser)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath(row(p) + ".editableDays.length()").value(hasItem(0)));
        board(olUser).andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(1));
        // Nhân viên không làm trưởng nhóm nào vẫn 403.
        board(P).andExpect(status().isForbidden());
    }

    @Test
    void hrBoardMarksOwnRowAndFutureDaysNotEditable() throws Exception {
        // p là HR có hồ sơ (tài khoản p-cm): dòng của chính mình không sửa được.
        RequestPostProcessor hrP = user("p-cm").authorities(() -> "ROLE_HR");
        clock.set(at(2026, 11, 20, 10, 0));
        board(hrP)
            .andExpect(status().isOk())
            .andExpect(jsonPath(row(p) + ".self").value(hasItem(true)))
            .andExpect(jsonPath(row(p) + ".editableDays.length()").value(hasItem(0)))
            .andExpect(jsonPath(row(q) + ".self").value(hasItem(false)))
            .andExpect(jsonPath(row(q) + ".editableDays[*]").value(hasItem("2026-11-20")))
            .andExpect(jsonPath(row(q) + ".editableDays[*]").value(not(hasItem("2026-11-21"))));
    }

    // ------------------------------------------------------------------ vòng sửa 2 (R27, R28)

    @Test
    void rehireFromInternshipDoesNotTurnInternMonthsIntoStaff() throws Exception {
        // Thực tập tới 31/08, nhận làm lại làm nhân viên từ 01/09.
        Employee r = rehired("CM0014", LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1), PersonType.THUC_TAP);
        weekdays(r, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        YearMonth aug = YearMonth.of(2026, 8);

        // Tháng 8: không có dòng, không ô đỏ, mọi ngày ngoài hợp đồng.
        MonthView v = dayService.month(r, aug);
        assertThat(v.days()).allSatisfy(d -> assertThat(d.autoKind()).isEqualTo("NGOAI_HD"));
        assertThat(v.missingDays()).isZero();
        assertThat(access.canEnter("head-cm", r, LocalDate.of(2026, 8, 20))).isFalse();
        mockMvc
            .perform(get("/api/attendance/month").param("month", "2026-08").with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath(IDS).value(not(hasItem(id(r)))));
        mockMvc
            .perform(post("/api/attendance/month/2026-08/close").param("force", "false").with(HR))
            .andExpect(jsonPath("$.unresolved[*].code").value(not(hasItem("CM0014"))));
        mockMvc.perform(post("/api/attendance/month/2026-08/close").param("force", "true").with(HR)).andExpect(status().isOk());
        assertThat(snapshotRepository.findByMonth("2026-08")).noneMatch(x -> x.getEmployeeId().equals(r.getId()));
        byte[] xlsx = mockMvc
            .perform(get("/api/payroll/imports/template.xlsx").param("period", "2026-08").with(QUAN_TRI))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        ParsedPayroll parsed = new PayrollSheetParser().parse(PoiSheetGrid.read(new ByteArrayInputStream(xlsx)));
        assertThat(parsed.lines()).noneMatch(l -> "CM0014".equals(l.employeeCode()));

        // Tháng 9 (nhân viên) vẫn chấm, chụp bình thường.
        assertThat(access.canEnter("head-cm", r, LocalDate.of(2026, 9, 10))).isTrue();
        mockMvc.perform(post("/api/attendance/month/2026-09/close").param("force", "true").with(HR)).andExpect(status().isOk());
        AttendanceSnapshot s = snapshotRepository
            .findByMonth("2026-09")
            .stream()
            .filter(x -> x.getEmployeeId().equals(r.getId()))
            .findFirst()
            .orElse(null);
        assertThat(s).isNotNull();
        assertThat(s.getActualDays()).isEqualByComparingTo("22");
    }
}
