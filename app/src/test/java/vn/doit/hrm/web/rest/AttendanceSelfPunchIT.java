package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.AttendancePunchStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AttendanceConsentRepository;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.attendance.SelfPunchService;
import vn.doit.hrm.service.leave.LeaveFixtures;

/**
 * Nhân viên tự chấm công trên web. Hôm nay: thứ Sáu 23/10/2026, 08:03 (Asia/Ho_Chi_Minh). Văn phòng "Văn phòng SP" có dải IP
 * 203.0.113.0/24, tọa độ 21.0279,105.8360, bán kính 200 m.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(AttendanceSelfPunchIT.MutableClockConfig.class)
class AttendanceSelfPunchIT {

    /** Đồng hồ tiến được trong test (bấm lại sau 30 giây, đổi ngày). */
    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now = new AtomicReference<>(START);
        private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

        void set(Instant i) {
            now.set(i);
        }

        void plusSeconds(long s) {
            now.updateAndGet(i -> i.plusSeconds(s));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId z) {
            return Clock.fixed(now.get(), z);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    private static final Instant START = Instant.parse("2026-10-23T01:03:00Z"); // 08:03 giờ Việt Nam
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final String OFFICE_IP = "203.0.113.77";
    private static final String OUTSIDE_IP = "198.51.100.5";
    private static final double OFFICE_LAT = 21.0279;
    private static final double OFFICE_LNG = 105.8360;

    private static final RequestPostProcessor ADMIN = user("admin-sp").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor HR = user("hr-sp").authorities(() -> "ROLE_HR");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MutableClock clock;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkSiteRepository workSiteRepository;

    @Autowired
    private WorkAssignmentRepository assignmentRepository;

    @Autowired
    private AttendancePunchRepository punchRepository;

    @Autowired
    private AttendanceConsentRepository consentRepository;

    @Autowired
    private AttendanceMonthRepository monthRepository;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private WorkSite office;
    private Employee me;
    private String login;
    private RequestPostProcessor ME;

    @BeforeEach
    void init() {
        clock.set(START);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        login = "sp-nv-" + suffix;
        ME = user(login).authorities(() -> "ROLE_NHAN_VIEN");
        office = new WorkSite()
            .name("Văn phòng SP " + suffix)
            .type(WorkLocationType.VAN_PHONG)
            .active(true);
        office.setLatitude(new BigDecimal("21.027900"));
        office.setLongitude(new BigDecimal("105.836000"));
        office.setRadiusM(200);
        office.setOfficeIps("203.0.113.0/24");
        office = workSiteRepository.saveAndFlush(office);
        me = person("SP" + suffix, PersonType.NHAN_VIEN, login);
    }

    private Employee person(String code, PersonType type, String userLogin) {
        Employee p = LeaveFixtures.employee(employeeRepository, code, type, LocalDate.of(2024, 1, 2));
        if (userLogin != null) {
            p.setUser(LeaveFixtures.user(userRepository, userLogin));
        }
        return employeeRepository.saveAndFlush(p);
    }

    private void assign(Employee who, WorkLocationType type, WorkSite site, LocalDate from, LocalDate to) {
        WorkAssignment w = new WorkAssignment().type(type).fromDate(from).toDate(to).employee(who);
        w.setSite(site);
        assignmentRepository.saveAndFlush(w);
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions punch(RequestPostProcessor who, String ip, String kind, Double lat, Double lng, Double acc) throws Exception {
        return punch(who, ip, kind, lat, lng, acc, null);
    }

    private ResultActions punch(RequestPostProcessor who, String ip, String kind, Double lat, Double lng, Double acc, String forwardedFor)
        throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        if (lat != null) body.put("latitude", lat);
        if (lng != null) body.put("longitude", lng);
        if (acc != null) body.put("accuracy", acc);
        MockHttpServletRequestBuilder req = post("/api/attendance/punches")
            .with(who)
            .with(from(ip))
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(body));
        if (forwardedFor != null) {
            req = req.header("X-Forwarded-For", forwardedFor);
        }
        return mockMvc.perform(req);
    }

    private void consent(RequestPostProcessor who) throws Exception {
        mockMvc.perform(post("/api/attendance/consent").with(who)).andExpect(status().isNoContent());
    }

    private List<AttendancePunch> punchesOf(Employee who) {
        return punchRepository.findByEmployeeIdAndWorkDayOrderByPunchTimeAsc(who.getId(), TODAY);
    }

    // ------------------------------------------------------------------ đồng ý thông báo

    @Test
    void consentRequiredFirst() throws Exception {
        punch(ME, OFFICE_IP, "VAO", null, null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendanceconsentrequired"));
        assertThat(punchesOf(me)).isEmpty();

        consent(ME);
        assertThat(consentRepository.findByEmployeeId(me.getId())).hasValueSatisfying(c -> {
            assertThat(c.getNoticeVersion()).isEqualTo(1);
            assertThat(c.getAcceptedAt()).isEqualTo(START);
        });
        // Đồng ý lần nữa không tạo dòng mới
        consent(ME);
        assertThat(consentRepository.count()).isGreaterThanOrEqualTo(1);

        punch(ME, OFFICE_IP, "VAO", null, null, null).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ bảng xác minh

    @Test
    void officeIpAccepted() throws Exception {
        consent(ME);
        punch(ME, OFFICE_IP, "VAO", null, null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHAP_NHAN"))
            .andExpect(jsonPath("$.method").value("IP"))
            .andExpect(jsonPath("$.kind").value("VAO"))
            .andExpect(jsonPath("$.time").value("08:03"))
            .andExpect(jsonPath("$.siteName").value(office.getName()));
        AttendancePunch p = punchesOf(me).get(0);
        assertThat(p.getSource()).isEqualTo(AttendancePunchSource.APP);
        assertThat(p.getPunchTime()).isEqualTo(LocalTime.of(8, 3));
        assertThat(p.getSiteId()).isEqualTo(office.getId());
        assertThat(p.getClientIp()).isEqualTo(OFFICE_IP);
        assertThat(p.getStatus()).isEqualTo(AttendancePunchStatus.CHAP_NHAN);
        assertThat(p.getEnteredBy()).isEqualTo(login);
    }

    @Test
    void gpsInsideAccepted() throws Exception {
        consent(ME);
        punch(ME, OUTSIDE_IP, "VAO", 21.0285, 105.8365, 20.0)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHAP_NHAN"))
            .andExpect(jsonPath("$.method").value("GPS"))
            .andExpect(jsonPath("$.distanceM").value(lessThan(200)));
        AttendancePunch p = punchesOf(me).get(0);
        assertThat(p.getLatitude()).isEqualByComparingTo("21.0285");
        assertThat(p.getLongitude()).isEqualByComparingTo("105.8365");
        assertThat(p.getAccuracyM()).isEqualByComparingTo("20");
    }

    @Test
    void gpsNearEdgeOrLowAccuracyPending() throws Exception {
        consent(ME);
        // Sai số 140 m (> 100 m) dù ở ngay văn phòng
        punch(ME, OUTSIDE_IP, "VAO", OFFICE_LAT, OFFICE_LNG, 140.0)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHO_DUYET"))
            .andExpect(jsonPath("$.method").value("GPS"));
        // Cách khoảng 255 m, sai số 80 m: trong bán kính + sai số
        punch(ME, OUTSIDE_IP, "RA", 21.0302, OFFICE_LNG, 80.0)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHO_DUYET"))
            .andExpect(jsonPath("$.message").value(containsString("sát ranh giới")));
        assertThat(punchesOf(me)).allSatisfy(p -> assertThat(p.getReviewReason()).isNotBlank());
    }

    @Test
    void gpsFarRejectedButStored() throws Exception {
        consent(ME);
        punch(ME, OUTSIDE_IP, "VAO", 21.0495, OFFICE_LNG, 20.0)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TU_CHOI"))
            .andExpect(jsonPath("$.method").value("GPS"))
            .andExpect(jsonPath("$.message").value(containsString("2,4 km")))
            .andExpect(jsonPath("$.message").value(containsString("Hôm nay bạn phải chấm tại " + office.getName())));
        List<AttendancePunch> rows = punchesOf(me);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(AttendancePunchStatus.TU_CHOI);
        assertThat(rows.get(0).getDistanceM()).isBetween(2350, 2450);
        assertThat(rows.get(0).getSiteId()).isEqualTo(office.getId());
        assertThat(rows.get(0).getReviewReason()).contains("2,4 km");
        assertThat(SelfPunchService.counts(rows.get(0))).isFalse();
    }

    @Test
    void noLocationInsideOfficeIpAccepted() throws Exception {
        consent(ME);
        punch(ME, OFFICE_IP, "VAO", null, null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHAP_NHAN"))
            .andExpect(jsonPath("$.method").value("IP"));
    }

    @Test
    void noLocationOutsideIsPending() throws Exception {
        consent(ME);
        punch(ME, OUTSIDE_IP, "VAO", null, null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHO_DUYET"))
            .andExpect(jsonPath("$.method").value(nullValue()))
            .andExpect(jsonPath("$.message").value(containsString("Không có vị trí")));
    }

    @Test
    void remoteAccepted() throws Exception {
        assign(me, WorkLocationType.TU_XA, null, LocalDate.of(2026, 10, 1), null);
        consent(ME);
        punch(ME, OUTSIDE_IP, "VAO", null, null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHAP_NHAN"))
            .andExpect(jsonPath("$.method").value("TU_XA"));
        assertThat(punchesOf(me).get(0).getSiteId()).isNull();
    }

    @Test
    void siteFollowsAssignmentOfThatDay() throws Exception {
        WorkSite k = new WorkSite()
            .name("Khách hàng K SP " + UUID.randomUUID().toString().substring(0, 6))
            .type(WorkLocationType.KHACH_HANG)
            .active(true);
        k.setLatitude(new BigDecimal("10.776900"));
        k.setLongitude(new BigDecimal("106.700900"));
        k.setRadiusM(200);
        k = workSiteRepository.saveAndFlush(k);
        assign(me, WorkLocationType.KHACH_HANG, k, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 22));
        assign(me, WorkLocationType.VAN_PHONG, office, LocalDate.of(2026, 10, 23), null);
        consent(ME);

        // 23/10: văn phòng
        punch(ME, OFFICE_IP, "VAO", null, null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHAP_NHAN"))
            .andExpect(jsonPath("$.siteName").value(office.getName()));
        // Ở khách hàng K hôm nay thì bị từ chối: hôm nay phải chấm tại văn phòng
        punch(ME, OUTSIDE_IP, "RA", 10.7769, 106.7009, 10.0)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TU_CHOI"))
            .andExpect(jsonPath("$.message").value(containsString(office.getName())));

        // 22/10: tại K
        clock.set(Instant.parse("2026-10-22T01:03:00Z"));
        punch(ME, OUTSIDE_IP, "VAO", 10.7769, 106.7009, 10.0)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHAP_NHAN"))
            .andExpect(jsonPath("$.method").value("GPS"))
            .andExpect(jsonPath("$.siteName").value(k.getName()));
        // IP văn phòng ngày 22/10 không còn được tính
        punch(ME, OFFICE_IP, "RA", null, null, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHO_DUYET"));
        mockMvc
            .perform(get("/api/attendance/punches/today").with(ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.siteKind").value("KHACH_HANG"))
            .andExpect(jsonPath("$.siteName").value(k.getName()));
    }

    @Test
    void undeclaredSitePending() throws Exception {
        WorkSite bare = workSiteRepository.saveAndFlush(
            new WorkSite()
                .name("Điểm trống SP " + UUID.randomUUID().toString().substring(0, 6))
                .type(WorkLocationType.VAN_PHONG)
                .active(true)
        );
        assign(me, WorkLocationType.VAN_PHONG, bare, LocalDate.of(2026, 10, 1), null);
        consent(ME);
        punch(ME, OFFICE_IP, "VAO", OFFICE_LAT, OFFICE_LNG, 10.0)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHO_DUYET"))
            .andExpect(jsonPath("$.message").value(containsString("Địa điểm chưa khai báo")));
    }

    // ------------------------------------------------------------------ chặn bấm trùng

    /** Bấm lại trong 1 phút (tuần tự, rồi song song): chỉ một dòng. Test tự commit và tự dọn. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void doubleTapWithinMinuteStoresOnce() throws Exception {
        try {
            consent(ME);
            String first = punch(ME, OFFICE_IP, "VAO", null, null, null)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
            long id = om.readTree(first).get("id").asLong();
            clock.plusSeconds(30);
            punch(ME, OFFICE_IP, "VAO", null, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.time").value("08:03"));
            assertThat(count(me, AttendancePunchKind.VAO)).isEqualTo(1);

            // Hai lần bấm Ra gửi cùng lúc
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            try {
                List<Future<JsonNode>> results = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    Callable<JsonNode> call = () -> {
                        go.await();
                        String body = punch(ME, OFFICE_IP, "RA", null, null, null).andReturn().getResponse().getContentAsString();
                        return om.readTree(body);
                    };
                    results.add(pool.submit(call));
                }
                go.countDown();
                long a = results.get(0).get(30, TimeUnit.SECONDS).get("id").asLong();
                long b = results.get(1).get(30, TimeUnit.SECONDS).get("id").asLong();
                assertThat(a).isEqualTo(b);
            } finally {
                pool.shutdownNow();
            }
            assertThat(count(me, AttendancePunchKind.RA)).isEqualTo(1);

            // Sau hơn 1 phút: ghi lần mới
            clock.plusSeconds(61);
            punch(ME, OFFICE_IP, "VAO", null, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(not(id)));
            assertThat(count(me, AttendancePunchKind.VAO)).isEqualTo(2);
        } finally {
            Long eid = me.getId();
            Long uid = me.getUser().getId();
            Long sid = office.getId();
            tx.executeWithoutResult(s -> {
                jdbc.update("delete from attendance_punch where employee_id = ?", eid);
                jdbc.update("delete from attendance_consent where employee_id = ?", eid);
                jdbc.update("delete from leave_ledger_entry where employee_id = ?", eid);
                jdbc.update("delete from employment_period where employee_id = ?", eid);
                jdbc.update("delete from employee where id = ?", eid);
                jdbc.update("delete from jhi_user where id = ?", uid);
                jdbc.update("delete from work_site where id = ?", sid);
            });
        }
    }

    private long count(Employee who, AttendancePunchKind kind) {
        Long n = jdbc.queryForObject(
            "select count(*) from attendance_punch where employee_id = ? and kind = ? and source = 'APP'",
            Long.class,
            who.getId(),
            kind.name()
        );
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------ chặn

    @Test
    void closedMonthRejected() throws Exception {
        consent(ME);
        AttendanceMonth m = monthRepository.findByMonth("2026-10").orElseGet(AttendanceMonth::new);
        m.setMonth("2026-10");
        m.setStatus(AttendanceMonthStatus.DA_CHOT);
        m.setClosedBy("test");
        m.setClosedAt(START);
        monthRepository.saveAndFlush(m);
        punch(ME, OFFICE_IP, "VAO", null, null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancemonthclosed"));
        assertThat(punchesOf(me)).isEmpty();
    }

    @Test
    void notApplicable() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        person("TT" + suffix, PersonType.THUC_TAP, "sp-tt-" + suffix);
        RequestPostProcessor intern = user("sp-tt-" + suffix).authorities(() -> "ROLE_NHAN_VIEN");
        punch(intern, OFFICE_IP, "VAO", null, null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancenotapplicable"));
        mockMvc
            .perform(post("/api/attendance/consent").with(intern))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancenotapplicable"));

        RequestPostProcessor noProfile = user("sp-none-" + suffix).authorities(() -> "ROLE_NHAN_VIEN");
        punch(noProfile, OFFICE_IP, "VAO", null, null, null).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/attendance/punches/today").with(noProfile)).andExpect(status().isNotFound());

        Employee archived = person("AR" + suffix, PersonType.NHAN_VIEN, "sp-ar-" + suffix);
        archived.setArchivedAt(Instant.parse("2026-10-01T00:00:00Z"));
        employeeRepository.saveAndFlush(archived);
        punch(user("sp-ar-" + suffix).authorities(() -> "ROLE_NHAN_VIEN"), OFFICE_IP, "VAO", null, null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeearchived"));

        // Đã nghỉ (ngày làm việc cuối đã qua)
        me.setLastWorkingDay(LocalDate.of(2026, 10, 22));
        employeeRepository.saveAndFlush(me);
        punch(ME, OFFICE_IP, "VAO", null, null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancenotapplicable"));
    }

    @Test
    void invalidBodyRejected() throws Exception {
        consent(ME);
        punch(ME, OFFICE_IP, "NGU", null, null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancepunchinvalid"));
        punch(ME, OFFICE_IP, "VAO", 21.0, null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancepunchinvalid"));
        punch(ME, OFFICE_IP, "VAO", 95.0, 105.0, 10.0)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancepunchinvalid"));
        assertThat(punchesOf(me)).isEmpty();
    }

    @Test
    void forwardedHeaderNotTrustedByDefault() throws Exception {
        consent(ME);
        punch(ME, OUTSIDE_IP, "VAO", null, null, null, "203.0.113.9")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHO_DUYET"))
            .andExpect(jsonPath("$.method").value(not("IP")));
        assertThat(punchesOf(me).get(0).getClientIp()).isEqualTo(OUTSIDE_IP);
    }

    // ------------------------------------------------------------------ hôm nay

    @Test
    void todayEndpoint() throws Exception {
        assign(me, WorkLocationType.VAN_PHONG, office, LocalDate.of(2026, 10, 1), null);
        mockMvc
            .perform(get("/api/attendance/punches/today").with(ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.day").value("2026-10-23"))
            .andExpect(jsonPath("$.siteName").value(office.getName()))
            .andExpect(jsonPath("$.siteKind").value("VAN_PHONG"))
            .andExpect(jsonPath("$.start").value("08:00"))
            .andExpect(jsonPath("$.end").value("17:00"))
            .andExpect(jsonPath("$.consentRequired").value(true))
            .andExpect(jsonPath("$.noticeText").value(not(nullValue())))
            .andExpect(jsonPath("$.punches", hasSize(0)));
        consent(ME);
        punch(ME, OFFICE_IP, "VAO", null, null, null).andExpect(status().isOk());
        clock.plusSeconds(120);
        punch(ME, OUTSIDE_IP, "RA", 21.0495, OFFICE_LNG, 20.0).andExpect(status().isOk());
        mockMvc
            .perform(get("/api/attendance/punches/today").with(ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consentRequired").value(false))
            .andExpect(jsonPath("$.punches", hasSize(2)))
            .andExpect(jsonPath("$.punches[0].kind").value("VAO"))
            .andExpect(jsonPath("$.punches[0].status").value("CHAP_NHAN"))
            .andExpect(jsonPath("$.punches[1].kind").value("RA"))
            .andExpect(jsonPath("$.punches[1].time").value("08:05"))
            .andExpect(jsonPath("$.punches[1].status").value("TU_CHOI"))
            .andExpect(jsonPath("$.punches[1].message").value(containsString("2,4 km")));
    }

    // ------------------------------------------------------------------ cài đặt thông báo

    @Test
    void noticeSettingsAdminOnlyAndVersionBumps() throws Exception {
        String body = mockMvc
            .perform(get("/api/company-setting/attendance-notice").with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.noticeText").value(not(nullValue())))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode before = om.readTree(body);
        int version = before.get("noticeVersion").asInt();
        mockMvc.perform(get("/api/company-setting/attendance-notice").with(ME)).andExpect(status().isForbidden());

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("noticeText", "Nội dung mới " + UUID.randomUUID());
        change.put("locationRetentionDays", 60);
        mockMvc
            .perform(
                put("/api/company-setting/attendance-notice")
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(change))
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                put("/api/company-setting/attendance-notice")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(change))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.noticeVersion").value(version + 1))
            .andExpect(jsonPath("$.locationRetentionDays").value(60));
        // Cùng nội dung, đổi số ngày: phiên bản giữ nguyên
        change.put("locationRetentionDays", 30);
        mockMvc
            .perform(
                put("/api/company-setting/attendance-notice")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(change))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.noticeVersion").value(version + 1))
            .andExpect(jsonPath("$.locationRetentionDays").value(30));
        change.put("locationRetentionDays", 0);
        mockMvc
            .perform(
                put("/api/company-setting/attendance-notice")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(change))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancenoticesettinginvalid"));

        // Đồng ý ghi phiên bản hiện tại; đổi nội dung sau đó không bắt đồng ý lại
        consent(ME);
        assertThat(consentRepository.findByEmployeeId(me.getId()).orElseThrow().getNoticeVersion()).isEqualTo(version + 1);
        change.put("noticeText", "Nội dung khác " + UUID.randomUUID());
        change.put("locationRetentionDays", 90);
        mockMvc
            .perform(
                put("/api/company-setting/attendance-notice")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(change))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.noticeVersion").value(version + 2));
        mockMvc.perform(get("/api/attendance/punches/today").with(ME)).andExpect(jsonPath("$.consentRequired").value(false));
    }

    // ------------------------------------------------------------------ được tính công

    @Test
    void countsRule() {
        assertThat(SelfPunchService.counts(p(AttendancePunchSource.GO_TAY, AttendancePunchStatus.CHAP_NHAN))).isTrue();
        assertThat(SelfPunchService.counts(p(AttendancePunchSource.GIAI_TRINH, AttendancePunchStatus.CHAP_NHAN))).isTrue();
        assertThat(SelfPunchService.counts(p(AttendancePunchSource.APP, AttendancePunchStatus.CHAP_NHAN))).isTrue();
        assertThat(SelfPunchService.counts(p(AttendancePunchSource.APP, AttendancePunchStatus.DA_DUYET))).isTrue();
        assertThat(SelfPunchService.counts(p(AttendancePunchSource.APP, AttendancePunchStatus.CHO_DUYET))).isFalse();
        assertThat(SelfPunchService.counts(p(AttendancePunchSource.APP, AttendancePunchStatus.TU_CHOI))).isFalse();
    }

    private static AttendancePunch p(AttendancePunchSource source, AttendancePunchStatus st) {
        AttendancePunch x = new AttendancePunch();
        x.setSource(source);
        x.setStatus(st);
        return x;
    }
}
