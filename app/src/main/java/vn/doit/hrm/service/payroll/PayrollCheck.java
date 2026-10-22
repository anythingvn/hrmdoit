package vn.doit.hrm.service.payroll;

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

    /** Mã trong file (hoặc dòng gắn tay) trỏ vào hồ sơ đã lưu trữ. */
    public static final String ARCHIVED_MESSAGE = "Hồ sơ đã lưu trữ — khôi phục trước khi import";

    private PayrollCheck() {}

    public static Result run(
        ParsedPayroll parsed,
        YearMonth period,
        Map<Integer, Employee> mappings,
        Function<String, Optional<Employee>> byCode,
        RuleBook book
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
