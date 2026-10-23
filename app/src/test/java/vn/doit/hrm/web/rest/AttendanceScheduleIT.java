package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.attendance.DayCredit;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.VietnameseText;
import vn.doit.hrm.service.attendance.AttendanceScheduleService;

/** Bộ giờ làm của công ty và khách hàng (theo ngày hiệu lực), trưởng nhóm onsite. Hôm nay: 23/10/2026. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(AttendanceScheduleIT.FixedClockConfig.class)
class AttendanceScheduleIT {

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-23T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final RequestPostProcessor HR = user("hr-gio").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor ADMIN = user("admin-gio").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor KE_TOAN = user("kt-gio").authorities(() -> "ROLE_KE_TOAN");
    private static final RequestPostProcessor NHAN_VIEN = user("nv-gio").authorities(() -> "ROLE_NHAN_VIEN");

    private static final DayCredit.Hours COMPANY = new DayCredit.Hours(
        LocalTime.of(8, 0),
        LocalTime.of(17, 0),
        LocalTime.of(12, 0),
        LocalTime.of(13, 0)
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AttendanceScheduleService service;

    @Autowired
    private AttendanceMonthRepository monthRepository;

    @Autowired
    private WorkSiteRepository workSiteRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private static int seq;

    private Map<String, Object> schedule(String from, String start, String end, String breakStart, String breakEnd, Long siteId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("effectiveFrom", from);
        body.put("startTime", start);
        body.put("endTime", end);
        body.put("breakStart", breakStart);
        body.put("breakEnd", breakEnd);
        if (siteId == null) {
            body.put("graceMinutes", 5);
            body.put("halfDayThresholdMinutes", 90);
        }
        body.put("workSiteId", siteId);
        return body;
    }

    private ResultActions postSchedule(RequestPostProcessor who, Map<String, Object> body) throws Exception {
        return mockMvc.perform(
            post("/api/attendance/schedules").with(who).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))
        );
    }

    private Long createdId(ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private WorkSite site(WorkLocationType type) {
        return workSiteRepository.saveAndFlush(new WorkSite().name("KH giờ " + System.nanoTime()).type(type).active(true));
    }

    private Employee employee(String name) {
        String code = "GIO" + (System.nanoTime() % 100000) + ++seq;
        return employeeRepository.saveAndFlush(
            new Employee()
                .code(code)
                .personType(PersonType.NHAN_VIEN)
                .fullName(name)
                .searchText(VietnameseText.fold(code + " " + name))
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
                .joinDate(LocalDate.of(2024, 3, 1))
        );
    }

    private ResultActions putLeads(RequestPostProcessor who, Long siteId, List<Long> employeeIds) throws Exception {
        return mockMvc.perform(
            put("/api/work-sites/{id}/onsite-leads", siteId)
                .with(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("employeeIds", employeeIds)))
        );
    }

    @Test
    void defaultCompanyScheduleSeeded() throws Exception {
        assertThat(service.hoursFor(LocalDate.of(2026, 10, 1), null)).isEqualTo(COMPANY);
        assertThat(service.hoursFor(LocalDate.of(2020, 1, 1), null)).isEqualTo(COMPANY);
        assertThat(service.rules(LocalDate.of(2026, 10, 1))).isEqualTo(new DayCredit.Rules(5, 90));
        mockMvc
            .perform(get("/api/attendance/schedules").with(NHAN_VIEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.effectiveFrom == '2020-01-01')].startTime").value(org.hamcrest.Matchers.hasItem("08:00")))
            .andExpect(jsonPath("$[?(@.effectiveFrom == '2020-01-01')].graceMinutes").value(org.hamcrest.Matchers.hasItem(5)));
    }

    @Test
    void scheduleChangeAppliesFromEffectiveDateOnly() throws Exception {
        postSchedule(HR, schedule("2026-11-15", "08:30", "17:30", "12:00", "13:00", null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.startTime").value("08:30"));
        assertThat(service.hoursFor(LocalDate.of(2026, 11, 14), null)).isEqualTo(COMPANY);
        assertThat(service.hoursFor(LocalDate.of(2026, 11, 15), null).start()).isEqualTo(LocalTime.of(8, 30));
        assertThat(service.hoursFor(LocalDate.of(2026, 12, 1), null).end()).isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    void customerHoursFallBackToCompany() throws Exception {
        WorkSite customer = site(WorkLocationType.KHACH_HANG);
        assertThat(service.hoursFor(LocalDate.of(2026, 11, 2), customer.getId())).isEqualTo(COMPANY);

        postSchedule(ADMIN, schedule("2026-11-01", "07:30", "16:30", "11:30", "12:30", customer.getId())).andExpect(status().isCreated());
        assertThat(service.hoursFor(LocalDate.of(2026, 10, 31), customer.getId())).isEqualTo(COMPANY);
        assertThat(service.hoursFor(LocalDate.of(2026, 11, 2), customer.getId())).isEqualTo(
            new DayCredit.Hours(LocalTime.of(7, 30), LocalTime.of(16, 30), LocalTime.of(11, 30), LocalTime.of(12, 30))
        );
        // Giờ công ty không bị ảnh hưởng; phút muộn cho phép và mốc nửa ngày luôn theo công ty.
        assertThat(service.hoursFor(LocalDate.of(2026, 11, 2), null)).isEqualTo(COMPANY);
        assertThat(service.rules(LocalDate.of(2026, 11, 2))).isEqualTo(new DayCredit.Rules(5, 90));
        mockMvc
            .perform(get("/api/attendance/schedules").param("siteId", customer.getId().toString()).with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].startTime").value("07:30"));

        // Chỉ nơi làm loại khách hàng có bộ giờ riêng.
        WorkSite office = site(WorkLocationType.VAN_PHONG);
        postSchedule(HR, schedule("2026-11-01", "07:30", "16:30", "11:30", "12:30", office.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleinvalidsite"));
    }

    @Test
    void cannotChangeScheduleInClosedMonth() throws Exception {
        AttendanceMonth closed = new AttendanceMonth();
        closed.setMonth("2026-11");
        closed.setStatus(AttendanceMonthStatus.DA_CHOT);
        closed.setClosedBy("hr");
        closed.setClosedAt(Instant.now());
        monthRepository.saveAndFlush(closed);

        postSchedule(HR, schedule("2026-11-20", "08:30", "17:30", "12:00", "13:00", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleclosedmonth"));
        assertThat(service.hoursFor(LocalDate.of(2026, 11, 20), null)).isEqualTo(COMPANY);
        // Tháng khác vẫn thêm được.
        postSchedule(HR, schedule("2026-12-01", "08:30", "17:30", "12:00", "13:00", null)).andExpect(status().isCreated());
    }

    private void close(String month) {
        AttendanceMonth m = new AttendanceMonth();
        m.setMonth(month);
        m.setStatus(AttendanceMonthStatus.DA_CHOT);
        m.setClosedBy("hr");
        m.setClosedAt(Instant.now());
        monthRepository.saveAndFlush(m);
    }

    /** R1: bộ giờ bắt đầu ở tháng đang mở nhưng còn hiệu lực sang tháng đã chốt (không có bộ giờ sau) cũng bị chặn. */
    @Test
    void cannotAddScheduleGoverningLaterClosedMonth() throws Exception {
        close("2026-11");
        postSchedule(HR, schedule("2026-10-15", "08:30", "17:30", "12:00", "13:00", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleclosedmonth"));
        assertThat(service.hoursFor(LocalDate.of(2026, 11, 20), null)).isEqualTo(COMPANY);

        WorkSite customer = site(WorkLocationType.KHACH_HANG);
        postSchedule(HR, schedule("2026-10-15", "07:30", "16:30", "11:30", "12:30", customer.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleclosedmonth"));
        assertThat(service.hoursFor(LocalDate.of(2026, 11, 20), customer.getId())).isEqualTo(COMPANY);
    }

    /** R1: bộ giờ có bộ giờ sau cùng phạm vi trước tháng đã chốt thì vẫn thêm được. */
    @Test
    void scheduleEndingBeforeClosedMonthAllowed() throws Exception {
        postSchedule(HR, schedule("2026-11-01", "08:00", "17:00", "12:00", "13:00", null)).andExpect(status().isCreated());
        WorkSite customer = site(WorkLocationType.KHACH_HANG);
        postSchedule(HR, schedule("2026-11-01", "07:30", "16:30", "11:30", "12:30", customer.getId())).andExpect(status().isCreated());
        close("2026-11");

        postSchedule(HR, schedule("2026-10-05", "08:15", "17:15", "12:00", "13:00", null)).andExpect(status().isCreated());
        postSchedule(HR, schedule("2026-10-05", "07:45", "16:45", "11:30", "12:30", customer.getId())).andExpect(status().isCreated());
        assertThat(service.hoursFor(LocalDate.of(2026, 10, 20), null).start()).isEqualTo(LocalTime.of(8, 15));
        assertThat(service.hoursFor(LocalDate.of(2026, 11, 20), null).start()).isEqualTo(LocalTime.of(8, 0));
        // Bộ giờ của khách hàng khác không chặn phạm vi công ty và ngược lại: công ty có bộ giờ 01/11 nhưng khách hàng mới thì không.
        WorkSite other = site(WorkLocationType.KHACH_HANG);
        postSchedule(HR, schedule("2026-10-05", "07:45", "16:45", "11:30", "12:30", other.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleclosedmonth"));
    }

    /** R1: bộ giờ kế tiếp bắt đầu giữa tháng đã chốt thì đầu tháng đó vẫn dùng bộ giờ mới thêm: chặn. */
    @Test
    void cannotAddScheduleGoverningStartOfClosedMonth() throws Exception {
        WorkSite customer = site(WorkLocationType.KHACH_HANG);
        postSchedule(HR, schedule("2026-11-15", "07:30", "16:30", "11:30", "12:30", customer.getId())).andExpect(status().isCreated());
        close("2026-11");
        postSchedule(HR, schedule("2026-10-05", "07:45", "16:45", "11:30", "12:30", customer.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleclosedmonth"));
    }

    /** R1: xóa bộ giờ tương lai mà phạm vi hiệu lực của nó (tới bộ giờ sau) phủ tháng đã chốt thì bị chặn. */
    @Test
    void cannotDeleteScheduleGoverningClosedMonth() throws Exception {
        Long nov = createdId(
            postSchedule(HR, schedule("2026-11-01", "08:30", "17:30", "12:00", "13:00", null)).andExpect(status().isCreated())
        );
        Long jan = createdId(
            postSchedule(HR, schedule("2027-01-01", "08:45", "17:45", "12:00", "13:00", null)).andExpect(status().isCreated())
        );
        close("2026-12");
        mockMvc
            .perform(delete("/api/attendance/schedules/{id}", nov).with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleclosedmonth"));
        assertThat(service.hoursFor(LocalDate.of(2026, 12, 10), null).start()).isEqualTo(LocalTime.of(8, 30));
        // Bộ giờ từ 01/2027 không phủ tháng 12 đã chốt: xóa được.
        mockMvc.perform(delete("/api/attendance/schedules/{id}", jan).with(HR)).andExpect(status().isNoContent());
    }

    @Test
    void onlyAdminHrWrite() throws Exception {
        postSchedule(KE_TOAN, schedule("2026-11-15", "08:30", "17:30", "12:00", "13:00", null)).andExpect(status().isForbidden());
        postSchedule(NHAN_VIEN, schedule("2026-11-15", "08:30", "17:30", "12:00", "13:00", null)).andExpect(status().isForbidden());
        Long id = createdId(postSchedule(HR, schedule("2026-11-15", "08:30", "17:30", "12:00", "13:00", null)).andExpect(status().isCreated()));
        mockMvc.perform(delete("/api/attendance/schedules/{id}", id).with(KE_TOAN)).andExpect(status().isForbidden());
        WorkSite customer = site(WorkLocationType.KHACH_HANG);
        putLeads(KE_TOAN, customer.getId(), List.of()).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/attendance/schedules")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidHoursRejected() throws Exception {
        postSchedule(HR, schedule("2026-11-15", "17:00", "08:00", "12:00", "13:00", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleinvalid"));
        postSchedule(HR, schedule("2026-11-15", "08:00", "17:00", "13:00", "12:00", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleinvalid"));
        Map<String, Object> noRules = schedule("2026-11-15", "08:00", "17:00", "12:00", "13:00", null);
        noRules.remove("graceMinutes");
        postSchedule(HR, noRules).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.attendancescheduleinvalid"));
    }

    @Test
    void deleteOnlyFutureSchedule() throws Exception {
        Long future = createdId(
            postSchedule(HR, schedule("2026-12-01", "08:30", "17:30", "12:00", "13:00", null)).andExpect(status().isCreated())
        );
        Long past = createdId(
            postSchedule(HR, schedule("2026-10-01", "08:15", "17:15", "12:00", "13:00", null)).andExpect(status().isCreated())
        );
        mockMvc
            .perform(delete("/api/attendance/schedules/{id}", past).with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduledeletepast"));
        mockMvc.perform(delete("/api/attendance/schedules/{id}", future).with(HR)).andExpect(status().isNoContent());
        assertThat(service.hoursFor(LocalDate.of(2026, 12, 2), null).start()).isEqualTo(LocalTime.of(8, 15));
    }

    @Test
    void onsiteLeadsSaved() throws Exception {
        WorkSite customer = site(WorkLocationType.KHACH_HANG);
        WorkSite other = site(WorkLocationType.KHACH_HANG);
        Employee a = employee("Trần Văn Onsite");
        Employee b = employee("Lê Thị Onsite");

        putLeads(HR, customer.getId(), List.of(a.getId(), b.getId())).andExpect(status().isOk());
        putLeads(ADMIN, other.getId(), List.of(a.getId())).andExpect(status().isOk());
        assertThat(service.onsiteLeadSiteIds(a.getId())).containsExactlyInAnyOrder(customer.getId(), other.getId());
        assertThat(service.onsiteLeadSiteIds(b.getId())).containsExactly(customer.getId());
        mockMvc
            .perform(get("/api/work-sites/{id}/onsite-leads", customer.getId()).with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        // Thay cả danh sách: b không còn là trưởng nhóm onsite.
        putLeads(HR, customer.getId(), List.of(a.getId())).andExpect(status().isOk());
        assertThat(service.onsiteLeadSiteIds(b.getId())).isEmpty();
        assertThat(service.onsiteLeadSiteIds(a.getId())).containsExactlyInAnyOrder(customer.getId(), other.getId());

        // Hồ sơ đã lưu trữ không gán làm trưởng nhóm onsite được.
        jdbc.update("update employee set archived_at = now() where id = ?", b.getId());
        putLeads(HR, customer.getId(), List.of(a.getId(), b.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeearchived"));
        // Chỉ nơi làm loại khách hàng có trưởng nhóm onsite.
        WorkSite office = site(WorkLocationType.VAN_PHONG);
        putLeads(HR, office.getId(), List.of(a.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancescheduleinvalidsite"));
    }
}
