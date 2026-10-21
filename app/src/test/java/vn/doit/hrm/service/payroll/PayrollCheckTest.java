package vn.doit.hrm.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.PoiSheetGrid;
import vn.doit.hr.payroll.RuleBook;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.service.payroll.dto.IssueDTO;
import vn.doit.hrm.service.payroll.dto.RowDTO;

/** Kiểm tra file bảng lương mẫu (bản ẩn danh) với danh bạ giả và các dòng gắn tay. */
class PayrollCheckTest {

    private static ParsedPayroll parsed;
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final Map<String, Employee> BY_CODE = new HashMap<>();

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = PayrollCheckTest.class.getResourceAsStream("/payroll/bang-luong-mau-an-danh.xlsx")) {
            parsed = new PayrollSheetParser().parse(PoiSheetGrid.read(in));
        }
        long id = 1;
        for (String[] p : PayrollFixture.PROFILES) {
            BY_CODE.put(p[0], new Employee().id(id++).code(p[0]).fullName(p[1]));
        }
    }

    private static PayrollCheck.Result run(Map<Integer, Employee> mappings) {
        return PayrollCheck.run(parsed, JUNE, mappings, code -> Optional.ofNullable(BY_CODE.get(code)), RuleBook.defaults());
    }

    private static List<Integer> errorRows(PayrollCheck.Result r) {
        return r.issues().stream().filter(i -> i.severity().equals("ERROR")).map(IssueDTO::excelRow).distinct().sorted().toList();
    }

    @Test
    void unmappedFileHasSevenErrors() {
        PayrollCheck.Result r = run(Map.of());
        assertThat(r.rows()).hasSize(14);
        assertThat(r.columns()).hasSize(36);
        assertThat(r.missingRequired()).isEmpty();
        assertThat(r.errorCount()).isEqualTo(7);
        assertThat(errorRows(r)).containsExactly(14, 15, 18, 19, 21, 22, 23);
        RowDTO row10 = r.rows().getFirst();
        assertThat(row10.excelRow()).isEqualTo(10);
        assertThat(row10.employeeCode()).isEqualTo("DOIT2401");
        assertThat(row10.mappedManually()).isFalse();
        // Dòng trùng mã chưa gắn: không tự gắn hồ sơ nào.
        assertThat(r.rows().stream().filter(x -> x.excelRow() == 14).findFirst().orElseThrow().employeeId()).isNull();
    }

    @Test
    void mappingRow18RemovesDuplicateOnRows14And18() {
        PayrollCheck.Result r = run(Map.of(18, BY_CODE.get("DOIT2515")));
        assertThat(errorRows(r)).containsExactly(15, 19, 21, 22, 23);
        assertThat(r.errorCount()).isEqualTo(5);
        RowDTO row18 = r.rows().stream().filter(x -> x.excelRow() == 18).findFirst().orElseThrow();
        assertThat(row18.employeeCode()).isEqualTo("DOIT2515");
        assertThat(row18.fileCode()).isEqualTo("DOIT2508");
        assertThat(row18.mappedManually()).isTrue();
        // Dòng 14 giờ là người duy nhất mang mã DOIT2508 → tự khớp.
        assertThat(r.rows().stream().filter(x -> x.excelRow() == 14).findFirst().orElseThrow().employeeCode()).isEqualTo("DOIT2508");
    }

    @Test
    void fullyMappedHasOnlyThreeWarnings() {
        Map<Integer, Employee> m = new HashMap<>();
        m.put(18, BY_CODE.get("DOIT2515"));
        m.put(19, BY_CODE.get("DOIT2516"));
        m.put(21, new Employee().id(100L).code("TT2601").fullName("Mai Văn Phúc"));
        m.put(22, new Employee().id(101L).code("HDDV1").fullName("Tạ Văn Quý"));
        m.put(23, new Employee().id(102L).code("HDDV2").fullName("Hồ Thị Sen"));
        PayrollCheck.Result r = run(m);
        assertThat(r.errorCount()).isZero();
        assertThat(r.warningCount()).isEqualTo(3);
        assertThat(r.issues()).filteredOn(i -> i.severity().equals("WARNING")).extracting(IssueDTO::excelRow).containsExactlyInAnyOrder(0, 12, 16);
        assertThat(r.rows()).allMatch(x -> x.employeeId() != null);
    }
}
