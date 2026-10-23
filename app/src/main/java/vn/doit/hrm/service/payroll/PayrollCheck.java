package vn.doit.hrm.service.payroll;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.poi.ss.util.CellReference;
import vn.doit.hr.payroll.EmployeeDirectory;
import vn.doit.hr.payroll.Issue;
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollValidator;
import vn.doit.hr.payroll.RuleBook;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.InsuranceExemption;
import vn.doit.hrm.service.EmployeeCodes;
import vn.doit.hrm.service.payroll.dto.ColumnDTO;
import vn.doit.hrm.service.payroll.dto.IssueDTO;
import vn.doit.hrm.service.payroll.dto.RowDTO;

/**
 * Kiểm tra một file bảng lương đã đọc, có tính các dòng Kế toán đã gắn tay: dòng gắn tay được thay mã trong file bằng mã của hồ
 * sơ đã gắn trước khi chạy {@link PayrollValidator}, nên lỗi trùng mã / thiếu mã của dòng đó biến mất và việc so tên, đối chiếu
 * luật dựa trên hồ sơ đã gắn.
 */
public final class PayrollCheck {

    /** Kết quả kiểm tra (xem {@link vn.doit.hrm.service.payroll.dto.ImportCheckDTO}). */
    public record Result(
        List<ColumnDTO> columns,
        List<String> missingRequired,
        List<RowDTO> rows,
        List<IssueDTO> issues,
        int errorCount,
        int warningCount,
        /** Dòng Excel → hồ sơ đã giải (để xác nhận thành phiên bản). */
        Map<Integer, Employee> resolved,
        List<PayrollLine> lines
    ) {}

    /** Ngày công của một người trong ảnh chụp bảng công đã chốt. */
    public record Days(BigDecimal standard, BigDecimal actual) {}

    /**
     * Bảng công của kỳ: {@code closed} = tháng đã chốt; {@code byEmployee} = ảnh chụp theo id hồ sơ (rỗng khi chưa chốt; thực tập, HĐ
     * dịch vụ không có).
     */
    public record Attendance(boolean closed, Map<Long, Days> byEmployee) {}

    /** Cảnh báo chung khi nhập / duyệt bảng lương mà bảng công tháng đó chưa chốt. */
    public static final String ATTENDANCE_NOT_CLOSED = "Bảng công tháng này chưa chốt";

    /** Mã trong file (hoặc dòng gắn tay) trỏ vào hồ sơ đã lưu trữ. */
    public static final String ARCHIVED_MESSAGE = "Hồ sơ đã lưu trữ — khôi phục trước khi import";

    private PayrollCheck() {}

    /** Kiểm tra không đối chiếu bảng công. */
    public static Result run(
        ParsedPayroll parsed,
        YearMonth period,
        Map<Integer, Employee> mappings,
        Function<String, Optional<Employee>> byCode,
        RuleBook book
    ) {
        return run(parsed, period, mappings, byCode, book, null);
    }

    /**
     * Kiểm tra kèm đối chiếu bảng công ({@code attendance} null: bỏ qua). Tháng chưa chốt: một cảnh báo chung. Tháng đã chốt: dòng của
     * người có ảnh chụp mà "Số ngày công chuẩn" / "Ngày công thực tế" khác ảnh chụp nhận cảnh báo "File ghi X, bảng công đã chốt Y".
     */
    public static Result run(
        ParsedPayroll parsed,
        YearMonth period,
        Map<Integer, Employee> mappings,
        Function<String, Optional<Employee>> byCode,
        RuleBook book,
        Attendance attendance
    ) {
        List<PayrollLine> lines = new ArrayList<>();
        Map<String, Employee> mappedByCode = new HashMap<>();
        for (PayrollLine l : parsed.lines()) {
            Employee m = mappings.get(l.excelRow());
            if (m != null) {
                mappedByCode.put(m.getCode(), m);
                lines.add(new PayrollLine(l.excelRow(), m.getCode(), l.position(), l.fullName(), l.contractStartDate(), l.amounts()));
            } else {
                lines.add(l);
            }
        }
        Map<String, Integer> codeCount = new HashMap<>();
        lines.stream().filter(PayrollLine::hasCode).forEach(l -> codeCount.merge(norm(l.employeeCode()), 1, Integer::sum));
        Function<String, Optional<Employee>> lookup = code ->
            Optional.ofNullable(mappedByCode.get(code)).or(() -> byCode.apply(norm(code)));

        ParsedPayroll rewritten = new ParsedPayroll(parsed.periodInFile(), parsed.columns(), lines, parsed.issues());
        List<Issue> all = new ArrayList<>(parsed.issues());
        if (!parsed.lines().isEmpty() || parsed.issues().isEmpty()) {
            EmployeeDirectory directory = new EmployeeDirectory() {
                @Override
                public Optional<String> findNameByCode(String code) {
                    return lookup.apply(code).map(Employee::getFullName);
                }

                @Override
                public Optional<String> insuranceExemption(String code) {
                    return lookup.apply(code).map(Employee::getInsuranceExemption).map(InsuranceExemption::label);
                }
            };
            all.addAll(new PayrollValidator(book).validate(rewritten, period, directory));
        }
        if (attendance != null && !attendance.closed()) {
            all.add(new Issue(Issue.Severity.WARNING, 0, null, null, ATTENDANCE_NOT_CLOSED));
        }

        List<RowDTO> rows = new ArrayList<>();
        Map<Integer, Employee> resolved = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            PayrollLine original = parsed.lines().get(i);
            PayrollLine l = lines.get(i);
            Employee mapped = mappings.get(l.excelRow());
            Employee e = mapped;
            if (e == null && l.hasCode() && codeCount.get(norm(l.employeeCode())) == 1) {
                e = lookup.apply(l.employeeCode()).orElse(null);
            }
            if (e != null && e.isArchived()) {
                // Hồ sơ đã lưu trữ không nhận dòng lương mới: báo lỗi, không gắn.
                all.add(new Issue(Issue.Severity.ERROR, l.excelRow(), original.employeeCode(), null, ARCHIVED_MESSAGE));
                e = null;
            }
            if (e != null) {
                resolved.put(l.excelRow(), e);
                Days days = attendance == null || !attendance.closed() ? null : attendance.byEmployee().get(e.getId());
                if (days != null) {
                    compareDays(parsed, l, original.employeeCode(), PayrollField.NGAY_CONG_CHUAN, days.standard(), all);
                    compareDays(parsed, l, original.employeeCode(), PayrollField.NGAY_CONG_THUC_TE, days.actual(), all);
                }
            }
            rows.add(
                new RowDTO(
                    l.excelRow(),
                    original.employeeCode(),
                    l.fullName(),
                    l.position(),
                    e == null ? null : e.getId(),
                    e == null ? null : e.getCode(),
                    e == null ? null : e.getFullName(),
                    mapped != null
                )
            );
        }

        List<IssueDTO> issues = all.stream().map(PayrollCheck::toDto).toList();
        int errors = (int) all.stream().filter(i -> i.severity() == Issue.Severity.ERROR).count();
        List<ColumnDTO> columns = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (PayrollField f : PayrollField.values()) {
            Integer col = parsed.columns().get(f);
            columns.add(new ColumnDTO(f.name(), f.label(), col == null ? null : CellReference.convertNumToColString(col), f.required()));
            if (col == null && f.required()) {
                missing.add(f.label());
            }
        }
        return new Result(columns, missing, rows, issues, errors, all.size() - errors, resolved, lines);
    }

    /** Cột có trong file mà ô khác số đã chốt (so theo giá trị: 20.5 = 20,50) → cảnh báo; ô trống được báo là để trống. */
    private static void compareDays(ParsedPayroll parsed, PayrollLine l, String fileCode, PayrollField f, BigDecimal closed, List<Issue> out) {
        if (closed == null || !parsed.columns().containsKey(f)) {
            return;
        }
        BigDecimal inFile = l.amounts().get(f);
        if (inFile != null && inFile.compareTo(closed) == 0) {
            return;
        }
        String written = inFile == null ? "File để trống" : "File ghi " + days(inFile);
        out.add(new Issue(Issue.Severity.WARNING, l.excelRow(), fileCode, f, written + ", bảng công đã chốt " + days(closed)));
    }

    /** Số ngày công kiểu Việt Nam: 20.50 → "20,5", 22.00 → "22". */
    static String days(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString().replace('.', ',');
    }

    private static String norm(String code) {
        return EmployeeCodes.normalize(code);
    }

    private static IssueDTO toDto(Issue i) {
        return new IssueDTO(
            i.severity().name(),
            i.excelRow(),
            i.employeeCode(),
            i.field() == null ? null : i.field().name(),
            i.field() == null ? null : i.field().label(),
            i.message()
        );
    }
}
