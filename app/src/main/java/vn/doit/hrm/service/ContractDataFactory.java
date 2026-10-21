package vn.doit.hrm.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.doit.hr.contract.ContractData;
import vn.doit.hr.contract.ContractFields;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.Gender;

/**
 * Dựng dữ liệu điền mẫu hợp đồng từ hồ sơ + hợp đồng + phụ cấp + cấu hình công ty.
 * Lương in đúng số đã thỏa thuận ({@code salary}); với lương Net chỉ in số Net, không in Gross.
 */
@Component
public class ContractDataFactory {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ApplicationProperties properties;

    public ContractDataFactory(ApplicationProperties properties) {
        this.properties = properties;
    }

    public ContractData build(Employee e, EmploymentContract c, List<Allowance> allowances) {
        LocalDate start = c.getStartDate();
        List<ContractData.Allowance> effective = allowances
            .stream()
            .filter(a -> start != null && a.getFromDate() != null && !a.getFromDate().isAfter(start))
            .filter(a -> a.getToDate() == null || !a.getToDate().isBefore(start))
            .filter(a -> a.getAmountPerMonth() != null)
            .map(a -> new ContractData.Allowance(a.getName(), a.getAmountPerMonth()))
            .toList();
        return new ContractData(
            c.getContractNo(),
            e.getFullName(),
            gender(e.getGender()),
            e.getBirthDate(),
            e.getResidence(),
            e.getPhone(),
            e.getEmail(),
            e.getEducation(),
            e.getIdNumber(),
            e.getIdIssueDate(),
            e.getIdIssuePlace(),
            e.getBankAccount(),
            e.getBankName(),
            c.getType() == ContractType.HDLD_KXDTH ? null : c.getTermMonths(),
            start,
            firstNonBlank(name(c.getDepartment()), name(e.getDepartment())),
            firstNonBlank(c.getPosition(), e.getPosition()),
            c.getSalary() == null ? 0 : c.getSalary(),
            effective,
            c.getRaisePolicy(),
            properties.getCompany().getRepresentativeName()
        );
    }

    /**
     * Thẻ → giá trị cho mẫu. Ngày kết thúc lấy theo hợp đồng đã lưu (HR sửa tay được); HĐ có ngày kết thúc mà không có số tháng
     * (thử việc, đào tạo, dịch vụ) in thời hạn theo khoảng ngày.
     */
    public Map<String, String> fields(ContractData d, EmploymentContract c) {
        Map<String, String> m = ContractFields.toMap(d);
        if (c.getType() != ContractType.HDLD_KXDTH && c.getEndDate() != null && d.startDate() != null) {
            m.put("loaiHopDong", "Xác định thời hạn");
            m.put("denNgay", c.getEndDate().format(DATE));
            if (d.termMonths() == null) {
                m.put("thoiHan", termText(d.startDate(), c.getEndDate()));
            }
        }
        return m;
    }

    /** Tên tiếng Việt các trường bắt buộc còn trống. */
    public List<String> missingFields(ContractData d) {
        List<String> missing = new ArrayList<>();
        if (blank(d.fullName())) missing.add("Họ tên");
        if (d.birthDate() == null) missing.add("Ngày sinh");
        if (blank(d.idNumber())) missing.add("Số CCCD");
        if (d.idIssueDate() == null) missing.add("Ngày cấp");
        if (blank(d.idIssuePlace())) missing.add("Nơi cấp");
        if (blank(d.residence())) missing.add("Nơi cư trú");
        if (blank(d.position())) missing.add("Chức danh");
        if (d.startDate() == null) missing.add("Ngày bắt đầu");
        if (d.baseSalary() <= 0) missing.add("Mức lương");
        if (blank(d.companyRepresentative())) missing.add("Người đại diện công ty");
        return missing;
    }

    /** "02 tháng" nếu đúng số tháng tròn (kết thúc = bắt đầu + n tháng − 1 ngày), ngược lại "N ngày". */
    static String termText(LocalDate start, LocalDate end) {
        long months = ChronoUnit.MONTHS.between(start, end.plusDays(1));
        if (months > 0 && start.plusMonths(months).minusDays(1).equals(end)) {
            return months % 12 == 0 ? "%02d năm".formatted(months / 12) : "%02d tháng".formatted(months);
        }
        return (ChronoUnit.DAYS.between(start, end) + 1) + " ngày";
    }

    private static String gender(Gender g) {
        if (g == null) {
            return null;
        }
        return g == Gender.NAM ? "Nam" : "Nữ";
    }

    private static String name(Department d) {
        return d == null ? null : d.getName();
    }

    private static String firstNonBlank(String a, String b) {
        return blank(a) ? b : a;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
