package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.PoiSheetGrid;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.AttendanceSnapshot;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendanceSnapshotRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.pdf.PdfService;

/**
 * Xác nhận bản tính nháp thành phiên bản của kỳ: phiên bản nguồn TINH_TOAN, dòng lương, file Excel 36 cột, điều kiện xác nhận, duyệt
 * và phiếu lương dùng được phiên bản này, hai người cùng xác nhận chỉ tạo một phiên bản.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class PayrollCalcConfirmIT {

    private static final String P = "2026-03";
    private static final RequestPostProcessor KE_TOAN = user("ketoan-cf").authorities(() -> "ROLE_KE_TOAN");
    private static final RequestPostProcessor ADMIN = user("admin-cf").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor HR = user("hr-cf").authorities(() -> "ROLE_HR");
    private static final byte[] PDF = "%PDF-1.4 phieu".getBytes();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-calc-confirm-it");
        registry.add("application.storage.root", storage::toString);
    }

    @MockitoBean
    private PdfService pdfService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private AttendanceMonthRepository months;

    @Autowired
    private AttendanceSnapshotRepository snapshots;

    @Autowired
    private SalaryChangeRepository salaries;

    @Autowired
    private PayrollPeriodRepository periods;

    @Autowired
    private JdbcTemplate jdbc;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Autowired
    private PlatformTransactionManager txManager;

    private void closedMonth(String month) {
        AttendanceMonth m = new AttendanceMonth();
        m.setMonth(month);
        m.setStatus(AttendanceMonthStatus.DA_CHOT);
        months.saveAndFlush(m);
    }

    private Employee person(String code, PersonType type, String salaryFrom, long salary, String month, String work) {
        Employee e = employees.saveAndFlush(
            LeaveFixtures.employee(employees, code, type, LocalDate.of(2024, 1, 2)).fullName("Người thử " + code)
        );
        AttendanceSnapshot s = new AttendanceSnapshot();
        s.setMonth(month);
        s.setEmployeeId(e.getId());
        s.setStandardDays(new BigDecimal("22"));
        s.setWorkDays(new BigDecimal(work));
        s.setPaidLeaveDays(BigDecimal.ZERO);
        s.setHolidayDays(BigDecimal.ZERO);
        s.setTripDays(BigDecimal.ZERO);
        s.setSickDays(BigDecimal.ZERO);
        snapshots.saveAndFlush(s);
        if (salaryFrom != null) {
            salaries.saveAndFlush(
                new SalaryChange().employee(e).effectiveDate(LocalDate.parse(salaryFrom)).amount(salary).basis(SalaryBasis.GROSS)
            );
        }
        return e;
    }

    private Employee staff(String code, long salary) {
        return person(code, PersonType.NHAN_VIEN, "2025-01-01", salary, P, "22");
    }

    private String open(String period) throws Exception {
        return mockMvc
            .perform(post("/api/payroll/periods/" + period + "/calc").with(KE_TOAN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private static Number draftId(String draft) {
        return JsonPath.read(draft, "$.id");
    }

    private ResultActions confirm(Number id, String body, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(post("/api/payroll/calc/{id}/confirm", id).with(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static Number lineIdOf(String draft, String code) {
        List<Number> ids = JsonPath.read(draft, "$.lines[?(@.code=='" + code + "')].id");
        assertThat(ids).hasSize(1);
        return ids.get(0);
    }

    @Test
    void confirmCreatesCalcVersionWithLinesAndExcelFile() throws Exception {
        closedMonth(P);
        staff("NV9801", 20_000_000L);
        staff("NV9802", 30_000_000L);
        staff("NV9803", 25_000_000L);
        String draft = open(P);
        // Bỏ một dòng: không vào phiên bản.
        mockMvc
            .perform(delete("/api/payroll/calc/{d}/lines/{l}", draftId(draft), lineIdOf(draft, "NV9803")).with(KE_TOAN))
            .andExpect(status().isNoContent());
        mockMvc
            .perform(
                put("/api/payroll/calc/{d}/lines/{l}", draftId(draft), lineIdOf(draft, "NV9801"))
                    .with(KE_TOAN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"manual\":{\"THUONG_KPI\":5000000}}")
            )
            .andExpect(status().isOk());

        confirm(draftId(draft), "{\"warningsAcknowledged\":false}", KE_TOAN)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.period").value(P))
            .andExpect(jsonPath("$.version").value(1));

        em.flush();
        assertThat(
            jdbc.queryForObject(
                "select count(*) from payroll_line l join payroll_version v on v.id = l.version_id join payroll_period p on p.id = v.period_id where p.period = ?",
                Integer.class,
                P
            )
        ).isEqualTo(2);
        Map<String, Object> v = jdbc.queryForMap(
            "select v.source, v.import_id, v.file_name, v.rules_snapshot, v.issues_snapshot from payroll_version v join payroll_period p on p.id = v.period_id where p.period = ?",
            P
        );
        assertThat(v.get("source")).isEqualTo("TINH_TOAN");
        assertThat(v.get("import_id")).isNull();
        assertThat(v.get("file_name")).isEqualTo("bang-luong-2026-03-tinh-toan-v1.xlsx");
        assertThat((String) v.get("rules_snapshot"))
            .contains("calcSettings")
            .contains("personalDeduction");
        assertThat(
            jdbc.queryForObject(
                "select thuc_nhan from payroll_line l join payroll_version v on v.id = l.version_id join payroll_period p on p.id = v.period_id where p.period = ? and l.file_code = 'NV9801'",
                BigDecimal.class,
                P
            )
        ).isEqualByComparingTo("22530000");
        assertThat(jdbc.queryForObject("select count(*) from payroll_line l where l.file_code = 'NV9803'", Integer.class)).isZero();
        assertThat(
            jdbc.queryForObject("select status from payroll_calc_draft where id = ?", String.class, draftId(draft).longValue())
        ).isEqualTo("DA_XAC_NHAN");

        // File của phiên bản (endpoint tải hiện có) đọc lại đúng số.
        MvcResult file = mockMvc
            .perform(get("/api/payroll/periods/{p}/versions/1/file", P).with(KE_TOAN))
            .andExpect(status().isOk())
            .andReturn();
        ParsedPayroll parsed = new PayrollSheetParser().parse(
            PoiSheetGrid.read(new ByteArrayInputStream(file.getResponse().getContentAsByteArray()))
        );
        assertThat(parsed.periodInFile()).isPresent();
        assertThat(parsed.lines()).hasSize(2);
        assertThat(parsed.columns()).hasSize(PayrollField.values().length);
        assertThat(parsed.lines().get(0).employeeCode()).isEqualTo("NV9801");
        assertThat(parsed.lines().get(0).get(PayrollField.THUC_NHAN)).isEqualByComparingTo("22530000");
        assertThat(parsed.lines().get(1).get(PayrollField.LUONG_GOP)).isEqualByComparingTo("30000000");

        // Tính lại và xác nhận lần nữa: phiên bản 2.
        String again = open(P);
        assertThat(draftId(again).longValue()).isNotEqualTo(draftId(draft).longValue());
        confirm(draftId(again), "{}", KE_TOAN).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void errorOrUnacknowledgedWarningBlocksConfirm() throws Exception {
        closedMonth(P);
        // Không có mức lương → dòng lỗi.
        person("NV9811", PersonType.NHAN_VIEN, null, 0, P, "22");
        String draft = open(P);
        confirm(draftId(draft), "{\"warningsAcknowledged\":true}", KE_TOAN)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcnotready"));
        // Bỏ dòng lỗi rồi thêm một dòng có cảnh báo (lương bắt đầu muộn): phải tích xác nhận.
        mockMvc
            .perform(delete("/api/payroll/calc/{d}/lines/{l}", draftId(draft), lineIdOf(draft, "NV9811")).with(KE_TOAN))
            .andExpect(status().isNoContent());
        person("NV9812", PersonType.NHAN_VIEN, "2026-03-10", 20_000_000L, P, "22");
        mockMvc.perform(post("/api/payroll/calc/{id}/recalculate", draftId(draft)).with(KE_TOAN)).andExpect(status().isOk());
        confirm(draftId(draft), "{\"warningsAcknowledged\":false}", KE_TOAN)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcnotready"));
        assertThat(jdbc.queryForObject("select count(*) from payroll_version", Integer.class)).isZero();
        confirm(draftId(draft), "{\"warningsAcknowledged\":true}", KE_TOAN).andExpect(status().isCreated());
    }

    @Test
    void approvedPeriodAndSecondConfirmAreRejected() throws Exception {
        closedMonth(P);
        staff("NV9821", 20_000_000L);
        String draft = open(P);
        confirm(draftId(draft), "{}", KE_TOAN).andExpect(status().isCreated());
        confirm(draftId(draft), "{}", KE_TOAN)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.calcdraftclosed"));

        String second = open(P);
        PayrollPeriod period = periods.findByPeriod(P).orElseThrow();
        period.setStatus(PayrollPeriodStatus.DA_DUYET);
        periods.saveAndFlush(period);
        confirm(draftId(second), "{}", KE_TOAN)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.periodapproved"));
        assertThat(jdbc.queryForObject("select count(*) from payroll_version", Integer.class)).isEqualTo(1);
    }

    @Test
    void adminApprovesCalcVersionAndPayslipIsReadable() throws Exception {
        when(pdfService.toPdf(any(), anyString())).thenReturn(PDF);
        closedMonth(P);
        Employee e = staff("NV9831", 20_000_000L);
        String draft = open(P);
        confirm(draftId(draft), "{}", KE_TOAN).andExpect(status().isCreated());
        mockMvc.perform(post("/api/payroll/periods/{p}/approve", P).with(ADMIN)).andExpect(status().isOk());
        mockMvc
            .perform(get("/api/payroll/periods/{p}/versions/1/lines", P).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fileCode").value("NV9831"));
        mockMvc
            .perform(get("/api/payroll/periods/{p}/payslips/{id}/pdf", P, e.getId()).with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(PDF));
    }

    @Test
    void otherRolesForbidden() throws Exception {
        closedMonth(P);
        staff("NV9841", 20_000_000L);
        String draft = open(P);
        confirm(draftId(draft), "{}", HR).andExpect(status().isForbidden());
        confirm(draftId(draft), "{}", user("nv").authorities(() -> "ROLE_NHAN_VIEN")).andExpect(status().isForbidden());
    }

    /** Hai người bấm Xác nhận cùng lúc: dữ liệu tự commit, tự dọn. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentConfirmsCreateExactlyOneVersion() throws Exception {
        String month = "2026-04";
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Long employeeId = null;
        try {
            employeeId = tx.execute(s -> {
                closedMonth(month);
                return person("NV9851", PersonType.NHAN_VIEN, "2025-01-01", 20_000_000L, month, "22").getId();
            });
            String draft = open(month);
            Number id = draftId(draft);
            int threads = 2;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(
                    pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return confirm(id, "{}", KE_TOAN).andReturn().getResponse().getStatus();
                    })
                );
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : results) {
                codes.add(f.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();
            assertThat(codes).containsExactlyInAnyOrder(201, 400);
            assertThat(
                jdbc.queryForObject(
                    "select count(*) from payroll_version v join payroll_period p on p.id = v.period_id where p.period = ?",
                    Integer.class,
                    month
                )
            ).isEqualTo(1);
            assertThat(
                jdbc.queryForObject(
                    "select count(*) from payroll_line l join payroll_version v on v.id = l.version_id join payroll_period p on p.id = v.period_id where p.period = ?",
                    Integer.class,
                    month
                )
            ).isEqualTo(1);
        } finally {
            tx.executeWithoutResult(s -> {
                String byPeriod = "(select id from payroll_period where period = ?)";
                jdbc.update(
                    "delete from payroll_line where version_id in (select id from payroll_version where period_id in " + byPeriod + ")",
                    month
                );
                jdbc.update("delete from payroll_version where period_id in " + byPeriod, month);
                jdbc.update(
                    "delete from payroll_calc_draft_line where draft_id in (select id from payroll_calc_draft where period_id in " +
                        byPeriod +
                        ")",
                    month
                );
                jdbc.update("delete from payroll_calc_draft where period_id in " + byPeriod, month);
                jdbc.update("delete from payroll_period where period = ?", month);
                jdbc.update("delete from attendance_snapshot where month = ?", month);
                jdbc.update("delete from attendance_month where month = ?", month);
            });
            Long eid = employeeId;
            if (eid != null) {
                tx.executeWithoutResult(s -> {
                    jdbc.update("delete from salary_change where employee_id = ?", eid);
                    jdbc.update("delete from employee where id = ?", eid);
                });
            }
        }
    }
}
