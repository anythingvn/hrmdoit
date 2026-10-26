package vn.doit.hrm.service.employee;

import static vn.doit.hr.employee.EmployeeField.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import vn.doit.hr.employee.EmployeeField;
import vn.doit.hr.employee.EmployeeIssue;
import vn.doit.hr.employee.EmployeeIssue.Severity;
import vn.doit.hr.employee.EmployeeRow;
import vn.doit.hr.employee.ParsedEmployees;
import vn.doit.hrm.domain.enumeration.Gender;
import vn.doit.hrm.domain.enumeration.InsuranceExemption;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.service.EmployeeCodes;
import vn.doit.hrm.service.IdNumbers;
import vn.doit.hrm.service.VietnameseText;
import vn.doit.hrm.service.employee.ImportDirectory.DepartmentRef;
import vn.doit.hrm.service.numbering.NumberingSettings;

/**
 * Kiểm tra nghiệp vụ file nhập nhân viên (spec §5): trùng CCCD/mã, mã dự kiến, phòng ban, quản lý.
 * Lớp thuần, không truy cập cơ sở dữ liệu; tra cứu qua {@link ImportDirectory}.
 */
public final class EmployeeImportCheck {

    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private EmployeeImportCheck() {}

    public record ManagerRef(Long employeeId, Integer excelRow) {}

    public record PlannedRow(
        EmployeeRow row,
        PersonType personType,
        Gender gender,
        InsuranceExemption exemption,
        String code,
        boolean codeProvisional,
        String idNumber,
        Long departmentId,
        String newDepartmentName,
        ManagerRef manager
    ) {}

    public record NewDepartment(String name, List<Integer> excelRows) {}

    public record Result(List<PlannedRow> rows, List<EmployeeIssue> issues, List<NewDepartment> newDepartments) {
        public long errorCount() {
            return issues
                .stream()
                .filter(i -> i.severity() == Severity.ERROR)
                .count();
        }

        public long warningCount() {
            return issues
                .stream()
                .filter(i -> i.severity() == Severity.WARNING)
                .count();
        }

        public boolean canSave() {
            return errorCount() == 0;
        }
    }

    public static PersonType personType(String label) {
        if (label == null) {
            return PersonType.NHAN_VIEN;
        }
        return switch (PERSON_TYPES.indexOf(label)) {
            case 1 -> PersonType.THUC_TAP;
            case 2 -> PersonType.DICH_VU;
            default -> PersonType.NHAN_VIEN;
        };
    }

    public static Gender gender(String label) {
        if (label == null) {
            return null;
        }
        return switch (label) {
            case "Nam" -> Gender.NAM;
            case "Nữ" -> Gender.NU;
            default -> null;
        };
    }

    public static InsuranceExemption exemption(String label) {
        if (label == null) {
            return null;
        }
        for (InsuranceExemption e : InsuranceExemption.values()) {
            if (e.label().equals(label)) {
                return e;
            }
        }
        return null;
    }

    /** Mã dành riêng cho HĐ dịch vụ: đúng dạng tiền tố dịch vụ + chỉ chữ số. */
    private static boolean serviceReserved(String servicePrefix, String code) {
        return Pattern.matches(Pattern.quote(servicePrefix) + "\\d+", code);
    }

    public static Result run(ParsedEmployees parsed, ImportDirectory dir, LocalDate today, NumberingSettings settings) {
        List<EmployeeIssue> mine = new ArrayList<>();
        List<EmployeeRow> rows = parsed.rows();
        int n = rows.size();

        // (1) chuẩn hóa từng dòng và kiểm tra ô
        PersonType[] types = new PersonType[n];
        String[] codes = new String[n];
        String[] ids = new String[n];
        // ô đã có lỗi đọc file thì không báo thêm "Thiếu …" cho đúng ô đó
        java.util.Set<String> parserFlagged = new java.util.HashSet<>();
        for (EmployeeIssue pi : parsed.issues()) {
            if (pi.field() != null) {
                parserFlagged.add(pi.excelRow() + ":" + pi.field());
            }
        }
        for (int i = 0; i < n; i++) {
            EmployeeRow r = rows.get(i);
            types[i] = personType(r.text(PERSON_TYPE));
            codes[i] = EmployeeCodes.normalize(r.text(CODE));
            ids[i] = IdNumbers.trim(r.text(ID_NUMBER));
            if (r.text(FULL_NAME) == null) {
                if (!parserFlagged.contains(r.excelRow() + ":" + FULL_NAME)) {
                    add(mine, Severity.ERROR, r, FULL_NAME, "Thiếu Họ và tên");
                }
            }
            if (ids[i] == null) {
                if (!parserFlagged.contains(r.excelRow() + ":" + ID_NUMBER)) {
                    add(mine, Severity.ERROR, r, ID_NUMBER, "Thiếu Số CCCD");
                }
            } else if (!IdNumbers.isValid(ids[i])) {
                add(mine, Severity.ERROR, r, ID_NUMBER, "Số CCCD phải gồm 12 chữ số (hoặc 9 chữ số CMND cũ)");
            }
            String email = r.text(EMAIL);
            if (email != null && !EMAIL_SHAPE.matcher(email).matches()) {
                add(mine, Severity.ERROR, r, EMAIL, "Email không hợp lệ");
            }
            if (exemption(r.text(INSURANCE_EXEMPTION)) == InsuranceExemption.KHAC && r.text(INSURANCE_EXEMPTION_NOTE) == null) {
                add(mine, Severity.ERROR, r, INSURANCE_EXEMPTION_NOTE, "Chọn \"Khác\" thì phải ghi Lý do khác");
            }
            Long dependants = r.integer(DEPENDANTS);
            if (dependants != null && dependants > Integer.MAX_VALUE) {
                add(mine, Severity.ERROR, r, DEPENDANTS, "Số người phụ thuộc quá lớn");
            }
            LocalDate birth = r.date(BIRTH_DATE);
            LocalDate join = r.date(JOIN_DATE);
            LocalDate ref = join != null ? join : today;
            if (birth != null && birth.plusYears(15).isAfter(ref)) {
                add(mine, Severity.WARNING, r, BIRTH_DATE, "Dưới 15 tuổi tại ngày vào làm");
            }
            if (join != null && join.isAfter(today)) {
                add(mine, Severity.WARNING, r, JOIN_DATE, "Ngày vào làm sau hôm nay");
            }
        }

        // (2) trùng CCCD / mã
        Map<String, List<Integer>> byId = new HashMap<>();
        Map<String, List<Integer>> byCode = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (ids[i] != null) {
                byId.computeIfAbsent(ids[i], k -> new ArrayList<>()).add(i);
            }
            if (codes[i] != null && types[i] != PersonType.DICH_VU) {
                byCode.computeIfAbsent(codes[i], k -> new ArrayList<>()).add(i);
            }
        }
        for (int i = 0; i < n; i++) {
            EmployeeRow r = rows.get(i);
            if (ids[i] != null) {
                if (dir.employeeIdByIdNumber(ids[i]).isPresent()) {
                    add(mine, Severity.ERROR, r, ID_NUMBER, "Số CCCD đã có trong hệ thống");
                }
                duplicateInFile(mine, rows, byId.get(ids[i]), i, ID_NUMBER, "Số CCCD");
            }
            if (codes[i] != null && types[i] != PersonType.DICH_VU) {
                if (dir.employeeIdByCode(codes[i]).isPresent()) {
                    add(mine, Severity.ERROR, r, CODE, "Mã NV đã có trong hệ thống");
                }
                duplicateInFile(mine, rows, byCode.get(codes[i]), i, CODE, "Mã NV");
            }
        }

        // (3) mã dự kiến: gom mã ghi tay đúng dạng trước, rồi cấp theo thứ tự dòng
        Map<String, List<String>> existing = new HashMap<>();
        for (String prefix : settings.sequencePrefixes()) {
            existing.put(prefix, new ArrayList<>(dir.codesStartingWith(prefix)));
        }
        for (int i = 0; i < n; i++) {
            if (codes[i] == null) {
                continue;
            }
            if (types[i] == PersonType.DICH_VU) {
                add(
                    mine,
                    Severity.WARNING,
                    rows.get(i),
                    CODE,
                    "HĐ dịch vụ luôn được cấp mã " + settings.serviceCodePrefix() + " tự động; bỏ qua mã đã ghi"
                );
                codes[i] = null;
            } else if (serviceReserved(settings.serviceCodePrefix(), codes[i])) {
                add(
                    mine,
                    Severity.ERROR,
                    rows.get(i),
                    CODE,
                    "Mã bắt đầu bằng " + settings.serviceCodePrefix() + " dành riêng cho HĐ dịch vụ (tự cấp)"
                );
            } else {
                boolean sequenced = false;
                for (String prefix : settings.sequencePrefixes()) {
                    if (EmployeeCodes.isSequenced(prefix, codes[i])) {
                        existing.get(prefix).add(codes[i]);
                        sequenced = true;
                    }
                }
                if (!sequenced) {
                    add(
                        mine,
                        Severity.WARNING,
                        rows.get(i),
                        CODE,
                        "Mã không theo dạng " +
                            settings.staffCodePrefix() +
                            "/" +
                            settings.internCodePrefix() +
                            " + số; dãy tự cấp bỏ qua mã này"
                    );
                }
            }
        }
        boolean[] provisional = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (types[i] == PersonType.DICH_VU || codes[i] != null) {
                continue;
            }
            EmployeeRow r = rows.get(i);
            LocalDate join = r.date(JOIN_DATE);
            if (join == null && !parserFlagged.contains(r.excelRow() + ":" + JOIN_DATE)) {
                add(mine, Severity.WARNING, r, JOIN_DATE, "Thiếu Ngày vào làm: mã tự cấp lấy năm nay");
            }
            String prefix = settings.sequencePrefix(types[i]);
            codes[i] = EmployeeCodes.next(prefix, (join != null ? join : today).getYear(), existing.get(prefix));
            existing.get(prefix).add(codes[i]);
            provisional[i] = true;
        }

        // (4) phòng ban
        Long[] deptIds = new Long[n];
        String[] newDeptNames = new String[n];
        Map<String, NewDepartment> created = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            EmployeeRow r = rows.get(i);
            String name = r.text(DEPARTMENT);
            if (name == null) {
                continue;
            }
            String key = VietnameseText.fold(name);
            List<DepartmentRef> same = dir
                .departments()
                .stream()
                .filter(d -> VietnameseText.fold(d.name()).equals(key))
                .toList();
            if (same.isEmpty()) {
                NewDepartment nd = created.get(key);
                if (nd == null) {
                    nd = new NewDepartment(name.trim(), new ArrayList<>());
                    created.put(key, nd);
                }
                nd.excelRows().add(r.excelRow());
                newDeptNames[i] = nd.name();
                add(mine, Severity.WARNING, r, DEPARTMENT, "Phòng ban \"" + nd.name() + "\" chưa có, sẽ được tạo mới");
                continue;
            }
            List<DepartmentRef> pool = same.stream().filter(DepartmentRef::active).toList();
            if (pool.isEmpty()) {
                pool = same;
            }
            if (pool.size() > 1) {
                add(mine, Severity.ERROR, r, DEPARTMENT, "Tên phòng ban không rõ: có nhiều phòng ban trùng tên");
            } else {
                deptIds[i] = pool.get(0).id();
            }
        }

        // (5) quản lý
        Map<String, Integer> fileByCode = new HashMap<>();
        Map<String, Integer> fileById = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (codes[i] != null && !provisional[i]) {
                fileByCode.putIfAbsent(codes[i], i);
            }
            if (ids[i] != null) {
                fileById.putIfAbsent(ids[i], i);
            }
        }
        ManagerRef[] managers = new ManagerRef[n];
        int[] managerRow = new int[n]; // chỉ số dòng trong file mà dòng i trỏ tới, -1 nếu không
        java.util.Arrays.fill(managerRow, -1);
        for (int i = 0; i < n; i++) {
            EmployeeRow r = rows.get(i);
            String value = r.text(MANAGER);
            if (value == null) {
                continue;
            }
            String code = EmployeeCodes.normalize(value);
            String id = IdNumbers.trim(value);
            Optional<Long> sysId = dir.employeeIdByCode(code);
            Integer target = null;
            if (sysId.isEmpty()) {
                target = fileByCode.get(code);
                if (target == null) {
                    sysId = dir.employeeIdByIdNumber(id);
                    if (sysId.isEmpty()) {
                        target = fileById.get(id);
                    }
                }
            }
            if (sysId.isPresent() && dir.isArchived(sysId.orElseThrow())) {
                add(
                    mine,
                    Severity.ERROR,
                    r,
                    MANAGER,
                    "Người quản lý \"" + value + "\": Hồ sơ đã lưu trữ — khôi phục hồ sơ hoặc chọn người quản lý khác"
                );
            } else if (sysId.isPresent()) {
                managers[i] = new ManagerRef(sysId.orElseThrow(), null);
            } else if (target == null) {
                add(mine, Severity.ERROR, r, MANAGER, "Không tìm thấy người quản lý \"" + value + "\" (theo mã NV hoặc số CCCD)");
            } else if (target == i) {
                add(mine, Severity.ERROR, r, MANAGER, "Không thể tự làm quản lý của mình");
            } else {
                managers[i] = new ManagerRef(null, rows.get(target).excelRow());
                managerRow[i] = target;
            }
        }
        for (int i = 0; i < n; i++) {
            if (managerRow[i] < 0) {
                continue;
            }
            List<Integer> path = new ArrayList<>(List.of(i));
            int cur = managerRow[i];
            while (cur >= 0 && cur != i && !path.contains(cur)) {
                path.add(cur);
                cur = managerRow[cur];
            }
            if (cur == i) {
                StringBuilder sb = new StringBuilder();
                for (int p : path) {
                    sb.append("dòng ").append(rows.get(p).excelRow()).append(" → ");
                }
                sb.append("dòng ").append(rows.get(i).excelRow());
                add(mine, Severity.ERROR, rows.get(i), MANAGER, "Quản lý tạo thành vòng (" + sb + ")");
            }
        }

        List<PlannedRow> planned = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            EmployeeRow r = rows.get(i);
            planned.add(
                new PlannedRow(
                    r,
                    types[i],
                    gender(r.text(GENDER)),
                    exemption(r.text(INSURANCE_EXEMPTION)),
                    codes[i],
                    provisional[i],
                    ids[i],
                    deptIds[i],
                    newDeptNames[i],
                    managers[i]
                )
            );
        }
        List<EmployeeIssue> issues = new ArrayList<>(parsed.issues());
        issues.addAll(mine);
        issues.sort(Comparator.comparingInt(EmployeeIssue::excelRow)); // sắp ổn định: lỗi đọc file đứng trước
        return new Result(planned, issues, new ArrayList<>(created.values()));
    }

    private static void duplicateInFile(
        List<EmployeeIssue> out,
        List<EmployeeRow> rows,
        List<Integer> group,
        int self,
        EmployeeField field,
        String label
    ) {
        if (group == null || group.size() < 2) {
            return;
        }
        String others = group
            .stream()
            .filter(g -> g != self)
            .map(g -> String.valueOf(rows.get(g).excelRow()))
            .collect(java.util.stream.Collectors.joining(", "));
        add(out, Severity.ERROR, rows.get(self), field, label + " trùng với dòng " + others);
    }

    private static void add(List<EmployeeIssue> out, Severity severity, EmployeeRow r, EmployeeField field, String message) {
        out.add(new EmployeeIssue(severity, r.excelRow(), r.text(FULL_NAME), field, message));
    }
}
