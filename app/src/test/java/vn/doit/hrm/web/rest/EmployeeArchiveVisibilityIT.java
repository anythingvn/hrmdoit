package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.DayPart;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.PayrollLineRecord;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.PayrollVersion;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.PayrollLineRecordRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayrollVersionRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.leave.ApproverResolver;
import vn.doit.hrm.service.leave.LeaveApprovalDigest;
import vn.doit.hrm.service.leave.LeaveFixtures;

/**
 * Hồ sơ đã lưu trữ không hiện ở danh sách, tìm kiếm, xuất Excel, ô chọn, nhắc việc, Tổng quan, lịch nghỉ, số dư phép công ty, hộp duyệt
 * đơn; vẫn hiện ở bảng lương / phiếu lương kỳ cũ và tổng hợp kế toán kèm cờ {@code archived}.
 *
 * <p>A, B: NHAN_VIEN đã nghỉ (ngày làm việc cuối 15/09/2026), A đã lưu trữ. M: hồ sơ tạo nhầm đã lưu trữ, vẫn "đang làm việc", có tài
 * khoản đang hoạt động, là quản lý trực tiếp của C và trưởng phòng của C (dữ liệu cũ). C: nhân viên đang làm.
 *
 * <p>Mang {@code @TestConfiguration} riêng (Clock cố định) nên dựng context riêng: chứng minh container PostgreSQL dùng chung không bị
 * dừng khi bộ nhớ đệm context của Spring đóng bớt context (R3).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(EmployeeArchiveVisibilityIT.FixedClockConfig.class)
class EmployeeArchiveVisibilityIT {

    /** Hôm nay 22/10/2026. */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final RequestPostProcessor HR = user("hr-arv").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor KT = user("kt-arv").authorities(() -> "ROLE_KE_TOAN");
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 9, 15);
    private static final String PAYROLL_PERIOD = "2016-09";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private vn.doit.hrm.repository.AuthorityRepository authorityRepository;

    @Autowired
    private LeaveRequestRepository requestRepository;

    @Autowired
    private PayrollPeriodRepository periodRepository;

    @Autowired
    private PayrollVersionRepository versionRepository;

    @Autowired
    private PayrollLineRecordRepository lineRepository;

    @Autowired
    private ApproverResolver approverResolver;

    @Autowired
    private LeaveApprovalDigest digest;

    private Employee a;
    private Employee b;
    private Employee m;
    private Employee c;
    private Department dept;

    @BeforeEach
    void seed() {
        dept = LeaveFixtures.department(departmentRepository, "Phòng ARV");
        a = left(LeaveFixtures.employee(employeeRepository, "ARV901", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2)));
        b = left(LeaveFixtures.employee(employeeRepository, "ARV902", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2)));

        User mu = LeaveFixtures.user(userRepository, "arv-m");
        mu.setEmail("arv-m@example.vn");
        userRepository.saveAndFlush(mu);
        m = LeaveFixtures.employee(employeeRepository, "ARV903", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        m.setUser(mu);
        m.setDepartment(dept);
        m.setBirthDate(LocalDate.of(1990, 10, 30));
        m = employeeRepository.saveAndFlush(m);

        dept.setHead(m);
        departmentRepository.saveAndFlush(dept);

        User cu = LeaveFixtures.user(userRepository, "arv-c");
        c = LeaveFixtures.employee(employeeRepository, "ARV904", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        c.setUser(cu);
        c.setManager(m);
        c.setDepartment(dept);
        c = employeeRepository.saveAndFlush(c);

        // Đơn nghỉ đã duyệt trong tháng nghỉ việc.
        leave(a, LeaveRequestStatus.DA_DUYET, "2026-09-08", ApproverKind.BGD, null);
        leave(b, LeaveRequestStatus.DA_DUYET, "2026-09-08", ApproverKind.BGD, null);

        // Kỳ lương cũ đã duyệt có dòng của A và B.
        PayrollPeriod p = new PayrollPeriod();
        p.setPeriod(PAYROLL_PERIOD);
        p.setStatus(PayrollPeriodStatus.DA_DUYET);
        p.setApprovedVersion(1);
        p.setApprovedBy("admin");
        p.setApprovedAt(Instant.parse("2016-10-05T03:00:00Z"));
        p = periodRepository.saveAndFlush(p);
        PayrollVersion v = new PayrollVersion();
        v.setPeriod(p);
        v.setVersion(1);
        v.setFileName("luong-2016-09.xlsx");
        v.setStoragePath("payroll/arv/luong-2016-09.xlsx");
        v.setImportedBy("kt");
        v.setImportedAt(Instant.parse("2016-10-01T03:00:00Z"));
        v = versionRepository.saveAndFlush(v);
        line(v, a, 1);
        line(v, b, 2);

        archive(a);
        archive(m);
    }

    @Test
    void archivedHiddenEverywhere() throws Exception {
        // Danh sách, đếm, ô tìm người.
        assertThat(codes(json(get("/api/employees?code.contains=ARV90&size=50").with(HR)), "$[*].code")).contains("ARV902").doesNotContain(
            "ARV901",
            "ARV903"
        );
        assertThat(json(get("/api/employees/count?code.contains=ARV90").with(HR))).isEqualTo("2");
        assertThat(codes(json(get("/api/employees?q.contains=arv90&size=50").with(HR)), "$[*].code")).contains("ARV902", "ARV904").doesNotContain(
            "ARV901",
            "ARV903"
        );

        // Xuất Excel.
        byte[] xlsx = mockMvc
            .perform(get("/api/employees/export.xlsx?code.contains=ARV90").with(HR))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        List<String> cells = cells(xlsx);
        assertThat(cells).contains("ARV902").doesNotContain("ARV901", "ARV903");

        // Ô gợi ý hồ sơ khi gắn dòng lương: PayrollMappingIT.archivedProfilesNeverAttach (cần bản nháp import thật).

        // Ứng viên trưởng phòng (đúng truy vấn của màn phòng ban) và lưu phòng với trưởng phòng đã lưu trữ.
        assertThat(
            codes(
                json(get("/api/employees?personType.equals=NHAN_VIEN&status.equals=DANG_LAM_VIEC&code.contains=ARV90&size=1000").with(HR)),
                "$[*].code"
            )
        )
            .contains("ARV904")
            .doesNotContain("ARV903");
        mockMvc
            .perform(
                post("/api/departments")
                    .with(user("admin-arv").authorities(() -> "ROLE_ADMIN"))
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Phòng ARV 2\",\"active\":true,\"headId\":" + m.getId() + "}")
            )
            .andExpect(status().isBadRequest());

        // Lịch nghỉ tháng nghỉ việc.
        List<Integer> calendarIds = JsonPath.read(json(get("/api/leave/calendar?month=2026-09").with(HR)), "$.items[*].employeeId");
        assertThat(calendarIds).contains(b.getId().intValue()).doesNotContain(a.getId().intValue());

        // Số dư phép công ty.
        assertThat(codes(json(get("/api/leave/balances").with(HR)), "$[*].code")).contains("ARV904").doesNotContain("ARV903");

        // Hộp duyệt đơn: đơn đang chờ của người đã lưu trữ không hiện.
        LeaveRequest pendingA = leave(a, LeaveRequestStatus.CHO_DUYET, "2026-10-26", ApproverKind.BGD, null);
        List<Integer> inbox = JsonPath.read(json(get("/api/leave/requests/inbox").with(HR)), "$[*].id");
        assertThat(inbox).doesNotContain(pendingA.getId().intValue());

        // Người duyệt: không bao giờ là hồ sơ đã lưu trữ (M là quản lý trực tiếp và trưởng phòng của C).
        ApproverResolver.Approver approver = approverResolver.resolve(c, BigDecimal.ONE, false);
        assertThat(approver.employeeId()).isNotEqualTo(m.getId());
        assertThat(approver.kind()).isNotIn(ApproverKind.TRUONG_NHOM, ApproverKind.TRUONG_PHONG);
        assertThat(approverResolver.escalate(c, ApproverKind.TRUONG_NHOM).employeeId()).isNotEqualTo(m.getId());
        // Đơn cũ còn trỏ vào M: email nhắc duyệt không gửi cho hồ sơ đã lưu trữ.
        leave(c, LeaveRequestStatus.CHO_DUYET, "2026-10-27", ApproverKind.TRUONG_NHOM, m);
        // Đơn cấp BGĐ: tài khoản BGĐ gắn hồ sơ đã lưu trữ không nhận email, không được tính là giám đốc.
        User bgd = LeaveFixtures.user(userRepository, "arv-bgd");
        bgd.setEmail("arv-bgd@example.vn");
        bgd.setAuthorities(new java.util.HashSet<>(java.util.Set.of(authorityRepository.findById("ROLE_BGD").orElseThrow())));
        userRepository.saveAndFlush(bgd);
        Employee bgdProfile = LeaveFixtures.employee(employeeRepository, "ARV905", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        bgdProfile.setUser(bgd);
        archive(left(bgdProfile));
        leave(c, LeaveRequestStatus.CHO_DUYET, "2026-10-28", ApproverKind.BGD, null);
        assertThat(userRepository.findActiveWithAnyAuthority(List.of("ROLE_BGD"))).extracting(User::getLogin).doesNotContain("arv-bgd");
        Map<String, List<String>> mails = digest.byEmail();
        assertThat(mails).doesNotContainKey("arv-m@example.vn").doesNotContainKey("arv-bgd@example.vn");

        // Nhắc việc, số người đang làm trên Tổng quan: so sánh với lúc khôi phục. Biến động các tháng cũ (R4b): lưu trữ người đã nghỉ
        // không đổi số liệu cũ.
        String reminders = json(get("/api/reminders/upcoming?type=BIRTHDAY").with(HR));
        long headcount = ((Number) JsonPath.read(json(get("/api/dashboard").with(HR)), "$.headcount")).longValue();
        int leavers = septemberLeavers();

        unarchive(a);
        unarchive(m);
        assertThat(codes(reminders, "$[*].employeeCode")).doesNotContain("ARV903");
        assertThat(codes(json(get("/api/reminders/upcoming?type=BIRTHDAY").with(HR)), "$[*].employeeCode")).contains("ARV903");
        assertThat(((Number) JsonPath.read(json(get("/api/dashboard").with(HR)), "$.headcount")).longValue()).isEqualTo(headcount + 1);
        assertThat(septemberLeavers()).isEqualTo(leavers);
        archive(a);
        archive(m);

        // Bảng lương và phiếu lương kỳ cũ: còn cả A và B, A gắn cờ lưu trữ.
        String lines = json(get("/api/payroll/periods/" + PAYROLL_PERIOD + "/versions/1/lines").with(KT));
        assertThat(JsonPath.<List<Boolean>>read(lines, "$[?(@.employeeCode=='ARV901')].archived")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(lines, "$[?(@.employeeCode=='ARV902')].archived")).containsExactly(false);
        String slips = json(get("/api/payroll/periods/" + PAYROLL_PERIOD + "/payslips").with(KT));
        assertThat(JsonPath.<List<Boolean>>read(slips, "$[?(@.employeeCode=='ARV901')].archived")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(slips, "$[?(@.employeeCode=='ARV902')].archived")).containsExactly(false);

        // Tổng hợp kế toán tháng nghỉ việc: còn A, gắn cờ lưu trữ; M (không có dữ liệu) không hiện.
        String summary = json(get("/api/leave/accounting?month=2026-09").with(KT));
        assertThat(JsonPath.<List<Boolean>>read(summary, "$.rows[?(@.code=='ARV901')].archived")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(summary, "$.rows[?(@.code=='ARV902')].archived")).containsExactly(false);
        assertThat(JsonPath.<List<String>>read(summary, "$.rows[*].code")).doesNotContain("ARV903");
        // Tháng cũ không có số liệu (08/2026): người đã nghỉ rồi lưu trữ vẫn hiện vì còn làm việc tháng đó; hồ sơ tạo nhầm thì không.
        String august = json(get("/api/leave/accounting?month=2026-08").with(KT));
        assertThat(JsonPath.<List<Boolean>>read(august, "$.rows[?(@.code=='ARV901')].archived")).containsExactly(true);
        assertThat(JsonPath.<List<String>>read(august, "$.rows[*].code")).contains("ARV902").doesNotContain("ARV903");
    }

    private int septemberLeavers() throws Exception {
        List<Integer> v = JsonPath.read(json(get("/api/dashboard").with(HR)), "$.movement[?(@.month=='2026-09')].leavers");
        assertThat(v).hasSize(1);
        return v.get(0);
    }

    private String json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) throws Exception {
        return mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<String> codes(String body, String path) {
        return JsonPath.read(body, path);
    }

    private static List<String> cells(byte[] xlsx) throws Exception {
        List<String> out = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Row row : wb.getSheetAt(0)) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.STRING) {
                        out.add(cell.getStringCellValue());
                    }
                }
            }
        }
        return out;
    }

    private Employee left(Employee e) {
        e.setStatus(EmploymentStatus.DA_NGHI);
        e.setLastWorkingDay(LAST_DAY);
        e.setTerminationDate(LAST_DAY.plusDays(1));
        e.setTerminationType(TerminationType.TU_NGHI);
        return employeeRepository.saveAndFlush(e);
    }

    private void archive(Employee e) {
        e.setArchivedAt(Instant.parse("2026-10-20T03:00:00Z"));
        e.setArchivedBy("hr-arv");
        employeeRepository.saveAndFlush(e);
    }

    private void unarchive(Employee e) {
        e.setArchivedAt(null);
        e.setArchivedBy(null);
        employeeRepository.saveAndFlush(e);
    }

    private LeaveRequest leave(Employee e, LeaveRequestStatus st, String day, ApproverKind kind, Employee approver) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(e);
        r.setType(LeaveRequestType.PHEP_NAM);
        r.setFromDate(LocalDate.parse(day));
        r.setToDate(LocalDate.parse(day));
        r.setFirstPart(DayPart.CA_NGAY);
        r.setLastPart(DayPart.CA_NGAY);
        r.setDays(BigDecimal.ONE);
        r.setStatus(st);
        r.setApproverKind(kind);
        r.setApproverEmployee(approver);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        return requestRepository.saveAndFlush(r);
    }

    private void line(PayrollVersion v, Employee e, int row) {
        PayrollLineRecord l = new PayrollLineRecord();
        l.setVersion(v);
        l.setEmployee(e);
        l.setExcelRow(row);
        l.setFileCode(e.getCode());
        l.setFileName(e.getFullName());
        l.setMappedManually(false);
        l.setAmounts("{}");
        l.setThucNhan(new BigDecimal("10000000"));
        l.setTongChiPhi(new BigDecimal("12000000"));
        lineRepository.saveAndFlush(l);
    }
}
