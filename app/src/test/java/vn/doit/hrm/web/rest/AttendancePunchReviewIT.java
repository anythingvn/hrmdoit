package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.attendance.DayCredit;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.OnsiteLead;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchMethod;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.AttendancePunchStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.OnsiteLeadRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.attendance.AttendanceDayService;
import vn.doit.hrm.service.attendance.AttendanceDayService.DayView;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Tính công theo lần chấm được tính, duyệt / từ chối lần chấm chờ duyệt, nguồn từng lần và ai thấy tọa độ, IP. Hôm nay: thứ Tư 02/12/2026,
 * 10:00 (Asia/Ho_Chi_Minh); các ngày thử thuộc tháng 11 (chưa chốt). Giờ công ty 08:00–17:00.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(AttendancePunchReviewIT.FixedClockConfig.class)
class AttendancePunchReviewIT {

    static final Instant NOW = Instant.parse("2026-12-02T03:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final RequestPostProcessor HR = user("hr-pr").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor BGD = user("bgd-pr").authorities(() -> "ROLE_BGD");
    private static final RequestPostProcessor KE_TOAN = user("kt-pr").authorities(() -> "ROLE_KE_TOAN");
    private static final RequestPostProcessor LEAD = as("lead-pr");
    private static final RequestPostProcessor ONSITE_LEAD = as("ol-pr");
    private static final RequestPostProcessor HEAD = as("head-pr");
    private static final RequestPostProcessor A = as("a-pr");

    private static final LocalDate ONSITE_DAY = LocalDate.of(2026, 11, 17); // a onsite ở K 16–18/11
    private static final LocalDate THU = LocalDate.of(2026, 11, 19); // a ở văn phòng
    private static final LocalDate FRI = LocalDate.of(2026, 11, 20);

    @Autowired
    private MockMvc mockMvc;

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
    private OnsiteLeadRepository onsiteLeadRepository;

    @Autowired
    private AttendancePunchRepository punchRepository;

    @Autowired
    private AttendanceMonthRepository monthRepository;

    @Autowired
    private WorkCalendarService calendarService;

    @Autowired
    private AttendanceDayService dayService;

    private WorkSite k;
    private Employee a; // trưởng nhóm lead, phòng D, onsite ở K 16–18/11
    private Employee hrPerson; // HR có hồ sơ nhân viên

    private static RequestPostProcessor as(String login) {
        return user(login).authorities(() -> "ROLE_NHAN_VIEN");
    }

    private Employee person(String code, String login, Employee manager, Department dept) {
        Employee p = LeaveFixtures.employee(employeeRepository, code, PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        if (login != null) {
            p.setUser(LeaveFixtures.user(userRepository, login));
        }
        p.setManager(manager);
        p.setDepartment(dept);
        return employeeRepository.saveAndFlush(p);
    }

    @BeforeEach
    void init() {
        calendarService.setWorkingDays(
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        );
        Department dept = LeaveFixtures.department(departmentRepository, "Phòng duyệt chấm công");
        k = workSiteRepository.saveAndFlush(new WorkSite().name("Khách hàng K").type(WorkLocationType.KHACH_HANG).active(true));
        Employee head = person("PR0001", "head-pr", null, dept);
        dept.setHead(head);
        departmentRepository.saveAndFlush(dept);
        Employee lead = person("PR0002", "lead-pr", null, null);
        Employee onsiteLead = person("PR0003", "ol-pr", null, null);
        a = person("PR0004", "a-pr", lead, dept);
        hrPerson = person("PR0005", "hr-pr", lead, null);
        onsiteLeadRepository.saveAndFlush(new OnsiteLead(k.getId(), onsiteLead.getId()));
        WorkAssignment w = new WorkAssignment()
            .type(WorkLocationType.KHACH_HANG)
            .fromDate(LocalDate.of(2026, 11, 16))
            .toDate(LocalDate.of(2026, 11, 18))
            .employee(a);
        w.setSite(k);
        assignmentRepository.saveAndFlush(w);
    }

    // ------------------------------------------------------------------ dữ liệu

    private AttendancePunch app(Employee who, LocalDate day, AttendancePunchKind kind, String time, AttendancePunchStatus st) {
        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(who.getId());
        p.setWorkDay(day);
        p.setPunchTime(LocalTime.parse(time));
        p.setKind(kind);
        p.setSource(AttendancePunchSource.APP);
        p.setStatus(st);
        p.setMethod(AttendancePunchMethod.GPS);
        p.setSiteId(k.getId());
        p.setDistanceM(150);
        p.setLatitude(new BigDecimal("21.028511"));
        p.setLongitude(new BigDecimal("105.804817"));
        p.setAccuracyM(new BigDecimal("12.0"));
        p.setClientIp("113.190.1.10");
        p.setReviewReason(st == AttendancePunchStatus.CHO_DUYET ? "Sát ranh giới" : null);
        p.setEnteredBy("a-pr");
        p.setEnteredAt(Instant.parse("2026-11-19T01:00:00Z"));
        return punchRepository.saveAndFlush(p);
    }

    private void goTay(Employee who, LocalDate day, AttendancePunchKind kind, String time) {
        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(who.getId());
        p.setWorkDay(day);
        p.setPunchTime(LocalTime.parse(time));
        p.setKind(kind);
        p.setSource(AttendancePunchSource.GO_TAY);
        p.setEnteredBy("lead-pr");
        p.setEnteredAt(Instant.parse("2026-11-30T10:00:00Z"));
        punchRepository.saveAndFlush(p);
    }

    private DayView day(Employee who, LocalDate d) {
        return dayService.day(employeeRepository.findById(who.getId()).orElseThrow(), d);
    }

    private ResultActions approve(RequestPostProcessor who, AttendancePunch p) throws Exception {
        return mockMvc.perform(post("/api/attendance/punches/" + p.getId() + "/approve").with(who));
    }

    private ResultActions reject(RequestPostProcessor who, AttendancePunch p, String note) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("note", note);
        return mockMvc.perform(
            post("/api/attendance/punches/" + p.getId() + "/reject")
                .with(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body))
        );
    }

    private AttendancePunch reload(AttendancePunch p) {
        return punchRepository.findById(p.getId()).orElseThrow();
    }

    private List<AttendancePunch> punches(Employee who, LocalDate d) {
        return punchRepository.findByEmployeeIdAndWorkDayOrderByPunchTimeAsc(who.getId(), d);
    }

    /** Lần chấm {@code punchId} trong ô ngày {@code d} của {@code who} trên bảng tháng 11 mà {@code viewer} xem. */
    private JsonNode boardPunch(RequestPostProcessor viewer, Employee who, LocalDate d, Long punchId) throws Exception {
        String json = mockMvc
            .perform(get("/api/attendance/month").param("month", "2026-11").with(viewer))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return findPunch(om.readTree(json).get("rows"), who, d, punchId);
    }

    private JsonNode myPunch(Long punchId) throws Exception {
        String json = mockMvc
            .perform(get("/api/attendance/my").param("month", "2026-11").with(A))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return findPunch(om.readTree(json).get("rows"), a, THU, punchId);
    }

    private static JsonNode findPunch(JsonNode rows, Employee who, LocalDate d, Long punchId) {
        for (JsonNode row : rows) {
            if (row.get("employee").get("id").longValue() != who.getId()) continue;
            for (JsonNode day : row.get("days")) {
                if (!d.toString().equals(day.get("day").stringValue())) continue;
                for (JsonNode p : day.get("punches")) {
                    if (p.get("id").longValue() == punchId) return p;
                }
            }
        }
        throw new AssertionError("Không thấy lần chấm " + punchId);
    }

    // ------------------------------------------------------------------ tính công

    @Test
    void pendingAndRejectedNotCounted() throws Exception {
        AttendancePunch in = app(a, THU, AttendancePunchKind.VAO, "08:20", AttendancePunchStatus.CHO_DUYET);
        app(a, THU, AttendancePunchKind.RA, "17:05", AttendancePunchStatus.CHAP_NHAN);
        DayView before = day(a, THU);
        assertThat(before.status()).isEqualTo(DayCredit.Status.THIEU_GIO);
        assertThat(before.in()).isNull();
        assertThat(before.out()).isEqualTo(LocalTime.of(17, 5));
        assertThat(before.pendingCount()).isEqualTo(1);
        assertThat(before.punches()).hasSize(2);

        approve(LEAD, in).andExpect(status().isOk()).andExpect(jsonPath("$.pendingCount").value(0)).andExpect(jsonPath("$.in").value("08:20"));
        DayView after = day(a, THU);
        assertThat(after.status()).isEqualTo(DayCredit.Status.DI_LAM);
        assertThat(after.credit()).isEqualByComparingTo("1");
        assertThat(after.lateMinutes()).isEqualTo(20);
        AttendancePunch saved = reload(in);
        assertThat(saved.getStatus()).isEqualTo(AttendancePunchStatus.DA_DUYET);
        assertThat(saved.getDecidedBy()).isEqualTo("lead-pr");
        assertThat(saved.getDecidedAt()).isEqualTo(NOW);
        assertThat(saved.getDecisionNote()).isNull();

        // Lần bị từ chối không tính.
        app(a, FRI, AttendancePunchKind.VAO, "08:00", AttendancePunchStatus.TU_CHOI);
        app(a, FRI, AttendancePunchKind.RA, "17:00", AttendancePunchStatus.CHAP_NHAN);
        DayView fri = day(a, FRI);
        assertThat(fri.status()).isEqualTo(DayCredit.Status.THIEU_GIO);
        assertThat(fri.in()).isNull();
        assertThat(fri.pendingCount()).isZero();
    }

    @Test
    void earliestAmongCounted() throws Exception {
        goTay(a, FRI, AttendancePunchKind.VAO, "08:10");
        goTay(a, FRI, AttendancePunchKind.RA, "17:00");
        app(a, FRI, AttendancePunchKind.VAO, "07:58", AttendancePunchStatus.CHAP_NHAN);
        DayView v = day(a, FRI);
        assertThat(v.in()).isEqualTo(LocalTime.of(7, 58));
        assertThat(v.manualIn()).isEqualTo(LocalTime.of(8, 10));
        assertThat(v.manualOut()).isEqualTo(LocalTime.of(17, 0));
        app(a, FRI, AttendancePunchKind.VAO, "07:50", AttendancePunchStatus.TU_CHOI);
        assertThat(day(a, FRI).in()).isEqualTo(LocalTime.of(7, 58));
        assertThat(day(a, FRI).punches()).hasSize(4);
    }

    @Test
    void manualSaveKeepsSelfPunches() throws Exception {
        AttendancePunch self = app(a, THU, AttendancePunchKind.VAO, "08:00", AttendancePunchStatus.CHAP_NHAN);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("employeeId", a.getId());
        entry.put("in", null);
        entry.put("out", "17:30");
        entry.put("absent", false);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", THU.toString());
        body.put("entries", List.of(entry));
        for (int i = 0; i < 2; i++) {
            mockMvc
                .perform(put("/api/attendance/day").with(LEAD).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].in").value("08:00"))
                .andExpect(jsonPath("$[0].out").value("17:30"))
                .andExpect(jsonPath("$[0].manualIn").doesNotExist())
                .andExpect(jsonPath("$[0].manualOut").value("17:30"))
                .andExpect(jsonPath("$[0].punches.length()").value(2))
                .andExpect(jsonPath("$[0].punches[0].source").value("APP"))
                .andExpect(jsonPath("$[0].punches[1].source").value("GO_TAY"));
        }
        List<AttendancePunch> all = punches(a, THU);
        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(p -> p.getSource() == AttendancePunchSource.APP).singleElement().satisfies(p -> {
            assertThat(p.getId()).isEqualTo(self.getId());
            assertThat(p.getPunchTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(p.getStatus()).isEqualTo(AttendancePunchStatus.CHAP_NHAN);
        });
        assertThat(all).filteredOn(p -> p.getSource() == AttendancePunchSource.GO_TAY).singleElement().satisfies(p -> {
            assertThat(p.getKind()).isEqualTo(AttendancePunchKind.RA);
            assertThat(p.getPunchTime()).isEqualTo(LocalTime.of(17, 30));
        });
        DayView v = day(a, THU);
        assertThat(v.manualIn()).isNull();
        assertThat(v.in()).isEqualTo(LocalTime.of(8, 0));
        assertThat(v.credit()).isEqualByComparingTo("1");
    }

    // ------------------------------------------------------------------ duyệt

    @Test
    void noSelfApproval() throws Exception {
        AttendancePunch p = app(hrPerson, THU, AttendancePunchKind.VAO, "08:00", AttendancePunchStatus.CHO_DUYET);
        approve(HR, p).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendanceselfreview"));
        reject(HR, p, "Không hợp lệ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendanceselfreview"));
        assertThat(reload(p).getStatus()).isEqualTo(AttendancePunchStatus.CHO_DUYET);
        assertThat(reload(p).getDecidedBy()).isNull();
        // Người duyệt khác (trưởng nhóm) thì được.
        approve(LEAD, p).andExpect(status().isOk());
    }

    @Test
    void onsiteLeadApprovesOnlyOnsiteDays() throws Exception {
        AttendancePunch onsite = app(a, ONSITE_DAY, AttendancePunchKind.VAO, "08:00", AttendancePunchStatus.CHO_DUYET);
        AttendancePunch office = app(a, THU, AttendancePunchKind.VAO, "08:00", AttendancePunchStatus.CHO_DUYET);
        approve(ONSITE_LEAD, office).andExpect(status().isForbidden());
        reject(ONSITE_LEAD, office, "Sai chỗ").andExpect(status().isForbidden());
        assertThat(reload(office).getStatus()).isEqualTo(AttendancePunchStatus.CHO_DUYET);
        approve(ONSITE_LEAD, onsite).andExpect(status().isOk());
        assertThat(reload(onsite).getStatus()).isEqualTo(AttendancePunchStatus.DA_DUYET);
        assertThat(reload(onsite).getDecidedBy()).isEqualTo("ol-pr");
        // Người ngoài phạm vi, id không tồn tại: 403 như nhau.
        approve(HEAD, office).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/attendance/punches/999999999/approve").with(LEAD)).andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/attendance/punches/999999999/approve").with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.idnotfound"));
    }

    @Test
    void rejectNeedsNote() throws Exception {
        AttendancePunch p = app(a, THU, AttendancePunchKind.VAO, "08:00", AttendancePunchStatus.CHO_DUYET);
        reject(LEAD, p, "  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancerejectnoterequired"));
        reject(LEAD, p, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancerejectnoterequired"));
        mockMvc
            .perform(post("/api/attendance/punches/" + p.getId() + "/reject").with(LEAD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancerejectnoterequired"));
        assertThat(reload(p).getStatus()).isEqualTo(AttendancePunchStatus.CHO_DUYET);

        reject(LEAD, p, " Không ở văn phòng ").andExpect(status().isOk()).andExpect(jsonPath("$.pendingCount").value(0));
        AttendancePunch saved = reload(p);
        assertThat(saved.getStatus()).isEqualTo(AttendancePunchStatus.TU_CHOI);
        assertThat(saved.getDecisionNote()).isEqualTo("Không ở văn phòng");
        assertThat(saved.getDecidedBy()).isEqualTo("lead-pr");
        assertThat(saved.getDecidedAt()).isEqualTo(NOW);
        assertThat(saved.getLatitude()).isNotNull(); // giữ nguyên dữ liệu

        // Đã quyết thì không duyệt / từ chối lại; lần không phải chờ duyệt cũng vậy.
        approve(LEAD, p).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancepunchnotpending"));
        AttendancePunch accepted = app(a, THU, AttendancePunchKind.RA, "17:00", AttendancePunchStatus.CHAP_NHAN);
        reject(LEAD, accepted, "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancepunchnotpending"));
    }

    @Test
    void closedMonthNoReview() throws Exception {
        LocalDate oct = LocalDate.of(2026, 10, 29);
        AttendancePunch p = app(a, oct, AttendancePunchKind.VAO, "08:00", AttendancePunchStatus.CHO_DUYET);
        AttendanceMonth m = new AttendanceMonth();
        m.setMonth("2026-10");
        m.setStatus(AttendanceMonthStatus.DA_CHOT);
        monthRepository.saveAndFlush(m);
        approve(LEAD, p).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancemonthclosed"));
        reject(HR, p, "Sai").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancemonthclosed"));
        assertThat(reload(p).getStatus()).isEqualTo(AttendancePunchStatus.CHO_DUYET);
    }

    // ------------------------------------------------------------------ chốt

    @Test
    void closeWarnsPending() throws Exception {
        goTay(a, THU, AttendancePunchKind.VAO, "08:00");
        goTay(a, THU, AttendancePunchKind.RA, "17:00");
        app(a, THU, AttendancePunchKind.VAO, "07:55", AttendancePunchStatus.CHO_DUYET);
        // Ngày đủ giờ gõ tay, nhưng còn lần chờ duyệt: vẫn có trong danh sách cảnh báo.
        assertThat(day(a, THU).status()).isEqualTo(DayCredit.Status.DI_LAM);
        mockMvc
            .perform(post("/api/attendance/month/2026-11/close").param("force", "false").with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendanceunresolved"))
            .andExpect(jsonPath("$.unresolved[?(@.code == 'PR0004')].dates[*]").value(hasItem("2026-11-19")));
    }

    // ------------------------------------------------------------------ riêng tư

    @Test
    void locationVisibility() throws Exception {
        AttendancePunch p = app(a, THU, AttendancePunchKind.VAO, "08:00", AttendancePunchStatus.CHO_DUYET);

        for (RequestPostProcessor hidden : new RequestPostProcessor[] { HEAD, BGD }) {
            JsonNode n = boardPunch(hidden, a, THU, p.getId());
            assertThat(n.get("latitude").isNull()).isTrue();
            assertThat(n.get("longitude").isNull()).isTrue();
            assertThat(n.get("accuracyM").isNull()).isTrue();
            assertThat(n.get("clientIp").isNull()).isTrue();
            assertThat(n.get("source").stringValue()).isEqualTo("APP");
            assertThat(n.get("status").stringValue()).isEqualTo("CHO_DUYET");
            assertThat(n.get("method").stringValue()).isEqualTo("GPS");
            assertThat(n.get("siteName").stringValue()).isEqualTo("Khách hàng K");
            assertThat(n.get("distanceM").intValue()).isEqualTo(150);
            assertThat(n.get("reviewReason").stringValue()).isEqualTo("Sát ranh giới");
        }
        for (RequestPostProcessor shown : new RequestPostProcessor[] { LEAD, HR }) {
            JsonNode n = boardPunch(shown, a, THU, p.getId());
            assertThat(n.get("latitude").decimalValue()).isEqualByComparingTo("21.028511");
            assertThat(n.get("clientIp").stringValue()).isEqualTo("113.190.1.10");
        }
        JsonNode mine = myPunch(p.getId());
        assertThat(mine.get("latitude").decimalValue()).isEqualByComparingTo("21.028511");
        assertThat(mine.get("clientIp").stringValue()).isEqualTo("113.190.1.10");
        mockMvc
            .perform(get("/api/attendance/day").param("date", THU.toString()).with(LEAD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[*].rows[*].punches[*].clientIp").value(hasItem("113.190.1.10")));
        // Không lộ qua chuỗi log (toString của kết quả).
        assertThat(day(a, THU).toString()).doesNotContain("113.190.1.10").doesNotContain("21.028511");

        // Kế toán chỉ xem tháng đã chốt.
        mockMvc.perform(post("/api/attendance/month/2026-11/close").param("force", "true").with(HR)).andExpect(status().isOk());
        JsonNode kt = boardPunch(KE_TOAN, a, THU, p.getId());
        assertThat(kt.get("latitude").isNull()).isTrue();
        assertThat(kt.get("clientIp").isNull()).isTrue();
    }
}
