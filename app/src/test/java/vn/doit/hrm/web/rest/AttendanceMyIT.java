package vn.doit.hrm.web.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceExplanation;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchMethod;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.AttendancePunchStatus;
import vn.doit.hrm.domain.enumeration.ExplanationStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AttendanceExplanationRepository;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * "Bảng công của tôi" ({@code GET /api/attendance/my}) cho khung Lịch sử ở màn chấm công: số ngày chờ duyệt (lần tự chấm chờ duyệt hoặc
 * giải trình đang chờ, mỗi ngày tính một lần) tính ở server; lần tự chấm có nơi chấm và khoảng cách. Hôm nay 10/12/2026.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(AttendanceMyIT.FixedClockConfig.class)
class AttendanceMyIT {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-12-10T03:00:00Z"), VN);
        }
    }

    private static final RequestPostProcessor ME = user("me-my").authorities(() -> "ROLE_NHAN_VIEN");
    private static final String M = "2026-11";
    private static final LocalDate D3 = LocalDate.of(2026, 11, 3);
    private static final LocalDate D4 = LocalDate.of(2026, 11, 4);
    private static final LocalDate D5 = LocalDate.of(2026, 11, 5);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkSiteRepository workSiteRepository;

    @Autowired
    private AttendancePunchRepository punchRepository;

    @Autowired
    private AttendanceExplanationRepository explanationRepository;

    @Autowired
    private WorkCalendarService calendarService;

    private Employee me;
    private WorkSite office;

    @BeforeEach
    void init() {
        calendarService.setWorkingDays(
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        );
        me = LeaveFixtures.employee(employeeRepository, "MY0001", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        me.setUser(LeaveFixtures.user(userRepository, "me-my"));
        me = employeeRepository.saveAndFlush(me);
        office = workSiteRepository.saveAndFlush(new WorkSite().name("Văn phòng MY").type(WorkLocationType.VAN_PHONG).active(true));
    }

    private void punch(LocalDate day, AttendancePunchKind kind, String time, AttendancePunchSource source, AttendancePunchStatus st) {
        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(me.getId());
        p.setWorkDay(day);
        p.setPunchTime(LocalTime.parse(time));
        p.setKind(kind);
        p.setSource(source);
        p.setStatus(st);
        p.setEnteredBy("me-my");
        p.setEnteredAt(Instant.parse("2026-11-03T01:00:00Z"));
        if (source == AttendancePunchSource.APP) {
            p.setMethod(AttendancePunchMethod.GPS);
            p.setSiteId(office.getId());
            p.setDistanceM(210);
        }
        punchRepository.saveAndFlush(p);
    }

    private void explanation(LocalDate day, ExplanationStatus st) {
        AttendanceExplanation x = new AttendanceExplanation();
        x.setEmployeeId(me.getId());
        x.setWorkDay(day);
        x.setProposedOut(LocalTime.of(17, 0));
        x.setReason("Quên chấm ra");
        x.setStatus(st);
        x.setCreatedAt(Instant.parse("2026-11-06T10:00:00Z"));
        explanationRepository.saveAndFlush(x);
    }

    @Test
    void myMonthCountsPendingDaysAndCarriesSiteAndDistance() throws Exception {
        // 03/11: lần tự chấm chờ duyệt và cũng có giải trình đang chờ (một ngày); 04/11: giải trình đang chờ; 05/11: giải trình đã rút.
        punch(D3, AttendancePunchKind.VAO, "08:40", AttendancePunchSource.APP, AttendancePunchStatus.CHO_DUYET);
        punch(D3, AttendancePunchKind.RA, "17:00", AttendancePunchSource.GO_TAY, AttendancePunchStatus.CHAP_NHAN);
        explanation(D3, ExplanationStatus.DANG_CHO);
        punch(D4, AttendancePunchKind.VAO, "08:00", AttendancePunchSource.GO_TAY, AttendancePunchStatus.CHAP_NHAN);
        explanation(D4, ExplanationStatus.DANG_CHO);
        explanation(D5, ExplanationStatus.DA_RUT);

        String d3 = "$.rows[0].days[?(@.day == '2026-11-03')]";
        mockMvc
            .perform(get("/api/attendance/my").param("month", M).with(ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].pendingDays").value(2))
            .andExpect(jsonPath(d3 + ".pendingCount").value(hasItem(1)))
            .andExpect(jsonPath(d3 + ".punches[?(@.source == 'APP')].siteName").value(hasItem("Văn phòng MY")))
            .andExpect(jsonPath(d3 + ".punches[?(@.source == 'APP')].distanceM").value(hasItem(210)));
    }

    @Test
    void myMonthWithoutPendingHasZero() throws Exception {
        punch(D4, AttendancePunchKind.VAO, "08:00", AttendancePunchSource.GO_TAY, AttendancePunchStatus.CHAP_NHAN);
        explanation(D5, ExplanationStatus.TU_CHOI);
        mockMvc
            .perform(get("/api/attendance/my").param("month", M).with(ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].pendingDays").value(0));
    }
}
