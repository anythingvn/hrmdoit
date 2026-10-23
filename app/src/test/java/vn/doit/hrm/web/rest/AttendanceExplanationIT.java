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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.OnsiteLead;
import vn.doit.hrm.domain.User;
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
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.OnsiteLeadRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.attendance.AttendanceDayService;
import vn.doit.hrm.service.attendance.AttendanceDayService.DayView;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.reminder.ReminderMailer;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Giải trình chấm công (spec 3.7): gửi, rút, duyệt sinh lần chấm {@code GIAI_TRINH}, từ chối; danh sách chờ duyệt theo phạm vi người
 * duyệt; email cho người duyệt và nhân viên. Hôm nay: thứ Sáu 23/10/2026, 10:00 (Asia/Ho_Chi_Minh). Giờ công ty 08:00–17:00.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(AttendanceExplanationIT.FixedClockConfig.class)
class AttendanceExplanationIT {

    static final Instant NOW = Instant.parse("2026-10-23T03:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final LocalDate D13 = LocalDate.of(2026, 10, 13);
    private static final LocalDate D14 = LocalDate.of(2026, 10, 14);
    private static final LocalDate D15 = LocalDate.of(2026, 10, 15);
    private static final LocalDate D16 = LocalDate.of(2026, 10, 16);
    private static final LocalDate D20 = LocalDate.of(2026, 10, 20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

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

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ReminderMailer mailer;

    private String sfx;
    private WorkSite k;
    private Employee lead; // trưởng nhóm của a, z, hrPerson
    private Employee lead2; // trưởng nhóm của b
    private Employee onsiteLead; // trưởng nhóm onsite ở K
    private Employee a;
    private Employee b;
    private Employee c; // không có trưởng nhóm, onsite ở K 12–13/10
    private Employee d; // không có trưởng nhóm, không onsite
    private Employee z; // nhóm lead, đã lưu trữ
    private Employee hrPerson; // HR có hồ sơ nhân viên, trưởng nhóm là lead
    private final List<Employee> created = new ArrayList<>();

    private RequestPostProcessor LEAD;
    private RequestPostProcessor LEAD2;
    private RequestPostProcessor ONSITE_LEAD;
    private RequestPostProcessor A;
    private RequestPostProcessor B;
    private RequestPostProcessor C;
    private RequestPostProcessor D;
    private RequestPostProcessor HR;

    private static RequestPostProcessor as(String login) {
        return user(login).authorities(() -> "ROLE_NHAN_VIEN");
    }

    private String mail(String who) {
        return who + sfx + "@doit.test";
    }

    private Employee person(String code, String login, Employee manager) {
        Employee p = LeaveFixtures.employee(employeeRepository, code + sfx, PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        User u = LeaveFixtures.user(userRepository, login + sfx);
        u.setEmail(mail(login));
        p.setUser(userRepository.saveAndFlush(u));
        p.setManager(manager);
        p = employeeRepository.saveAndFlush(p);
        created.add(p);
        return p;
    }

    @BeforeEach
    void init() {
        when(mailer.configured()).thenReturn(true);
        created.clear();
        sfx = "-" + UUID.randomUUID().toString().substring(0, 6);
        calendarService.setWorkingDays(
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        );
        k = workSiteRepository.saveAndFlush(new WorkSite().name("Khách hàng K" + sfx).type(WorkLocationType.KHACH_HANG).active(true));
        lead = person("EX1", "lead-ex", null);
        lead2 = person("EX2", "lead2-ex", null);
        onsiteLead = person("EX3", "ol-ex", null);
        a = person("EX4", "a-ex", lead);
        b = person("EX5", "b-ex", lead2);
        c = person("EX6", "c-ex", null);
        d = person("EX7", "d-ex", null);
        z = person("EX8", "z-ex", lead);
        hrPerson = person("EX9", "hr-ex", lead);
        User hrUser = userRepository.findOneWithAuthoritiesByLogin("hr-ex" + sfx).orElseThrow();
        hrUser.getAuthorities().add(authorityRepository.findById(AuthoritiesConstants.HR).orElseThrow());
        userRepository.saveAndFlush(hrUser);
        onsiteLeadRepository.saveAndFlush(new OnsiteLead(k.getId(), onsiteLead.getId()));
        WorkAssignment w = new WorkAssignment().type(WorkLocationType.KHACH_HANG).fromDate(LocalDate.of(2026, 10, 12)).toDate(D13).employee(c);
        w.setSite(k);
        assignmentRepository.saveAndFlush(w);

        LEAD = as("lead-ex" + sfx);
        LEAD2 = as("lead2-ex" + sfx);
        ONSITE_LEAD = as("ol-ex" + sfx);
        A = as("a-ex" + sfx);
        B = as("b-ex" + sfx);
        C = as("c-ex" + sfx);
        D = as("d-ex" + sfx);
        HR = user("hr-ex" + sfx).authorities(() -> "ROLE_HR");
    }

    // ------------------------------------------------------------------ hỗ trợ

    private AttendancePunch app(Employee who, LocalDate day, AttendancePunchKind kind, String time, AttendancePunchStatus st) {
        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(who.getId());
        p.setWorkDay(day);
        p.setPunchTime(LocalTime.parse(time));
        p.setKind(kind);
        p.setSource(AttendancePunchSource.APP);
        p.setStatus(st);
        p.setMethod(AttendancePunchMethod.GPS);
        p.setDistanceM(250);
        p.setLatitude(new BigDecimal("21.028511"));
        p.setLongitude(new BigDecimal("105.804817"));
        p.setAccuracyM(new BigDecimal("12.0"));
        p.setClientIp("113.190.1.10");
        p.setReviewReason(st == AttendancePunchStatus.CHO_DUYET ? "Sát ranh giới" : null);
        p.setEnteredBy("x");
        p.setEnteredAt(day.atTime(1, 0).toInstant(java.time.ZoneOffset.UTC));
        return punchRepository.saveAndFlush(p);
    }

    private ResultActions submit(RequestPostProcessor who, LocalDate day, String in, String out, String reason) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workDay", day == null ? null : day.toString());
        body.put("proposedIn", in);
        body.put("proposedOut", out);
        body.put("reason", reason);
        return mockMvc.perform(
            post("/api/attendance/explanations").with(who).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))
        );
    }

    private long submitOk(RequestPostProcessor who, LocalDate day, String in, String out, String reason) throws Exception {
        String json = submit(who, day, in, out, reason)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DANG_CHO"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return om.readTree(json).get("id").longValue();
    }

    private ResultActions approve(RequestPostProcessor who, long id) throws Exception {
        return mockMvc.perform(post("/api/attendance/explanations/" + id + "/approve").with(who));
    }

    private ResultActions reject(RequestPostProcessor who, long id, String note) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("note", note);
        return mockMvc.perform(
            post("/api/attendance/explanations/" + id + "/reject")
                .with(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body))
        );
    }

    private ResultActions withdraw(RequestPostProcessor who, long id) throws Exception {
        return mockMvc.perform(post("/api/attendance/explanations/" + id + "/withdraw").with(who));
    }

    private List<AttendancePunch> punches(Employee who, LocalDate day) {
        return punchRepository.findByEmployeeIdAndWorkDayOrderByPunchTimeAsc(who.getId(), day);
    }

    private DayView day(Employee who, LocalDate day) {
        return dayService.day(employeeRepository.findById(who.getId()).orElseThrow(), day);
    }

    private String explanationStatus(long id) {
        return jdbc.queryForObject("select status from attendance_explanation where id = ?", String.class, id);
    }

    private void closeOctober() {
        AttendanceMonth m = monthRepository.findByMonth("2026-10").orElseGet(AttendanceMonth::new);
        m.setMonth("2026-10");
        m.setStatus(AttendanceMonthStatus.DA_CHOT);
        monthRepository.saveAndFlush(m);
    }

    /** Các mục chờ duyệt mà {@code who} thấy, dạng "LOẠI:id"; kèm khẳng định không lộ tọa độ hay IP. */
    private Set<String> pending(RequestPostProcessor who) throws Exception {
        String json = mockMvc
            .perform(get("/api/attendance/pending").with(who))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(json).doesNotContain("113.190.1.10").doesNotContain("21.028511").doesNotContain("latitude").doesNotContain("clientIp");
        Set<String> out = new HashSet<>();
        for (JsonNode n : om.readTree(json)) {
            out.add(n.get("type").stringValue() + ":" + n.get("id").longValue());
        }
        return out;
    }

    // ------------------------------------------------------------------ gửi và duyệt

    @Test
    void submitAndApproveCreatesPunches() throws Exception {
        app(a, D13, AttendancePunchKind.VAO, "08:00", AttendancePunchStatus.CHAP_NHAN);
        long id = submitOk(A, D13, null, "17:12", " Quên chấm ra ");
        assertThat(day(a, D13).out()).isNull(); // đang chờ: chưa tính

        approve(LEAD, id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_DUYET"))
            .andExpect(jsonPath("$.decidedBy").value("lead-ex" + sfx))
            .andExpect(jsonPath("$.proposedOut").value("17:12"))
            .andExpect(jsonPath("$.reason").value("Quên chấm ra"));
        List<AttendancePunch> made = punches(a, D13).stream().filter(p -> p.getSource() == AttendancePunchSource.GIAI_TRINH).toList();
        assertThat(made).singleElement().satisfies(p -> {
            assertThat(p.getKind()).isEqualTo(AttendancePunchKind.RA);
            assertThat(p.getPunchTime()).isEqualTo(LocalTime.of(17, 12));
            assertThat(p.getExplanationId()).isEqualTo(id);
            assertThat(p.getLatitude()).isNull();
            assertThat(p.getClientIp()).isNull();
        });
        DayView v = day(a, D13);
        assertThat(v.credit()).isEqualByComparingTo("1");
        assertThat(v.out()).isEqualTo(LocalTime.of(17, 12));

        mockMvc
            .perform(get("/api/attendance/explanations/mine").param("month", "2026-10").with(A))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].workDay").value("2026-10-13"))
            .andExpect(jsonPath("$[0].status").value("DA_DUYET"));
        mockMvc
            .perform(get("/api/attendance/explanations/mine").param("month", "2026-09").with(A))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void explanationOutOnlyKeepsPendingIn() throws Exception {
        AttendancePunch in = app(a, D14, AttendancePunchKind.VAO, "08:05", AttendancePunchStatus.CHO_DUYET);
        long id = submitOk(A, D14, null, "17:30", "Quên chấm ra");
        approve(LEAD, id).andExpect(status().isOk());
        List<AttendancePunch> all = punches(a, D14);
        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(p -> p.getSource() == AttendancePunchSource.GIAI_TRINH).singleElement().satisfies(p ->
            assertThat(p.getKind()).isEqualTo(AttendancePunchKind.RA)
        );
        assertThat(punchRepository.findById(in.getId()).orElseThrow().getStatus()).isEqualTo(AttendancePunchStatus.CHO_DUYET);
        DayView v = day(a, D14);
        assertThat(v.in()).isNull();
        assertThat(v.out()).isEqualTo(LocalTime.of(17, 30));
        assertThat(v.pendingCount()).isEqualTo(1);
        assertThat(pending(LEAD)).contains("PUNCH:" + in.getId()).doesNotContain("EXPLANATION:" + id);
    }

    @Test
    void onePendingPerDay() throws Exception {
        long first = submitOk(A, D15, "08:00", null, "Quên chấm vào");
        submit(A, D15, null, "17:00", "Quên chấm ra")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.explanationpending"));
        withdraw(A, first).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_RUT"));
        submitOk(A, D15, "08:00", "17:00", "Quên chấm cả ngày");
        // Ngày khác vẫn gửi được.
        submitOk(A, D16, "08:00", null, "Quên chấm vào");
    }

    @Test
    void futureOrWrongDayRejected() throws Exception {
        LocalDate today = LocalDate.of(2026, 10, 23);
        submit(A, today, null, "11:00", "Về sớm").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationfuture"));
        submit(A, today.plusDays(1), "08:00", null, "Ngày mai")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.explanationfuture"));
        submit(A, D13, null, null, "Không giờ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationtimerequired"));
        submit(A, D13, "08:00", null, "  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationreasonrequired"));
        submit(A, D13, "08:00", null, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationreasonrequired"));
        submit(A, D13, "08:00", null, "x".repeat(501))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.explanationreasontoolong"));
        submit(A, D13, "17:00", "08:00", "Ngược").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationtimeorder"));
        submit(A, null, "08:00", null, "Thiếu ngày").andExpect(status().isBadRequest());
        // Trước khi vào làm: không áp dụng.
        submit(A, LocalDate.of(2023, 12, 1), "08:00", null, "Trước ngày vào làm")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancenotapplicable"));
        // Hôm nay, giờ đã qua: được.
        submitOk(A, today, "08:10", null, "Quên chấm vào");
        assertThat(jdbc.queryForObject("select count(*) from attendance_explanation where employee_id = ?", Integer.class, a.getId())).isEqualTo(1);
    }

    @Test
    void closedMonthRejected() throws Exception {
        long id = submitOk(A, D16, "08:00", null, "Quên chấm vào");
        closeOctober();
        submit(A, D20, "08:00", null, "Quên chấm vào")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancemonthclosed"));
        approve(LEAD, id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancemonthclosed"));
        reject(HR, id, "Sai").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancemonthclosed"));
        assertThat(explanationStatus(id)).isEqualTo("DANG_CHO");
        assertThat(punches(a, D16)).isEmpty();
    }

    @Test
    void noSelfApproval() throws Exception {
        long id = submitOk(HR, D13, "08:00", null, "Quên chấm vào");
        approve(HR, id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationselfreview"));
        reject(HR, id, "Không").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationselfreview"));
        assertThat(explanationStatus(id)).isEqualTo("DANG_CHO");
        // Ngoài phạm vi: 403; id không tồn tại: 403 cho trưởng nhóm, 400 idnotfound cho HR.
        approve(LEAD2, id).andExpect(status().isForbidden());
        approve(ONSITE_LEAD, id).andExpect(status().isForbidden());
        approve(LEAD, 987654321L).andExpect(status().isForbidden());
        approve(HR, 987654321L).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.idnotfound"));
        approve(LEAD, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_DUYET"));
    }

    @Test
    void onsiteLeadApprovesOnsiteDayOnly() throws Exception {
        long onsite = submitOk(C, D13, "08:00", null, "Quên chấm vào");
        long office = submitOk(C, D20, "08:00", null, "Quên chấm vào");
        approve(ONSITE_LEAD, office).andExpect(status().isForbidden());
        approve(ONSITE_LEAD, onsite).andExpect(status().isOk());
        assertThat(pending(ONSITE_LEAD)).doesNotContain("EXPLANATION:" + office);
        assertThat(pending(HR)).contains("EXPLANATION:" + office);
    }

    @Test
    void rejectNeedsNote() throws Exception {
        long id = submitOk(A, D13, "08:00", "17:00", "Quên chấm");
        reject(LEAD, id, " ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancerejectnoterequired"));
        mockMvc
            .perform(post("/api/attendance/explanations/" + id + "/reject").with(LEAD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancerejectnoterequired"));
        reject(LEAD, id, " Không có căn cứ ")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TU_CHOI"))
            .andExpect(jsonPath("$.decisionNote").value("Không có căn cứ"))
            .andExpect(jsonPath("$.decidedBy").value("lead-ex" + sfx));
        assertThat(punches(a, D13)).isEmpty();
        approve(LEAD, id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationnotpending"));
    }

    @Test
    void withdrawOnlyOwnPending() throws Exception {
        long id = submitOk(A, D13, "08:00", null, "Quên chấm vào");
        withdraw(B, id).andExpect(status().isNotFound());
        withdraw(LEAD, id).andExpect(status().isNotFound());
        withdraw(HR, id).andExpect(status().isNotFound());
        withdraw(A, 987654321L).andExpect(status().isNotFound());
        assertThat(explanationStatus(id)).isEqualTo("DANG_CHO");
        withdraw(A, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_RUT"));
        withdraw(A, id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationnotpending"));
        approve(LEAD, id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationnotpending"));
        long rejected = submitOk(A, D14, "08:00", null, "Quên chấm vào");
        reject(LEAD, rejected, "Không").andExpect(status().isOk());
        withdraw(A, rejected).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.explanationnotpending"));
    }

    @Test
    void pendingListScopedAndArchivedHidden() throws Exception {
        AttendancePunch pa = app(a, D13, AttendancePunchKind.VAO, "08:20", AttendancePunchStatus.CHO_DUYET);
        app(a, D13, AttendancePunchKind.RA, "17:00", AttendancePunchStatus.CHAP_NHAN);
        long ea = submitOk(A, D14, "08:00", null, "Quên chấm vào");
        AttendancePunch pb = app(b, D13, AttendancePunchKind.VAO, "08:30", AttendancePunchStatus.CHO_DUYET);
        long eb = submitOk(B, D14, null, "17:00", "Quên chấm ra");
        AttendancePunch pz = app(z, D13, AttendancePunchKind.VAO, "08:10", AttendancePunchStatus.CHO_DUYET);
        long ez = submitOk(as("z-ex" + sfx), D14, "08:00", null, "Quên chấm vào");
        Employee archived = employeeRepository.findById(z.getId()).orElseThrow();
        archived.setArchivedAt(NOW);
        employeeRepository.saveAndFlush(archived);

        Set<String> lead1 = pending(LEAD);
        assertThat(lead1).contains("PUNCH:" + pa.getId(), "EXPLANATION:" + ea);
        assertThat(lead1).doesNotContain("PUNCH:" + pb.getId(), "EXPLANATION:" + eb, "PUNCH:" + pz.getId(), "EXPLANATION:" + ez);
        Set<String> lead2Items = pending(LEAD2);
        assertThat(lead2Items).contains("PUNCH:" + pb.getId(), "EXPLANATION:" + eb);
        assertThat(lead2Items).doesNotContain("PUNCH:" + pa.getId(), "EXPLANATION:" + ea);
        Set<String> hr = pending(HR);
        assertThat(hr).contains("PUNCH:" + pa.getId(), "EXPLANATION:" + ea, "PUNCH:" + pb.getId(), "EXPLANATION:" + eb);
        assertThat(hr).doesNotContain("PUNCH:" + pz.getId(), "EXPLANATION:" + ez);
        assertThat(pending(A)).doesNotContain("PUNCH:" + pa.getId(), "EXPLANATION:" + ea);

        // Nội dung một dòng.
        mockMvc
            .perform(get("/api/attendance/pending").with(LEAD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type == 'PUNCH' && @.id == " + pa.getId() + ")].employeeCode").value("EX4" + sfx))
            .andExpect(jsonPath("$[?(@.type == 'PUNCH' && @.id == " + pa.getId() + ")].day").value("2026-10-13"))
            .andExpect(jsonPath("$[?(@.type == 'PUNCH' && @.id == " + pa.getId() + ")].summary").value("Vào 08:20"))
            .andExpect(jsonPath("$[?(@.type == 'PUNCH' && @.id == " + pa.getId() + ")].detail").value("Sát ranh giới"))
            .andExpect(jsonPath("$[?(@.type == 'EXPLANATION' && @.id == " + ea + ")].summary").value("Vào 08:00"))
            .andExpect(jsonPath("$[?(@.type == 'EXPLANATION' && @.id == " + ea + ")].detail").value("Quên chấm vào"));

        // Đã quyết thì không còn trong danh sách.
        approve(LEAD, ea).andExpect(status().isOk());
        assertThat(pending(LEAD)).doesNotContain("EXPLANATION:" + ea);
    }

    // ------------------------------------------------------------------ email (giao dịch thật: thư gửi sau commit)

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void emailsSent() throws Exception {
        boolean monthExisted = monthRepository.findByMonth("2026-10").isPresent();
        try {
            long ea = submitOk(A, D13, null, "17:12", "Quên chấm ra");
            verify(mailer).send(eq(mail("lead-ex")), contains("Giải trình"), anyString());
            verify(mailer, never()).send(eq(mail("ol-ex")), anyString(), anyString());
            verify(mailer, never()).send(eq(mail("hr-ex")), anyString(), anyString());

            long ec = submitOk(C, D13, "08:00", null, "Quên chấm vào");
            verify(mailer).send(eq(mail("ol-ex")), contains("Giải trình"), anyString());
            submitOk(D, D13, "08:00", null, "Quên chấm vào");
            verify(mailer).send(eq(mail("hr-ex")), contains("Giải trình"), anyString());

            approve(LEAD, ea).andExpect(status().isOk());
            verify(mailer).send(eq(mail("a-ex")), contains("được duyệt"), anyString());
            reject(ONSITE_LEAD, ec, "Không có căn cứ").andExpect(status().isOk());
            verify(mailer).send(eq(mail("c-ex")), contains("bị từ chối"), contains("Không có căn cứ"));

            doThrow(new MailSendException("SMTP hỏng")).when(mailer).send(anyString(), anyString(), anyString());
            long ea2 = submitOk(A, D14, "08:00", null, "Quên chấm vào");
            approve(LEAD, ea2).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_DUYET"));
            assertThat(explanationStatus(ea2)).isEqualTo("DA_DUYET");
            assertThat(punches(a, D14)).hasSize(1);
        } finally {
            cleanup(monthExisted);
        }
    }

    /** Xóa dữ liệu đã commit của test không chạy trong transaction. */
    private void cleanup(boolean monthExisted) {
        tx.executeWithoutResult(s -> {
            List<Long> ids = created.stream().map(Employee::getId).toList();
            List<Long> users = created.stream().map(e -> e.getUser().getId()).toList();
            String in = String.join(",", ids.stream().map(String::valueOf).toList());
            String uin = String.join(",", users.stream().map(String::valueOf).toList());
            jdbc.update("delete from attendance_punch_aud where employee_id in (" + in + ")");
            jdbc.update("delete from attendance_punch where employee_id in (" + in + ")");
            jdbc.update("delete from attendance_explanation_aud where employee_id in (" + in + ")");
            jdbc.update("delete from attendance_explanation where employee_id in (" + in + ")");
            jdbc.update("delete from work_site_onsite_lead where work_site_id = ?", k.getId());
            jdbc.update("delete from work_assignment_aud where employee_id in (" + in + ")");
            jdbc.update("delete from work_assignment where employee_id in (" + in + ")");
            jdbc.update("delete from work_site_aud where id = ?", k.getId());
            jdbc.update("delete from work_site where id = ?", k.getId());
            jdbc.update("delete from leave_ledger_entry where employee_id in (" + in + ")");
            jdbc.update("delete from employment_period_aud where employee_id in (" + in + ")");
            jdbc.update("delete from employment_period where employee_id in (" + in + ")");
            jdbc.update("update employee set manager_id = null where id in (" + in + ")");
            jdbc.update("delete from employee_aud where id in (" + in + ")");
            jdbc.update("delete from employee where id in (" + in + ")");
            jdbc.update("delete from jhi_user_authority where user_id in (" + uin + ")");
            jdbc.update("delete from jhi_user where id in (" + uin + ")");
            if (!monthExisted) {
                jdbc.update("delete from attendance_month where month = '2026-10'");
            }
        });
    }
}
