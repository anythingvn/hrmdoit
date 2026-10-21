package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.doit.hrm.web.rest.PayrollFlow.KE_TOAN;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.payroll.PayrollFixture;
import vn.doit.hrm.service.payroll.PayrollTestData;

/** Trang Tổng quan và báo cáo Excel: chi phí lương chỉ Quản trị và Kế toán thấy. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(PayrollTestData.class)
class DashboardResourceIT {

    private static final RequestPostProcessor HR = user("hr-db").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor BGD = user("bgd-db").authorities(() -> "ROLE_BGD");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-dashboard-it");
        registry.add("application.storage.root", storage::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollTestData data;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private TransactionTemplate tx;

    private final List<Long> extra = new ArrayList<>();
    private int headcount0;
    private int probation0;
    private int expiring0;

    @BeforeEach
    void seed() throws Exception {
        clean();
        String before = mockMvc.perform(get("/api/dashboard").with(KE_TOAN)).andReturn().getResponse().getContentAsString();
        headcount0 = JsonPath.read(before, "$.headcount");
        probation0 = JsonPath.read(before, "$.onProbation");
        expiring0 = JsonPath.read(before, "$.contractsExpiring30");

        Map<String, Long> ids = new HashMap<>(data.seedProfiles());
        ids.put("TT2601", data.seedService("TT2601", "Mai Văn Phúc"));
        ids.put("HDDV9001", data.seedService("HDDV9001", "Tạ Văn Quý"));
        ids.put("HDDV9002", data.seedService("HDDV9002", "Hồ Thị Sen"));
        Integer draft = PayrollFlow.readyDraft(mockMvc, "2026-06", ids);
        mockMvc.perform(post("/api/payroll/imports/{id}/confirm", draft).with(KE_TOAN)).andExpect(status().isCreated());

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        tx.executeWithoutResult(s -> {
            Employee probation = employee("DB-TV", "Người Thử Việc", today.minusDays(10));
            contractRepository.save(
                new EmploymentContract().type(ContractType.HDTV).startDate(today.minusDays(10)).endDate(today.plusDays(50)).status(ContractStatus.DA_KY).employee(probation)
            );
            Employee expiring = employee("DB-HD", "Người Sắp Hết HĐ", today.minusMonths(12));
            contractRepository.save(
                new EmploymentContract()
                    .contractNo("HĐ-DB")
                    .type(ContractType.HDLD_XDTH)
                    .termMonths(12)
                    .startDate(today.minusMonths(12).plusDays(20))
                    .endDate(today.plusDays(20))
                    .status(ContractStatus.DA_KY)
                    .employee(expiring)
            );
        });
    }

    @AfterEach
    void clean() {
        tx.executeWithoutResult(s -> extra.forEach(id -> contractRepository.findByEmployeeId(id).forEach(contractRepository::delete)));
        extra.clear();
        data.clean();
    }

    @Test
    void dashboardForAccountantHasCost() throws Exception {
        mockMvc
            .perform(get("/api/dashboard").with(KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headcount").value(headcount0 + PayrollFixture.PROFILES.length + 2))
            .andExpect(jsonPath("$.onProbation").value(probation0 + 1))
            .andExpect(jsonPath("$.contractsExpiring30").value(expiring0 + 1))
            .andExpect(jsonPath("$.payrollCost.period").value("2026-06"))
            .andExpect(jsonPath("$.payrollCost.status").value("NHAP"))
            .andExpect(jsonPath("$.payrollCost.totalCost").value(PayrollFixture.TOTAL_COST))
            .andExpect(jsonPath("$.movement.length()").value(12));
    }

    @Test
    void dashboardWithoutCostForHrAndBoard() throws Exception {
        for (RequestPostProcessor who : List.of(HR, BGD)) {
            mockMvc
                .perform(get("/api/dashboard").with(who))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").exists())
                .andExpect(jsonPath("$.payrollCost").doesNotExist());
        }
        mockMvc.perform(get("/api/dashboard").with(user("nv").authorities(() -> "ROLE_NHAN_VIEN"))).andExpect(status().isForbidden());
    }

    @Test
    void reportForAccountantHasCostSheet() throws Exception {
        try (XSSFWorkbook wb = report(KE_TOAN, "2026-01", "2026-12")) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(4);
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            Row total = findRow(wb.getSheet("Chi phí lương"), "Cả kỳ");
            Cell c = total.getCell(10);
            double value = c.getCellType() == CellType.FORMULA ? ev.evaluate(c).getNumberValue() : c.getNumericCellValue();
            assertThat(value).isEqualTo(PayrollFixture.TOTAL_COST);
        }
    }

    @Test
    void reportForHrHasNoCost() throws Exception {
        try (XSSFWorkbook wb = report(HR, "2026-01", "2026-12")) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            for (Sheet s : wb) {
                for (Row r : s) {
                    for (Cell c : r) {
                        if (c.getCellType() == CellType.NUMERIC) {
                            assertThat(c.getNumericCellValue()).isNotEqualTo((double) PayrollFixture.TOTAL_COST);
                        } else if (c.getCellType() == CellType.STRING) {
                            assertThat(c.getStringCellValue()).doesNotContain("245.709.000");
                        }
                    }
                }
            }
        }
        mockMvc.perform(get("/api/reports/hr.xlsx").with(HR)).andExpect(status().isOk());
    }

    @Test
    void reportRangeAndAccess() throws Exception {
        mockMvc
            .perform(get("/api/reports/hr.xlsx").param("from", "2026-12").param("to", "2026-01").with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.reportrangeinvalid"));
        mockMvc
            .perform(get("/api/reports/hr.xlsx").param("from", "2020-01").param("to", "2026-12").with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.reportrangeinvalid"));
        mockMvc.perform(get("/api/reports/hr.xlsx").with(user("nv").authorities(() -> "ROLE_NHAN_VIEN"))).andExpect(status().isForbidden());
    }

    private XSSFWorkbook report(RequestPostProcessor who, String from, String to) throws Exception {
        byte[] bytes = mockMvc
            .perform(get("/api/reports/hr.xlsx").param("from", from).param("to", to).param("groupBy", "DEPARTMENT").with(who))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static Row findRow(Sheet s, String firstCell) {
        for (Row r : s) {
            Cell c = r.getCell(0);
            if (c != null && c.getCellType() == CellType.STRING && firstCell.equals(c.getStringCellValue())) {
                return r;
            }
        }
        throw new AssertionError("Không thấy dòng \"" + firstCell + "\" trong sheet " + s.getSheetName());
    }

    private Employee employee(String code, String name, LocalDate joinDate) {
        Employee e = employeeRepository.save(
            new Employee().code(code).fullName(name).personType(PersonType.NHAN_VIEN).status(EmploymentStatus.DANG_LAM_VIEC).dependants(0).joinDate(joinDate)
        );
        extra.add(e.getId());
        data.track(e.getId());
        return e;
    }
}
