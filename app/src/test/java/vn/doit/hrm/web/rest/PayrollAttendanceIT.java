package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.PayrollTemplateWriter;
import vn.doit.hr.payroll.PayrollTemplateWriter.PrefillRow;
import vn.doit.hr.payroll.PoiSheetGrid;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.AttendanceSnapshot;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendanceSnapshotRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;

/**
 * Ngày công trong bảng lương: file bảng lương điền sẵn ngày công từ bảng công đã chốt, đối chiếu ngày công khi nhập, cảnh báo khi
 * duyệt kỳ lương mà bảng công chưa chốt. Hôm nay: 10/12/2026.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(PayrollAttendanceIT.FixedClockConfig.class)
class PayrollAttendanceIT {

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-12-10T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final RequestPostProcessor KE_TOAN = user("kt-pa").authorities(() -> "ROLE_KE_TOAN");
    private static final RequestPostProcessor QUAN_TRI = user("qt-pa").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor HR = user("hr-pa").authorities(() -> "ROLE_HR");
    private static final String NOT_CLOSED = "Bảng công tháng này chưa chốt";
    private static final YearMonth NOV = YearMonth.of(2026, 11);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private AttendanceMonthRepository monthRepository;

    @Autowired
    private AttendanceSnapshotRepository snapshotRepository;

    private Employee nv; // nhân viên, có ảnh chụp 21 / 20,5
    private Employee nv2; // nhân viên, có ảnh chụp 21 / 21
    private Employee intern; // thực tập, không có ảnh chụp
    private Employee service; // HĐ dịch vụ, không có ảnh chụp
    private Employee gone; // thực tập đã nghỉ trước tháng 11: không có dòng

    @BeforeEach
    void init() {
        nv2 = LeaveFixtures.employee(employeeRepository, "PA0002", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        nv = LeaveFixtures.employee(employeeRepository, "PA0001", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        nv.setPosition("Lập trình viên");
        employeeRepository.saveAndFlush(nv);
        intern = LeaveFixtures.employee(employeeRepository, "PA0003", PersonType.THUC_TAP, LocalDate.of(2026, 9, 1));
        service = LeaveFixtures.employee(employeeRepository, "PA0004", PersonType.DICH_VU, LocalDate.of(2026, 1, 5));
        gone = LeaveFixtures.employee(employeeRepository, "PA0005", PersonType.THUC_TAP, LocalDate.of(2026, 1, 5));
        gone.setStatus(EmploymentStatus.DA_NGHI);
        gone.setLastWorkingDay(LocalDate.of(2026, 10, 31));
        employeeRepository.saveAndFlush(gone);
        contract(nv, ContractType.HDTV, LocalDate.of(2024, 1, 2));
        contract(nv, ContractType.HDLD_XDTH, LocalDate.of(2024, 3, 2));
        contract(nv, ContractType.HDLD_KXDTH, LocalDate.of(2026, 3, 2));
    }

    private void contract(Employee e, ContractType type, LocalDate start) {
        EmploymentContract c = new EmploymentContract();
        c.setEmployee(e);
        c.setType(type);
        c.setStartDate(start);
        c.setStatus(ContractStatus.DA_KY);
        contractRepository.saveAndFlush(c);
    }

    private void close(YearMonth m, Map<Employee, String[]> days) {
        AttendanceMonth row = new AttendanceMonth();
        row.setMonth(m.toString());
        row.setStatus(AttendanceMonthStatus.DA_CHOT);
        row.setClosedBy("hr-pa");
        row.setClosedAt(Instant.parse("2026-12-05T03:00:00Z"));
        monthRepository.saveAndFlush(row);
        days.forEach((e, d) -> {
            AttendanceSnapshot s = new AttendanceSnapshot();
            s.setMonth(m.toString());
            s.setEmployeeId(e.getId());
            s.setStandardDays(new BigDecimal(d[0]));
            s.setActualDays(new BigDecimal(d[1]));
            snapshotRepository.saveAndFlush(s);
        });
    }

    private void closeNov() {
        close(NOV, Map.of(nv, new String[] { "21.00", "20.50" }, nv2, new String[] { "21.00", "21.00" }));
    }

    private static PrefillRow row(Employee e, String standard, String actual) {
        return new PrefillRow(
            e.getCode(),
            e.getFullName(),
            null,
            null,
            standard == null ? null : new BigDecimal(standard),
            actual == null ? null : new BigDecimal(actual)
        );
    }

    private static byte[] file(List<PrefillRow> rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PayrollTemplateWriter().write(out, rows);
        return out.toByteArray();
    }

    private String upload(String period, byte[] content) throws Exception {
        return mockMvc
            .perform(multipart("/api/payroll/imports").file(PayrollImportResourceIT.file("bang-luong.xlsx", content)).param("period", period).with(KE_TOAN))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    @Test
    void templateNeedsClosedMonth() throws Exception {
        mockMvc
            .perform(get("/api/payroll/imports/template.xlsx").param("period", "2026-11").with(KE_TOAN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.attendancenotclosed"));
        // Kỳ sai dạng
        mockMvc
            .perform(get("/api/payroll/imports/template.xlsx").param("period", "11/2026").with(KE_TOAN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.periodinvalid"));
        // HR không vào được bảng lương
        mockMvc.perform(get("/api/payroll/imports/template.xlsx").param("period", "2026-11").with(HR)).andExpect(status().isForbidden());
        // File mẫu trống vẫn tải được như cũ
        mockMvc
            .perform(get("/api/payroll/imports/template.xlsx").with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("mau-bang-luong.xlsx")));
    }

    @Test
    void templatePrefilledFromSnapshot() throws Exception {
        closeNov();
        // Hồ sơ đã lưu trữ có ảnh chụp: không có dòng trong file (khi import sẽ luôn báo lỗi hồ sơ đã lưu trữ) (R20).
        Employee archivedNv = LeaveFixtures.employee(employeeRepository, "PA0006", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        archivedNv.setStatus(EmploymentStatus.DA_NGHI);
        archivedNv.setLastWorkingDay(LocalDate.of(2026, 11, 10));
        archivedNv.setArchivedAt(Instant.parse("2026-11-20T00:00:00Z"));
        employeeRepository.saveAndFlush(archivedNv);
        AttendanceSnapshot as = new AttendanceSnapshot();
        as.setMonth("2026-11");
        as.setEmployeeId(archivedNv.getId());
        as.setStandardDays(new BigDecimal("21"));
        as.setActualDays(new BigDecimal("7"));
        snapshotRepository.saveAndFlush(as);
        byte[] xlsx = mockMvc
            .perform(get("/api/payroll/imports/template.xlsx").param("period", "2026-11").with(QUAN_TRI))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("bang-luong-2026-11.xlsx")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        ParsedPayroll parsed = new PayrollSheetParser().parse(PoiSheetGrid.read(new ByteArrayInputStream(xlsx)));
        assertThat(parsed.periodInFile()).contains(NOV);
        List<String> codes = parsed.lines().stream().map(PayrollLine::employeeCode).filter(c -> c.startsWith("PA")).toList();
        assertThat(codes).containsExactly("PA0001", "PA0002", "PA0003", "PA0004");

        PayrollLine first = line(parsed, "PA0001");
        assertThat(first.fullName()).isEqualTo("Người thử PA0001");
        assertThat(first.position()).isEqualTo("Lập trình viên");
        // Ngày ký HĐLĐ đầu tiên (bỏ qua HĐ thử việc)
        assertThat(first.contractStartDate()).isEqualTo(LocalDate.of(2024, 3, 2));
        assertThat(first.amounts().get(PayrollField.NGAY_CONG_CHUAN)).isEqualByComparingTo("21");
        assertThat(first.amounts().get(PayrollField.NGAY_CONG_THUC_TE)).isEqualByComparingTo("20.5");

        PayrollLine tt = line(parsed, "PA0003");
        assertThat(tt.amounts()).doesNotContainKeys(PayrollField.NGAY_CONG_CHUAN, PayrollField.NGAY_CONG_THUC_TE);
        PayrollLine dv = line(parsed, "PA0004");
        assertThat(dv.amounts()).doesNotContainKeys(PayrollField.NGAY_CONG_CHUAN, PayrollField.NGAY_CONG_THUC_TE);
    }

    private static PayrollLine line(ParsedPayroll p, String code) {
        Optional<PayrollLine> l = p.lines().stream().filter(x -> code.equals(x.employeeCode())).findFirst();
        assertThat(l).as(code).isPresent();
        return l.orElseThrow();
    }

    @Test
    void importWarnsOnMismatch() throws Exception {
        closeNov();
        String body = upload(
            "2026-11",
            file(List.of(row(nv, "21", "22"), row(nv2, "21", "21"), row(intern, "21", "15"), row(service, null, null)))
        );
        List<Map<String, Object>> dayIssues = JsonPath.read(body, "$.issues[?(@.message =~ /.*bảng công.*/)]");
        assertThat(dayIssues).hasSize(1);
        Map<String, Object> issue = dayIssues.get(0);
        assertThat(issue.get("severity")).isEqualTo("WARNING");
        assertThat(issue.get("employeeCode")).isEqualTo("PA0001");
        assertThat(issue.get("field")).isEqualTo("NGAY_CONG_THUC_TE");
        assertThat(issue.get("message")).isEqualTo("File ghi 22, bảng công đã chốt 20,5");
        assertThat((Integer) JsonPath.read(body, "$.errorCount")).isZero();
    }

    @Test
    void importWarnsOnBlankDaysWhenClosed() throws Exception {
        closeNov();
        String body = upload("2026-11", file(List.of(row(nv, "21", null), row(nv2, "21", "21"))));
        List<String> messages = JsonPath.read(body, "$.issues[?(@.employeeCode == 'PA0001')].message");
        assertThat(messages).contains("File để trống, bảng công đã chốt 20,5");
    }

    @Test
    void importWarnsWhenNotClosed() throws Exception {
        String body = upload("2026-10", file(List.of(row(nv, "22", "22"))));
        List<Map<String, Object>> general = JsonPath.read(body, "$.issues[?(@.message == '" + NOT_CLOSED + "')]");
        assertThat(general).hasSize(1);
        assertThat(general.get(0).get("severity")).isEqualTo("WARNING");
        assertThat(general.get(0).get("excelRow")).isEqualTo(0);

        // Tháng đã chốt: không còn cảnh báo chung
        closeNov();
        mockMvc
            .perform(multipart("/api/payroll/imports").file(PayrollImportResourceIT.file("b.xlsx", file(List.of(row(nv, "21", "20.5"))))).param("period", "2026-11").with(KE_TOAN))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.issues[*].message", not(hasItem(NOT_CLOSED))));
    }

    private void confirmedVersion(String period, byte[] content) throws Exception {
        Integer id = JsonPath.read(upload(period, content), "$.id");
        mockMvc.perform(post("/api/payroll/imports/{id}/acknowledge-warnings", id).with(KE_TOAN)).andExpect(status().isOk());
        mockMvc.perform(post("/api/payroll/imports/{id}/confirm", id).with(KE_TOAN)).andExpect(status().isCreated());
    }

    @Test
    void approveWarnsWhenNotClosed() throws Exception {
        confirmedVersion("2026-10", file(List.of(row(nv, "22", "22"))));
        mockMvc
            .perform(post("/api/payroll/periods/2026-10/approve").with(QUAN_TRI))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_DUYET"))
            .andExpect(jsonPath("$.warnings").value(org.hamcrest.Matchers.contains(NOT_CLOSED)));
    }

    @Test
    void approveWithoutWarningWhenClosed() throws Exception {
        closeNov();
        confirmedVersion("2026-11", file(List.of(row(nv, "21", "20.5"), row(nv2, "21", "21"))));
        mockMvc
            .perform(post("/api/payroll/periods/2026-11/approve").with(QUAN_TRI))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_DUYET"))
            .andExpect(jsonPath("$.warnings").isEmpty());
    }
}
