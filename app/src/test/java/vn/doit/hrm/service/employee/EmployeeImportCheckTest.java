package vn.doit.hrm.service.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hr.employee.EmployeeField.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.doit.hr.employee.EmployeeField;
import vn.doit.hr.employee.EmployeeIssue;
import vn.doit.hr.employee.EmployeeIssue.Severity;
import vn.doit.hr.employee.EmployeeRow;
import vn.doit.hr.employee.ParsedEmployees;
import vn.doit.hrm.domain.enumeration.InsuranceExemption;
import vn.doit.hrm.service.employee.EmployeeImportCheck.PlannedRow;
import vn.doit.hrm.service.employee.EmployeeImportCheck.Result;
import vn.doit.hrm.service.employee.ImportDirectory.DepartmentRef;

/** Kiểm tra nghiệp vụ file nhập nhân viên, dữ liệu giả. */
class EmployeeImportCheckTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    /** Danh bạ giả. */
    private static final class FakeDir implements ImportDirectory {
        final List<String> codes = new ArrayList<>();
        final Map<String, Long> byCode = new HashMap<>();
        final Map<String, Long> byId = new HashMap<>();
        final List<DepartmentRef> departments = new ArrayList<>();

        FakeDir code(String code, long id) {
            codes.add(code);
            byCode.put(code, id);
            return this;
        }

        @Override
        public List<String> codesStartingWith(String prefix) {
            return codes.stream().filter(c -> c.startsWith(prefix)).toList();
        }

        @Override
        public Optional<Long> employeeIdByCode(String code) {
            return Optional.ofNullable(byCode.get(code));
        }

        @Override
        public Optional<Long> employeeIdByIdNumber(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<DepartmentRef> departments() {
            return departments;
        }
    }

    private static int nextId = 100;

    /** Dòng hợp lệ tối thiểu; CCCD tự tăng để không trùng nhau. */
    private static EmployeeRow row(int excelRow, Object... kv) {
        Map<EmployeeField, Object> v = new EnumMap<>(EmployeeField.class);
        v.put(FULL_NAME, "Người " + excelRow);
        v.put(ID_NUMBER, "0791234567" + (10 + excelRow));
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] == null) {
                v.remove((EmployeeField) kv[i]);
            } else {
                v.put((EmployeeField) kv[i], kv[i + 1]);
            }
        }
        return new EmployeeRow(excelRow, v);
    }

    private static Result run(FakeDir dir, EmployeeRow... rows) {
        return EmployeeImportCheck.run(new ParsedEmployees(Map.of(), List.of(rows), List.of()), dir, TODAY);
    }

    private static List<String> messages(Result r, int excelRow, Severity severity) {
        return r.issues().stream().filter(i -> i.excelRow() == excelRow && i.severity() == severity)
                .map(EmployeeIssue::message).toList();
    }

    private static PlannedRow planned(Result r, int excelRow) {
        return r.rows().stream().filter(p -> p.row().excelRow() == excelRow).findFirst().orElseThrow();
    }

    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    @Test
    void exemptionLabelsMatchCore() {
        List<String> labels = java.util.Arrays.stream(InsuranceExemption.values()).map(InsuranceExemption::label).toList();
        assertThat(EXEMPTIONS).isEqualTo(labels);
    }

    @Test
    void missingNameOrIdNumber() {
        Result r = run(new FakeDir(), row(2, FULL_NAME, null, ID_NUMBER, null));
        assertThat(messages(r, 2, Severity.ERROR)).contains("Thiếu Họ và tên", "Thiếu Số CCCD");
        assertThat(r.canSave()).isFalse();
    }

    @Test
    void idNumberFormat() {
        Result r = run(new FakeDir(), row(2, ID_NUMBER, "12345"));
        assertThat(messages(r, 2, Severity.ERROR)).contains("Số CCCD phải gồm 12 chữ số (hoặc 9 chữ số CMND cũ)");
        assertThat(run(new FakeDir(), row(2, ID_NUMBER, "123456789")).canSave()).isTrue();
    }

    @Test
    void idNumberDuplicates() {
        FakeDir dir = new FakeDir();
        dir.byId.put("079000000001", 7L);
        Result r = run(dir, row(2, ID_NUMBER, " 079000000001 "), row(3, ID_NUMBER, "079000000002"),
                row(4, ID_NUMBER, "079000000002"));
        assertThat(messages(r, 2, Severity.ERROR)).contains("Số CCCD đã có trong hệ thống");
        assertThat(messages(r, 3, Severity.ERROR)).contains("Số CCCD trùng với dòng 4");
        assertThat(messages(r, 4, Severity.ERROR)).contains("Số CCCD trùng với dòng 3");
    }

    @Test
    void codeDuplicates() {
        FakeDir dir = new FakeDir().code("DOIT2501", 1L);
        Result r = run(dir, row(2, CODE, "doit2501"), row(3, CODE, " doit2601"), row(4, CODE, "DOIT2601"));
        assertThat(messages(r, 2, Severity.ERROR)).contains("Mã NV đã có trong hệ thống");
        assertThat(messages(r, 3, Severity.ERROR)).contains("Mã NV trùng với dòng 4");
        assertThat(messages(r, 4, Severity.ERROR)).contains("Mã NV trùng với dòng 3");
    }

    @Test
    void exemptionOtherNeedsNote() {
        Result r = run(new FakeDir(), row(2, INSURANCE_EXEMPTION, "Khác"));
        assertThat(messages(r, 2, Severity.ERROR)).contains("Chọn \"Khác\" thì phải ghi Lý do khác");
        Result ok = run(new FakeDir(), row(2, INSURANCE_EXEMPTION, "Khác", INSURANCE_EXEMPTION_NOTE, "Thỏa thuận"));
        assertThat(ok.canSave()).isTrue();
        assertThat(planned(ok, 2).exemption()).isEqualTo(InsuranceExemption.KHAC);
    }

    @Test
    void invalidEmail() {
        assertThat(messages(run(new FakeDir(), row(2, EMAIL, "a@")), 2, Severity.ERROR)).contains("Email không hợp lệ");
        assertThat(run(new FakeDir(), row(2, EMAIL, "a@example.com")).canSave()).isTrue();
    }

    @Test
    void provisionalCodes() {
        FakeDir dir = new FakeDir().code("DOIT2401", 1L).code("DOIT2517", 2L).code("TT2601", 3L);
        Result r = run(dir,
                row(2, JOIN_DATE, d(2026, 3, 1)),
                row(3, PERSON_TYPE, "Thực tập", JOIN_DATE, d(2026, 3, 1)),
                row(4, JOIN_DATE, d(2026, 4, 1)),
                row(5, JOIN_DATE, d(2026, 4, 1), CODE, "DOIT2401X"));
        assertThat(planned(r, 2).code()).isEqualTo("DOIT2618");
        assertThat(planned(r, 3).code()).isEqualTo("TT2602");
        assertThat(planned(r, 4).code()).isEqualTo("DOIT2619");
        assertThat(planned(r, 2).codeProvisional()).isTrue();
        assertThat(planned(r, 5).code()).isEqualTo("DOIT2401X");
        assertThat(planned(r, 5).codeProvisional()).isFalse();
        assertThat(messages(r, 5, Severity.WARNING))
                .contains("Mã không theo dạng DOIT/TT + số; dãy tự cấp bỏ qua mã này");
    }

    @Test
    void provisionalCodesSkipManualCodesLaterInFile() {
        Result r = run(new FakeDir(), row(2, JOIN_DATE, d(2026, 3, 1)),
                row(5, JOIN_DATE, d(2026, 3, 1), CODE, "DOIT2601"));
        assertThat(planned(r, 2).code()).isEqualTo("DOIT2602");
        assertThat(planned(r, 5).code()).isEqualTo("DOIT2601");
    }

    @Test
    void serviceRowsIgnoreCode() {
        Result r = run(new FakeDir(), row(2, PERSON_TYPE, "HĐ dịch vụ", CODE, "DOIT2601"));
        assertThat(planned(r, 2).code()).isNull();
        assertThat(messages(r, 2, Severity.WARNING))
                .contains("HĐ dịch vụ luôn được cấp mã HDDV tự động; bỏ qua mã đã ghi");
    }

    @Test
    void missingJoinDateUsesThisYear() {
        Result r = run(new FakeDir(), row(2));
        assertThat(planned(r, 2).code()).startsWith("DOIT26");
        assertThat(messages(r, 2, Severity.WARNING)).contains("Thiếu Ngày vào làm: mã tự cấp lấy năm nay");
    }

    @Test
    void ageAndFutureJoin() {
        Result r = run(new FakeDir(),
                row(2, BIRTH_DATE, d(2015, 1, 1), JOIN_DATE, d(2026, 1, 1)),
                row(3, JOIN_DATE, d(2026, 12, 1)));
        assertThat(messages(r, 2, Severity.WARNING)).contains("Dưới 15 tuổi tại ngày vào làm");
        assertThat(messages(r, 3, Severity.WARNING)).contains("Ngày vào làm sau hôm nay");
        assertThat(r.canSave()).isTrue();
    }

    @Test
    void departmentMatchIgnoresAccentAndCase() {
        FakeDir dir = new FakeDir();
        dir.departments.add(new DepartmentRef(1L, "Phòng Kỹ thuật", true));
        Result r = run(dir, row(2, DEPARTMENT, "  phong ky thuat "));
        assertThat(planned(r, 2).departmentId()).isEqualTo(1L);
        assertThat(messages(r, 2, Severity.WARNING)).noneMatch(m -> m.startsWith("Phòng ban"));
        assertThat(r.newDepartments()).isEmpty();
    }

    @Test
    void newDepartmentWarned() {
        Result r = run(new FakeDir(), row(2, DEPARTMENT, "Kinh doanh"), row(3, DEPARTMENT, "kinh doanh"));
        assertThat(r.newDepartments()).hasSize(1);
        assertThat(r.newDepartments().get(0).name()).isEqualTo("Kinh doanh");
        assertThat(r.newDepartments().get(0).excelRows()).containsExactly(2, 3);
        assertThat(messages(r, 2, Severity.WARNING)).contains("Phòng ban \"Kinh doanh\" chưa có, sẽ được tạo mới");
        assertThat(messages(r, 3, Severity.WARNING)).contains("Phòng ban \"Kinh doanh\" chưa có, sẽ được tạo mới");
        assertThat(planned(r, 3).newDepartmentName()).isEqualTo("Kinh doanh");
    }

    @Test
    void ambiguousDepartment() {
        FakeDir dir = new FakeDir();
        dir.departments.add(new DepartmentRef(1L, "Kế toán", true));
        dir.departments.add(new DepartmentRef(2L, "Ke toan", true));
        dir.departments.add(new DepartmentRef(3L, "Nhân sự", true));
        dir.departments.add(new DepartmentRef(4L, "Nhan su", false));
        Result r = run(dir, row(2, DEPARTMENT, "kế toán"), row(3, DEPARTMENT, "Nhân sự"));
        assertThat(messages(r, 2, Severity.ERROR))
                .contains("Tên phòng ban không rõ: có nhiều phòng ban trùng tên");
        assertThat(planned(r, 3).departmentId()).isEqualTo(3L);
        assertThat(messages(r, 3, Severity.ERROR)).isEmpty();
    }

    @Test
    void managerResolution() {
        FakeDir dir = new FakeDir().code("DOIT2401", 11L);
        dir.byId.put("079000000099", 12L);
        Result r = run(dir,
                row(2, MANAGER, "doit2401"),
                row(3, MANAGER, "079000000099"),
                row(4, CODE, "DOIT2601", ID_NUMBER, "079000000004"),
                row(5, MANAGER, "DOIT2601"),
                row(6, MANAGER, "079000000004"),
                row(7, MANAGER, "XYZ"),
                row(8, MANAGER, "DOIT2601", CODE, "DOIT2602", ID_NUMBER, "079000000008"),
                row(9, MANAGER, "079000000009"));
        assertThat(planned(r, 2).manager().employeeId()).isEqualTo(11L);
        assertThat(planned(r, 3).manager().employeeId()).isEqualTo(12L);
        assertThat(planned(r, 5).manager().excelRow()).isEqualTo(4);
        assertThat(planned(r, 6).manager().excelRow()).isEqualTo(4);
        assertThat(planned(r, 5).manager().employeeId()).isNull();
        assertThat(messages(r, 7, Severity.ERROR))
                .contains("Không tìm thấy người quản lý \"XYZ\" (theo mã NV hoặc số CCCD)");
        assertThat(messages(r, 9, Severity.ERROR))
                .contains("Không tìm thấy người quản lý \"079000000009\" (theo mã NV hoặc số CCCD)");
        assertThat(messages(r, 2, Severity.ERROR)).isEmpty();
        assertThat(messages(r, 8, Severity.ERROR)).isEmpty();
    }

    @Test
    void managerSelfAndCycle() {
        Result self = run(new FakeDir(), row(2, CODE, "DOIT2601", MANAGER, "DOIT2601"));
        assertThat(messages(self, 2, Severity.ERROR)).contains("Không thể tự làm quản lý của mình");

        Result cycle = run(new FakeDir(),
                row(2, CODE, "DOIT2601", MANAGER, "DOIT2602"),
                row(3, CODE, "DOIT2602", MANAGER, "DOIT2601"),
                row(4, MANAGER, "DOIT2601"));
        assertThat(messages(cycle, 2, Severity.ERROR)).contains("Quản lý tạo thành vòng (dòng 2 → dòng 3 → dòng 2)");
        assertThat(messages(cycle, 3, Severity.ERROR)).contains("Quản lý tạo thành vòng (dòng 3 → dòng 2 → dòng 3)");
        assertThat(messages(cycle, 4, Severity.ERROR)).isEmpty();
    }

    @Test
    void parserIssuesKeptFirst() {
        EmployeeIssue parserIssue = new EmployeeIssue(Severity.ERROR, 2, "Người 2", BIRTH_DATE, "Ngày sai");
        ParsedEmployees parsed = new ParsedEmployees(Map.of(), List.of(row(2, ID_NUMBER, "12")), List.of(parserIssue));
        Result r = EmployeeImportCheck.run(parsed, new FakeDir(), TODAY);
        assertThat(r.issues().get(0)).isEqualTo(parserIssue);
        assertThat(r.errorCount()).isEqualTo(2);
    }
}
