package vn.doit.hr.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.doit.hr.employee.EmployeeIssue.Severity;
import vn.doit.hr.payroll.SheetGrid;

class EmployeeSheetParserTest {

    private static final List<Object> NAME_ID = row("Họ và tên", "Số CCCD");

    private final EmployeeSheetParser parser = new EmployeeSheetParser();

    private static List<Object> row(Object... cells) {
        return Arrays.asList(cells);
    }

    /** Lưới có tiêu đề ở dòng 1, kèm các dòng dữ liệu. */
    @SafeVarargs
    private static ListGrid grid(List<Object> header, List<Object>... data) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(header);
        rows.addAll(Arrays.asList(data));
        return new ListGrid(rows);
    }

    private static EmployeeIssue error(int row, String name, EmployeeField field, String message) {
        return new EmployeeIssue(Severity.ERROR, row, name, field, message);
    }

    @Test
    void readsColumnsInAnyOrder() {
        ParsedEmployees p = parser.parse(new ListGrid(List.of(
                row("DANH SÁCH NHÂN VIÊN"),
                row(),
                row("Số CCCD", "Ghi chú", "Họ và tên"),
                row("079123456789", "ghi", "Nguyễn Văn A"))));

        assertEquals(Map.of(EmployeeField.ID_NUMBER, 0, EmployeeField.NOTE, 1, EmployeeField.FULL_NAME, 2),
                p.columns());
        assertTrue(p.issues().isEmpty());
        assertEquals(1, p.rows().size());
        assertEquals(4, p.rows().get(0).excelRow());
        assertEquals("Nguyễn Văn A", p.rows().get(0).text(EmployeeField.FULL_NAME));
        assertEquals("ghi", p.rows().get(0).text(EmployeeField.NOTE));
    }

    @Test
    void missingRequiredColumnIsFileError() {
        ParsedEmployees p = parser.parse(grid(row("Họ và tên", "Ghi chú"), row("Nguyễn Văn A", "x")));

        assertTrue(p.rows().isEmpty());
        assertEquals(List.of(EmployeeIssue.fileError("Không tìm thấy cột \"Số CCCD\".")), p.issues());
    }

    @Test
    void noHeaderInFirstTenRows() {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(row("dòng " + i));
        }
        rows.add(row("Họ và tên", "Số CCCD"));
        ParsedEmployees p = parser.parse(new ListGrid(rows));

        assertTrue(p.rows().isEmpty());
        assertEquals(List.of(EmployeeIssue.fileError(
                "Không tìm thấy dòng tiêu đề có cột \"Họ và tên\". Sai mẫu file?")), p.issues());
    }

    @Test
    void unknownColumnIsFileWarning() {
        ParsedEmployees p = parser.parse(grid(row("Họ và tên", "Số CCCD", "Sở thích"),
                row("Nguyễn Văn A", "079123456789", "bơi")));

        assertEquals(1, p.rows().size());
        assertEquals(1, p.issues().size());
        EmployeeIssue w = p.issues().get(0);
        assertEquals(Severity.WARNING, w.severity());
        assertEquals(0, w.excelRow());
        assertTrue(w.message().contains("Sở thích"));
    }

    @Test
    void datesFromCellOrText() {
        ParsedEmployees p = parser.parse(grid(row("Họ và tên", "Số CCCD", "Ngày sinh"),
                row("A", "1", LocalDate.of(1990, 5, 1)),
                row("B", "2", "1/5/1990"),
                row("C", "3", "31/02/1990")));

        assertEquals(LocalDate.of(1990, 5, 1), p.rows().get(0).date(EmployeeField.BIRTH_DATE));
        assertEquals(LocalDate.of(1990, 5, 1), p.rows().get(1).date(EmployeeField.BIRTH_DATE));
        assertFalse(p.rows().get(2).values().containsKey(EmployeeField.BIRTH_DATE));
        assertEquals(List.of(error(4, "C", EmployeeField.BIRTH_DATE,
                "Ngày không hợp lệ: \"31/02/1990\" (dạng dd/MM/yyyy)")), p.issues());
    }

    @Test
    void numericIdNumberReadAsDigits() {
        ParsedEmployees p = parser.parse(grid(NAME_ID,
                row("A", new BigDecimal("1.23456789012E+11")),
                row("B", new BigDecimal("79123456789"))));

        assertEquals("123456789012", p.rows().get(0).text(EmployeeField.ID_NUMBER));
        assertEquals("079123456789", p.rows().get(1).text(EmployeeField.ID_NUMBER));
        assertEquals(List.of(new EmployeeIssue(Severity.WARNING, 3, "B", EmployeeField.ID_NUMBER,
                "Đã thêm số 0 ở đầu số CCCD, kiểm tra lại")), p.issues());
    }

    @Test
    void choicesIgnoreCaseAccentAndSpaces() {
        ParsedEmployees p = parser.parse(grid(row("Họ và tên", "Số CCCD", "Giới tính", "Loại hồ sơ"),
                row("A", "1", " nữ ", "thuc tap"),
                row("B", "2", "Nam", "Khác loại")));

        assertEquals("Nữ", p.rows().get(0).text(EmployeeField.GENDER));
        assertEquals("Thực tập", p.rows().get(0).text(EmployeeField.PERSON_TYPE));
        assertEquals("Nam", p.rows().get(1).text(EmployeeField.GENDER));
        assertEquals(List.of(error(3, "B", EmployeeField.PERSON_TYPE,
                "Giá trị không có trong danh sách: Nhân viên, Thực tập, HĐ dịch vụ")), p.issues());
    }

    @Test
    void integersAndLengths() {
        ParsedEmployees p = parser.parse(grid(row("Họ và tên", "Số CCCD", "Số người phụ thuộc"),
                row("A", "1", "2"),
                row("B", "2", new BigDecimal("2")),
                row("C", "3", "-1"),
                row("D", "4", "1,5"),
                row("x".repeat(101), "5", null)));

        assertEquals(2L, p.rows().get(0).integer(EmployeeField.DEPENDANTS));
        assertEquals(2L, p.rows().get(1).integer(EmployeeField.DEPENDANTS));
        assertEquals(List.of(
                error(4, "C", EmployeeField.DEPENDANTS, "Phải là số nguyên ≥ 0"),
                error(5, "D", EmployeeField.DEPENDANTS, "Phải là số nguyên ≥ 0"),
                error(6, "x".repeat(101), EmployeeField.FULL_NAME, "Dài quá 100 ký tự")), p.issues());
    }

    @Test
    void partiallyFilledRowIsKept() {
        ParsedEmployees p = parser.parse(grid(row("Họ và tên", "Số CCCD", "Ghi chú"),
                row(null, null, "chỉ có ghi chú"),
                row(null, " ", null),
                row("A", "1", null)));

        assertEquals(2, p.rows().size());
        assertEquals(2, p.rows().get(0).excelRow());
        assertEquals(4, p.rows().get(1).excelRow());
        assertEquals(Map.of(EmployeeField.NOTE, "chỉ có ghi chú"), p.rows().get(0).values());
    }

    @Test
    void tooManyRows() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(NAME_ID);
        for (int i = 0; i < EmployeeSheetParser.MAX_ROWS + 1; i++) {
            rows.add(row("Người " + i, String.valueOf(i)));
        }
        ParsedEmployees p = parser.parse(new ListGrid(rows));

        assertTrue(p.rows().isEmpty());
        assertEquals(List.of(EmployeeIssue.fileError("File có 2001 dòng, tối đa 2000 dòng mỗi lần nhập")),
                p.issues());
    }

    @Test
    void blankRowsDoNotCountTowardLimit() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(NAME_ID);
        for (int i = 0; i < EmployeeSheetParser.MAX_ROWS; i++) {
            rows.add(row("Người " + i, String.valueOf(i)));
            rows.add(row());
        }
        ParsedEmployees p = parser.parse(new ListGrid(rows));

        assertTrue(p.issues().isEmpty());
        assertEquals(EmployeeSheetParser.MAX_ROWS, p.rows().size());
    }

    @Test
    void cellErrorIsRowError() {
        ParsedEmployees p = parser.parse(grid(NAME_ID, row("A", new SheetGrid.CellError("#REF!"))));

        assertEquals(List.of(error(2, "A", EmployeeField.ID_NUMBER, "Ô bị lỗi công thức #REF!")), p.issues());
    }
}
