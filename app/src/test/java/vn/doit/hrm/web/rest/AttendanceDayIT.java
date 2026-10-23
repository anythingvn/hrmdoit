package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import vn.doit.hr.attendance.DayCredit;
import vn.doit.hr.leave.DayPart;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.AttendanceSchedule;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.Holiday;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.OnsiteLead;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.AttendanceScheduleRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.HolidayRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.OnsiteLeadRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.attendance.AttendanceAccess;
import vn.doit.hrm.service.attendance.AttendanceDayService;
import vn.doit.hrm.service.attendance.AttendanceDayService.DayView;
import vn.doit.hrm.service.attendance.AttendanceDayService.MonthView;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Tính công từng ngày, quyền gõ và màn gõ theo ngày. Hôm nay: thứ Sáu 20/11/2026, 10:00 (Asia/Ho_Chi_Minh). Tuần làm việc thứ Hai–thứ Sáu.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(AttendanceDayIT.FixedClockConfig.class)
class AttendanceDayIT {

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final RequestPostProcessor HR = user("hr-cc").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor KE_TOAN = user("kt-cc").authorities(() -> "ROLE_KE_TOAN");
    private static final RequestPostProcessor LEAD = as("lead-cc");
    private static final RequestPostProcessor ONSITE_LEAD = as("ol-cc");

    private static final LocalDate THU = LocalDate.of(2026, 11, 19);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final String IDS = "$.groups[*].rows[*].employeeId";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkSiteRepository workSiteRepository;

    @Autowired
    private WorkAssignmentRepository assignmentRepository;

    @Autowired
    private OnsiteLeadRepository onsiteLeadRepository;

    @Autowired
    private AttendanceScheduleRepository scheduleRepository;

    @Autowired
    private AttendancePunchRepository punchRepository;

    @Autowired
    private AttendanceMonthRepository monthRepository;

    @Autowired
    private LeaveRequestRepository leaveRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private WorkCalendarService calendarService;

    @Autowired
    private AttendanceDayService dayService;

    @Autowired
    private AttendanceAccess access;

    @Autowired
    private PayrollPeriodRepository periodRepository;

    private WorkSite customer;
    private Employee lead; // trưởng nhóm của a, b, e, intern, archived
    private Employee otherLead; // trưởng nhóm của c, d
    private Employee onsiteLead; // trưởng nhóm onsite của khách hàng
    private Employee a;
    private Employee b;
    private Employee c;
    private Employee d; // onsite ở khách hàng tới 19/11
    private Employee e; // onsite ở khách hàng cả tháng 11
    private Employee intern;
    private Employee archived;

    private static RequestPostProcessor as(String login) {
        return user(login).authorities(() -> "ROLE_NHAN_VIEN");
    }

    private Employee person(String code, PersonType type, String login, Employee manager) {
        Employee p = LeaveFixtures.employee(employeeRepository, code, type, LocalDate.of(2024, 1, 2));
        if (login != null) {
            p.setUser(LeaveFixtures.user(userRepository, login));
        }
        p.setManager(manager);
        return employeeRepository.saveAndFlush(p);
    }

    private void assign(Employee who, WorkLocationType type, WorkSite site, LocalDate from, LocalDate to) {
        WorkAssignment w = new WorkAssignment().type(type).fromDate(from).toDate(to).employee(who);
        w.setSite(site);
        assignmentRepository.saveAndFlush(w);
    }

    private void leave(Employee who, LeaveRequestType type, LeaveRequestStatus st, LocalDate from, LocalDate to, DayPart first, DayPart last) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(who);
        r.setType(type);
        r.setFromDate(from);
        r.setToDate(to);
        r.setFirstPart(first);
        r.setLastPart(last);
        r.setDays(BigDecimal.ONE);
        r.setReasonText("thử");
        r.setStatus(st);
        r.setApproverKind(ApproverKind.TRUONG_NHOM);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        leaveRepository.saveAndFlush(r);
    }

    @BeforeEach
    void init() {
        calendarService.setWorkingDays(
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        );
        customer = workSiteRepository.saveAndFlush(new WorkSite().name("Khách hàng CC").type(WorkLocationType.KHACH_HANG).active(true));
        lead = person("CC0001", PersonType.NHAN_VIEN, "lead-cc", null);
        otherLead = person("CC0002", PersonType.NHAN_VIEN, "otherlead-cc", null);
        onsiteLead = person("CC0003", PersonType.NHAN_VIEN, "ol-cc", otherLead);
        a = person("CC0004", PersonType.NHAN_VIEN, "a-cc", lead);
        b = person("CC0005", PersonType.NHAN_VIEN, null, lead);
        c = person("CC0006", PersonType.NHAN_VIEN, "c-cc", otherLead);
        d = person("CC0007", PersonType.NHAN_VIEN, null, otherLead);
        e = person("CC0008", PersonType.NHAN_VIEN, null, lead);
        intern = person("CC0009", PersonType.THUC_TAP, null, lead);
        archived = person("CC0010", PersonType.NHAN_VIEN, null, lead);
        archived.setArchivedAt(Instant.parse("2026-11-01T00:00:00Z"));
        employeeRepository.saveAndFlush(archived);
        onsiteLeadRepository.saveAndFlush(new OnsiteLead(customer.getId(), onsiteLead.getId()));
        assign(d, WorkLocationType.KHACH_HANG, customer, LocalDate.of(2026, 11, 1), THU);
        assign(e, WorkLocationType.KHACH_HANG, customer, LocalDate.of(2026, 11, 1), null);
    }

    private ResultActions getDay(RequestPostProcessor who, LocalDate day) throws Exception {
        return mockMvc.perform(get("/api/attendance/day").param("date", day.toString()).with(who));
    }

    private static Map<String, Object> entry(Employee who, String in, String out, boolean absent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", who.getId());
        m.put("in", in);
        m.put("out", out);
        m.put("absent", absent);
        return m;
    }

    @SafeVarargs
    private ResultActions putDay(RequestPostProcessor who, LocalDate day, Map<String, Object>... entries) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", day.toString());
        body.put("entries", List.of(entries));
        return mockMvc.perform(
            put("/api/attendance/day").with(who).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))
        );
    }

    private static int id(Employee p) {
        return p.getId().intValue();
    }

    private List<AttendancePunch> goTay(Employee who, LocalDate day) {
        List<AttendancePunch> out = new ArrayList<>();
        for (AttendancePunch p : punchRepository.findByEmployeeIdAndWorkDayOrderByPunchTimeAsc(who.getId(), day)) {
            if (p.getSource() == AttendancePunchSource.GO_TAY) {
                out.add(p);
            }
        }
        return out;
    }

    @Test
    void leadSeesOnlyOwnTeam() throws Exception {
        getDay(LEAD, THU)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.date").value("2026-11-19"))
            .andExpect(jsonPath("$.graceMinutes").value(5))
            .andExpect(jsonPath("$.halfDayThresholdMinutes").value(90))
            .andExpect(jsonPath(IDS, hasItems(id(a), id(b), id(e))))
            .andExpect(jsonPath(IDS, not(hasItem(id(c)))))
            .andExpect(jsonPath(IDS, not(hasItem(id(d)))))
            .andExpect(jsonPath(IDS, not(hasItem(id(lead)))))
            .andExpect(jsonPath("$.groups[?(@.group == 'VP')].label").value(hasItem("Văn phòng")))
            .andExpect(jsonPath("$.groups[?(@.group == 'VP')].hours.start").value(hasItem("08:00")));
        assertThat(access.enterableEmployees("lead-cc", THU)).extracting(Employee::getId).containsExactlyInAnyOrder(a.getId(), b.getId(), e.getId());
        assertThat(access.canEnter("lead-cc", c, THU)).isFalse();
        assertThat(access.canEnter("lead-cc", a, THU)).isTrue();
    }

    @Test
    void onsiteLeadSeesOnlySiteOnThatDay() throws Exception {
        String site = "SITE:" + customer.getId();
        getDay(ONSITE_LEAD, THU)
            .andExpect(status().isOk())
            .andExpect(jsonPath(IDS, hasItems(id(d), id(e))))
            .andExpect(jsonPath(IDS, not(hasItem(id(a)))))
            .andExpect(jsonPath("$.groups[?(@.group == '" + site + "')].label").value(hasItem("Onsite · Khách hàng CC")))
            .andExpect(jsonPath("$.groups.length()").value(1));
        // Hết hạn phân công (d onsite tới 19/11) thì không còn thấy.
        getDay(ONSITE_LEAD, TODAY).andExpect(status().isOk()).andExpect(jsonPath(IDS, hasItem(id(e)))).andExpect(jsonPath(IDS, not(hasItem(id(d)))));
        assertThat(access.canEnter("ol-cc", d, THU)).isTrue();
        assertThat(access.canEnter("ol-cc", d, TODAY)).isFalse();
        putDay(ONSITE_LEAD, TODAY, entry(d, "08:00", null, false)).andExpect(status().isForbidden());
        putDay(ONSITE_LEAD, THU, entry(d, "08:00", "17:00", false)).andExpect(status().isOk());
    }

    @Test
    void hrSeesAll() throws Exception {
        getDay(HR, THU)
            .andExpect(status().isOk())
            .andExpect(jsonPath(IDS, hasItems(id(lead), id(otherLead), id(onsiteLead), id(a), id(b), id(c), id(d), id(e))));
        putDay(HR, THU, entry(c, "08:00", "17:00", false)).andExpect(status().isOk()).andExpect(jsonPath("$[0].credit").value(1));
    }

    @Test
    void internsAndArchivedExcluded() throws Exception {
        for (RequestPostProcessor who : new RequestPostProcessor[] { HR, LEAD }) {
            getDay(who, THU).andExpect(jsonPath(IDS, not(hasItem(id(intern))))).andExpect(jsonPath(IDS, not(hasItem(id(archived)))));
        }
        putDay(HR, THU, entry(archived, "08:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeearchived"));
        putDay(LEAD, THU, entry(intern, "08:00", "17:00", false)).andExpect(status().isForbidden());
    }

    @Test
    void cannotEnterSelf() throws Exception {
        putDay(LEAD, THU, entry(lead, "08:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendanceself"));
        // Kể cả HR có hồ sơ.
        Employee hrPerson = person("CC0011", PersonType.NHAN_VIEN, "hr2-cc", null);
        putDay(user("hr2-cc").authorities(() -> "ROLE_HR"), THU, entry(hrPerson, "08:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendanceself"));
        assertThat(goTay(lead, THU)).isEmpty();
    }

    @Test
    void cannotEnterFuture() throws Exception {
        putDay(LEAD, TODAY.plusDays(1), entry(a, "08:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancefuture"));
        // Hôm nay lúc 10:00: giờ ra 18:00 chưa tới.
        putDay(LEAD, TODAY, entry(a, "08:00", "18:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancefuture"));
        assertThat(goTay(a, TODAY)).isEmpty();
        // Giờ vào đã qua thì gõ được, công còn thiếu giờ ra.
        putDay(LEAD, TODAY, entry(a, "08:00", null, false))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("THIEU_GIO"))
            .andExpect(jsonPath("$[0].in").value("08:00"));
    }

    @Test
    void closedMonthRejected() throws Exception {
        AttendanceMonth m = new AttendanceMonth();
        m.setMonth("2026-10");
        m.setStatus(AttendanceMonthStatus.DA_CHOT);
        monthRepository.saveAndFlush(m);
        putDay(LEAD, LocalDate.of(2026, 10, 30), entry(a, "08:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancemonthclosed"));
        assertThat(goTay(a, LocalDate.of(2026, 10, 30))).isEmpty();
        putDay(LEAD, LocalDate.of(2026, 11, 2), entry(a, "08:00", "17:00", false)).andExpect(status().isOk());
    }

    @Test
    void onsiteUsesCustomerHours() throws Exception {
        AttendanceSchedule s = new AttendanceSchedule();
        s.setWorkSiteId(customer.getId());
        s.setEffectiveFrom(LocalDate.of(2026, 11, 1));
        s.setStartTime(LocalTime.of(8, 30));
        s.setEndTime(LocalTime.of(17, 30));
        s.setBreakStart(LocalTime.of(12, 0));
        s.setBreakEnd(LocalTime.of(13, 0));
        s.setUpdatedBy("test");
        s.setUpdatedAt(Instant.now());
        scheduleRepository.saveAndFlush(s);

        // Muộn 90 phút so với 08:30 → vẫn 1 công (theo giờ công ty sẽ là muộn 120 phút → nửa công).
        putDay(ONSITE_LEAD, THU, entry(e, "10:00", "17:30", false))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].credit").value(1))
            .andExpect(jsonPath("$[0].lateMinutes").value(90))
            .andExpect(jsonPath("$[0].group").value("SITE:" + customer.getId()));
        putDay(ONSITE_LEAD, THU, entry(e, "10:01", "17:30", false)).andExpect(jsonPath("$[0].credit").value(0.5));
        getDay(ONSITE_LEAD, THU).andExpect(jsonPath("$.groups[0].hours.start").value("08:30"));
        // Người văn phòng cùng ngày vẫn theo giờ công ty.
        putDay(LEAD, THU, entry(a, "10:00", "17:00", false)).andExpect(jsonPath("$[0].credit").value(0.5));
    }

    @Test
    void bothLeadsMayEnterSameDay() throws Exception {
        assertThat(access.canEnter("lead-cc", e, THU)).isTrue();
        assertThat(access.canEnter("ol-cc", e, THU)).isTrue();
        putDay(LEAD, THU, entry(e, "08:00", "17:00", false)).andExpect(status().isOk());
        putDay(ONSITE_LEAD, THU, entry(e, "08:20", "17:00", false)).andExpect(status().isOk());
        // PUT thay toàn bộ lần chấm gõ tay của người đó trong ngày: người gõ sau thắng, công không cộng dồn.
        assertThat(goTay(e, THU)).hasSize(2);
        DayView v = dayService.day(employeeRepository.findById(e.getId()).orElseThrow(), THU);
        assertThat(v.in()).isEqualTo(LocalTime.of(8, 20));
        assertThat(v.credit()).isEqualByComparingTo("1");
        assertThat(v.actualCredit()).isEqualByComparingTo("1");
        assertThat(v.lateMinutes()).isEqualTo(20);
    }

    @Test
    void absentMarkAndClear() throws Exception {
        putDay(LEAD, THU, entry(a, null, null, true))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("VANG"))
            .andExpect(jsonPath("$[0].credit").value(0))
            .andExpect(jsonPath("$[0].actualCredit").value(0));
        putDay(LEAD, THU, entry(a, null, null, false)).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("CHUA_NHAP"));
    }

    @Test
    void approvedLeaveAutoFilled() throws Exception {
        LocalDate tue = LocalDate.of(2026, 11, 17);
        leave(a, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_DUYET, tue, tue, DayPart.CA_NGAY, DayPart.CA_NGAY);
        // Đơn chờ hủy vẫn tính là nghỉ; đơn chờ duyệt thì chưa.
        leave(b, LeaveRequestType.OM, LeaveRequestStatus.CHO_HUY, tue, tue, DayPart.CA_NGAY, DayPart.CA_NGAY);
        leave(e, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.CHO_DUYET, tue, tue, DayPart.CA_NGAY, DayPart.CA_NGAY);
        DayView va = dayService.day(a, tue);
        assertThat(va.autoKind()).isEqualTo("PHEP_NAM");
        assertThat(va.actualCredit()).isEqualByComparingTo("1");
        assertThat(va.status()).isNull();
        DayView vb = dayService.day(b, tue);
        assertThat(vb.autoKind()).isEqualTo("OM");
        assertThat(vb.actualCredit()).isEqualByComparingTo("0");
        assertThat(dayService.day(e, tue).autoKind()).isNull();
        getDay(LEAD, tue).andExpect(jsonPath("$.groups[0].rows[?(@.employeeId == " + id(a) + ")].autoKind").value(hasItem("PHEP_NAM")));
        // Ô tự điền không gõ giờ.
        putDay(LEAD, tue, entry(a, "08:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendanceautofilled"));
    }

    @Test
    void halfDayLeavePlusWork() throws Exception {
        LocalDate mon = LocalDate.of(2026, 11, 16);
        leave(a, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_DUYET, mon, mon, DayPart.SANG, DayPart.SANG);
        // Nghỉ sáng: buổi làm bắt đầu 13:00.
        putDay(LEAD, mon, entry(a, "13:00", "17:00", false))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].credit").value(0.5))
            .andExpect(jsonPath("$[0].actualCredit").value(1))
            .andExpect(jsonPath("$[0].autoKind").value("PHEP_NAM"))
            .andExpect(jsonPath("$[0].leavePart").value("SANG"))
            .andExpect(jsonPath("$[0].lateMinutes").value(0));
        // Nghỉ chiều không lương ngày cuối của đơn nhiều ngày: chỉ phần đi làm.
        leave(b, LeaveRequestType.KHONG_LUONG, LeaveRequestStatus.DA_DUYET, LocalDate.of(2026, 11, 12), mon, DayPart.CA_NGAY, DayPart.SANG);
        putDay(LEAD, mon, entry(b, "13:00", "17:00", false)).andExpect(jsonPath("$[0].actualCredit").value(0.5));
        assertThat(dayService.day(b, LocalDate.of(2026, 11, 13)).autoKind()).isEqualTo("KHONG_LUONG");
    }

    @Test
    void tripAutoFilled() throws Exception {
        assign(b, WorkLocationType.CONG_TAC, null, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));
        DayView v = dayService.day(b, LocalDate.of(2026, 11, 11));
        assertThat(v.autoKind()).isEqualTo("CONG_TAC");
        assertThat(v.actualCredit()).isEqualByComparingTo("1");
        assertThat(dayService.day(b, LocalDate.of(2026, 11, 13)).autoKind()).isNull();
    }

    @Test
    void holidayCountsActual() throws Exception {
        LocalDate wed = LocalDate.of(2026, 11, 18);
        if (!holidayRepository.existsByDay(wed)) {
            Holiday h = new Holiday();
            h.setDay(wed);
            h.setName("Lễ thử chấm công");
            holidayRepository.saveAndFlush(h);
        }
        DayView v = dayService.day(a, wed);
        assertThat(v.autoKind()).isEqualTo("LE");
        assertThat(v.actualCredit()).isEqualByComparingTo("1");
        DayView sat = dayService.day(a, LocalDate.of(2026, 11, 14));
        assertThat(sat.autoKind()).isEqualTo("NGHI");
        assertThat(sat.actualCredit()).isEqualByComparingTo("0");
        // Trước ngày vào làm: ngoài hợp đồng, không tính.
        a.setJoinDate(LocalDate.of(2026, 11, 10));
        employeeRepository.saveAndFlush(a);
        assertThat(dayService.day(a, LocalDate.of(2026, 11, 9)).autoKind()).isEqualTo("NGOAI_HD");

        MonthView m = dayService.month(a, YearMonth.of(2026, 11));
        assertThat(m.standardDays()).isEqualByComparingTo("21");
        assertThat(m.holidayDays()).isEqualByComparingTo("1");
    }

    @Test
    void remoteFlagged() throws Exception {
        assign(a, WorkLocationType.TU_XA, null, LocalDate.of(2026, 11, 1), null);
        getDay(LEAD, THU)
            .andExpect(jsonPath("$.groups[?(@.group == 'VP')].rows[?(@.employeeId == " + id(a) + ")].remote").value(hasItem(true)))
            .andExpect(jsonPath("$.groups[?(@.group == 'VP')].rows[?(@.employeeId == " + id(b) + ")].remote").value(hasItem(false)));
        assertThat(dayService.day(a, THU).group()).isEqualTo("VP");
    }

    @Test
    void appPunchesUntouchedByPut() throws Exception {
        AttendancePunch app = new AttendancePunch();
        app.setEmployeeId(a.getId());
        app.setWorkDay(THU);
        app.setPunchTime(LocalTime.of(7, 55));
        app.setKind(AttendancePunchKind.VAO);
        app.setSource(AttendancePunchSource.APP);
        app.setEnteredBy("app");
        app.setEnteredAt(Instant.now());
        punchRepository.saveAndFlush(app);

        putDay(LEAD, THU, entry(a, "08:10", "17:00", false)).andExpect(status().isOk()).andExpect(jsonPath("$[0].in").value("07:55"));
        putDay(LEAD, THU, entry(a, "08:15", "17:05", false)).andExpect(status().isOk());
        List<AttendancePunch> all = punchRepository.findByEmployeeIdAndWorkDayOrderByPunchTimeAsc(a.getId(), THU);
        assertThat(all).extracting(AttendancePunch::getSource).containsExactly(
            AttendancePunchSource.APP,
            AttendancePunchSource.GO_TAY,
            AttendancePunchSource.GO_TAY
        );
        assertThat(all.get(0).getPunchTime()).isEqualTo(LocalTime.of(7, 55));
        assertThat(all.get(1).getPunchTime()).isEqualTo(LocalTime.of(8, 15));
        assertThat(all.get(1).getEnteredBy()).isEqualTo("lead-cc");
    }

    @Test
    void nonLeadForbidden() throws Exception {
        getDay(as("c-cc"), THU).andExpect(status().isForbidden());
        putDay(as("c-cc"), THU, entry(a, "08:00", "17:00", false)).andExpect(status().isForbidden());
        getDay(KE_TOAN, THU).andExpect(status().isForbidden());
        // Trưởng nhóm gõ cho người ngoài nhóm.
        putDay(LEAD, THU, entry(c, "08:00", "17:00", false)).andExpect(status().isForbidden());
        assertThat(goTay(c, THU)).isEmpty();
    }

    @Test
    void leadWithArchivedOrLeftProfileGrantsNothing() throws Exception {
        lead.setStatus(EmploymentStatus.DA_NGHI);
        employeeRepository.saveAndFlush(lead);
        assertThat(access.canEnter("lead-cc", a, THU)).isFalse();
        assertThat(access.enterableEmployees("lead-cc", THU)).isEmpty();
        getDay(LEAD, THU).andExpect(status().isForbidden());

        onsiteLead.setArchivedAt(Instant.parse("2026-11-01T00:00:00Z"));
        employeeRepository.saveAndFlush(onsiteLead);
        assertThat(access.canEnter("ol-cc", e, THU)).isFalse();
        putDay(ONSITE_LEAD, THU, entry(e, "08:00", "17:00", false)).andExpect(status().isForbidden());
    }

    @Test
    void accountFlagShowsMenu() throws Exception {
        mockMvc.perform(get("/api/account").with(LEAD)).andExpect(status().isOk()).andExpect(jsonPath("$.entersAttendance").value(true));
        mockMvc.perform(get("/api/account").with(ONSITE_LEAD)).andExpect(jsonPath("$.entersAttendance").value(true));
        mockMvc.perform(get("/api/account").with(as("c-cc"))).andExpect(jsonPath("$.entersAttendance").value(false));
    }

    @Test
    void outBeforeInRejected() throws Exception {
        putDay(LEAD, THU, entry(a, "17:00", "08:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancetimeorder"));
    }

    @Test
    void leadOfLeaverStillEntersDaysWorked() throws Exception {
        Employee lead2 = person("CC0012", PersonType.NHAN_VIEN, "lead2-cc", null);
        Employee leaver = person("CC0013", PersonType.NHAN_VIEN, null, lead2);
        leaver.setStatus(EmploymentStatus.DA_NGHI);
        leaver.setLastWorkingDay(LocalDate.of(2026, 11, 10));
        employeeRepository.saveAndFlush(leaver);
        RequestPostProcessor who = as("lead2-cc");
        assertThat(access.entersAnyone("lead2-cc")).isTrue();
        mockMvc.perform(get("/api/account").with(who)).andExpect(jsonPath("$.entersAttendance").value(true));
        getDay(who, LocalDate.of(2026, 11, 9)).andExpect(status().isOk()).andExpect(jsonPath(IDS, hasItem(id(leaver))));
        putDay(who, LocalDate.of(2026, 11, 9), entry(leaver, "08:00", "17:00", false)).andExpect(status().isOk());
        // Sau ngày làm việc cuối thì không còn ai.
        getDay(who, LocalDate.of(2026, 11, 11)).andExpect(status().isOk()).andExpect(jsonPath(IDS, not(hasItem(id(leaver)))));
        // Tháng của ngày làm việc cuối đã chốt: không còn gì để gõ. (Lần gõ ở trên đã tạo dòng tháng đang mở, R23.)
        AttendanceMonth m = monthRepository.findByMonth("2026-11").orElseGet(AttendanceMonth::new);
        m.setMonth("2026-11");
        m.setStatus(AttendanceMonthStatus.DA_CHOT);
        monthRepository.saveAndFlush(m);
        assertThat(access.entersAnyone("lead2-cc")).isFalse();
        getDay(who, LocalDate.of(2026, 11, 9)).andExpect(status().isForbidden());
    }

    @Test
    void leadOfInternsOnlyHasNothing() throws Exception {
        Employee lead3 = person("CC0014", PersonType.NHAN_VIEN, "lead3-cc", null);
        person("CC0015", PersonType.THUC_TAP, null, lead3);
        RequestPostProcessor who = as("lead3-cc");
        assertThat(access.entersAnyone("lead3-cc")).isFalse();
        mockMvc.perform(get("/api/account").with(who)).andExpect(jsonPath("$.entersAttendance").value(false));
        getDay(who, THU).andExpect(status().isForbidden());
    }

    @Test
    void onsiteGateFollowsAssignmentsNotSiteStatus() throws Exception {
        Employee lead4 = person("CC0017", PersonType.NHAN_VIEN, "lead4-cc", null);
        RequestPostProcessor who = as("lead4-cc");
        // Khách hàng đang hoạt động nhưng không có ai được phân công: không có gì để gõ.
        WorkSite empty = workSiteRepository.saveAndFlush(new WorkSite().name("KH trống CC").type(WorkLocationType.KHACH_HANG).active(true));
        onsiteLeadRepository.saveAndFlush(new OnsiteLead(empty.getId(), lead4.getId()));
        assertThat(access.entersAnyone("lead4-cc")).isFalse();
        // Chỉ thực tập sinh được phân công: vẫn không.
        Employee internOnsite = person("CC0018", PersonType.THUC_TAP, null, otherLead);
        assign(internOnsite, WorkLocationType.KHACH_HANG, empty, LocalDate.of(2026, 11, 1), null);
        assertThat(access.entersAnyone("lead4-cc")).isFalse();
        // Phân công chưa bắt đầu: không.
        Employee worker = person("CC0019", PersonType.NHAN_VIEN, null, otherLead);
        assign(worker, WorkLocationType.KHACH_HANG, empty, LocalDate.of(2026, 12, 1), null);
        assertThat(access.entersAnyone("lead4-cc")).isFalse();

        // Khách hàng đã ngừng hoạt động: phân công kết thúc trong tháng đã chốt thì không, trong tháng chưa chốt thì có.
        WorkSite inactive = workSiteRepository.saveAndFlush(new WorkSite().name("KH ngừng CC").type(WorkLocationType.KHACH_HANG).active(false));
        onsiteLeadRepository.saveAndFlush(new OnsiteLead(inactive.getId(), lead4.getId()));
        AttendanceMonth oct = new AttendanceMonth();
        oct.setMonth("2026-10");
        oct.setStatus(AttendanceMonthStatus.DA_CHOT);
        monthRepository.saveAndFlush(oct);
        assign(worker, WorkLocationType.KHACH_HANG, inactive, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15));
        assertThat(access.entersAnyone("lead4-cc")).isFalse();
        // Ngày làm việc cuối chặn ngày kết thúc phân công.
        Employee leaver = person("CC0020", PersonType.NHAN_VIEN, null, otherLead);
        leaver.setStatus(EmploymentStatus.DA_NGHI);
        leaver.setLastWorkingDay(LocalDate.of(2026, 10, 20));
        employeeRepository.saveAndFlush(leaver);
        assign(leaver, WorkLocationType.KHACH_HANG, inactive, LocalDate.of(2026, 10, 1), null);
        assertThat(access.entersAnyone("lead4-cc")).isFalse();

        assign(worker, WorkLocationType.KHACH_HANG, inactive, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        assertThat(access.entersAnyone("lead4-cc")).isTrue();
        mockMvc.perform(get("/api/account").with(who)).andExpect(jsonPath("$.entersAttendance").value(true));
        getDay(who, THU).andExpect(status().isOk()).andExpect(jsonPath(IDS, hasItem(id(worker))));
    }

    @Test
    void leadCannotProbeOtherIds() throws Exception {
        Employee archivedOther = person("CC0016", PersonType.NHAN_VIEN, null, otherLead);
        archivedOther.setArchivedAt(Instant.parse("2026-11-01T00:00:00Z"));
        employeeRepository.saveAndFlush(archivedOther);
        putDay(LEAD, THU, entry(archivedOther, "08:00", "17:00", false)).andExpect(status().isForbidden());
        // Người ngoài nhóm, tháng đã chốt hay ngày chưa tới: vẫn 403, không lộ thông tin.
        putDay(LEAD, TODAY.plusDays(3), entry(c, "08:00", "17:00", false)).andExpect(status().isForbidden());
        Map<String, Object> unknown = new LinkedHashMap<>(entry(a, "08:00", "17:00", false));
        unknown.put("employeeId", 999_999_999L);
        putDay(LEAD, THU, unknown).andExpect(status().isForbidden());
        // HR giữ lỗi cụ thể.
        putDay(HR, THU, entry(archivedOther, "08:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeearchived"));
        putDay(HR, THU, unknown).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.idnotfound"));
    }

    @Test
    void absentWithTimesRejected() throws Exception {
        putDay(LEAD, THU, entry(a, "08:00", null, true))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendanceabsentwithtimes"));
        putDay(LEAD, THU, entry(a, null, null, true)).andExpect(status().isOk()).andExpect(jsonPath("$[0].markedAbsent").value(true));
        assertThat(dayService.day(a, THU).markedAbsent()).isTrue();
        assertThat(dayService.day(b, THU).markedAbsent()).isFalse();
    }

    @Test
    void duplicateEntryRejected() throws Exception {
        putDay(LEAD, THU, entry(a, "08:00", "17:00", false), entry(a, "09:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendanceduplicateentry"));
        assertThat(goTay(a, THU)).isEmpty();
    }

    @Test
    void creditStatusesFromCore() throws Exception {
        DayView v = dayService.day(a, THU);
        assertThat(v.status()).isEqualTo(DayCredit.Status.CHUA_NHAP);
        assertThat(v.credit()).isNull();
        assertThat(v.actualCredit()).isEqualByComparingTo("0");
        // Hôm nay chưa gõ: chưa phải ô đỏ "chưa nhập".
        assertThat(dayService.day(a, TODAY).status()).isNull();
    }

    @Test
    void approvedPayrollPeriodLocksEditsEvenIfNotClosed() throws Exception {
        Employee lead2 = person("CC0021", PersonType.NHAN_VIEN, "lead2-cc", null);
        Employee leaver = person("CC0022", PersonType.NHAN_VIEN, null, lead2);
        leaver.setStatus(EmploymentStatus.DA_NGHI);
        leaver.setLastWorkingDay(LocalDate.of(2026, 11, 10));
        employeeRepository.saveAndFlush(leaver);
        assertThat(access.entersAnyone("lead2-cc")).isTrue();

        PayrollPeriod period = new PayrollPeriod();
        period.setPeriod("2026-11");
        period.setStatus(PayrollPeriodStatus.DA_DUYET);
        period.setApprovedBy("qt-cc");
        period.setApprovedAt(Instant.parse("2026-11-19T03:00:00Z"));
        periodRepository.saveAndFlush(period);

        putDay(LEAD, THU, entry(a, "08:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancepayrollapproved"));
        putDay(HR, THU, entry(c, "08:00", "17:00", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancepayrollapproved"));
        assertThat(goTay(a, THU)).isEmpty();
        assertThat(goTay(c, THU)).isEmpty();
        // Tháng có kỳ lương đã duyệt tính như tháng đã chốt: trưởng nhóm của người nghỉ trong tháng đó không còn gì để gõ.
        assertThat(access.entersAnyone("lead2-cc")).isFalse();
        // Tháng khác vẫn gõ được.
        putDay(LEAD, LocalDate.of(2026, 10, 30), entry(a, "08:00", "17:00", false)).andExpect(status().isOk());
    }

    @Test
    void saveLocksOrCreatesMonthRow() throws Exception {
        assertThat(monthRepository.findByMonth("2026-11")).isEmpty();
        putDay(LEAD, THU, entry(a, "08:00", "17:00", false)).andExpect(status().isOk());
        assertThat(monthRepository.findByMonth("2026-11")).get().extracting(AttendanceMonth::getStatus).isEqualTo(AttendanceMonthStatus.DANG_MO);
    }
}
