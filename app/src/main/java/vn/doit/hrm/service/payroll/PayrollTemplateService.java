package vn.doit.hrm.service.payroll;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.payroll.PayrollTemplateWriter;
import vn.doit.hr.payroll.PayrollTemplateWriter.PrefillRow;
import vn.doit.hrm.domain.AttendanceSnapshot;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.attendance.AttendanceMonthService;

/**
 * File bảng lương có ngày công của một tháng (Kế toán, Quản trị): file mẫu {@link PayrollTemplateWriter} điền sẵn mỗi người một dòng
 * (mã, họ tên, vị trí, ngày ký HĐLĐ) và hai cột ngày công lấy từ ảnh chụp bảng công đã chốt. Chỉ tải được khi tháng đã chốt.
 *
 * <p>Danh sách người: mọi nhân viên có ảnh chụp tháng đó (kể cả hồ sơ đã lưu trữ, người nghỉ giữa tháng), cộng thực tập sinh và HĐ dịch
 * vụ chưa lưu trữ có làm trong tháng (để trống cột ngày công). Sắp theo mã.
 */
@Service
@Transactional(readOnly = true)
public class PayrollTemplateService {

    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Set<ContractType> LABOUR = EnumSet.of(ContractType.HDLD_XDTH, ContractType.HDLD_KXDTH);

    private final AttendanceMonthService attendanceMonthService;
    private final EmployeeRepository employeeRepository;
    private final EmploymentContractRepository contractRepository;

    public PayrollTemplateService(
        AttendanceMonthService attendanceMonthService,
        EmployeeRepository employeeRepository,
        EmploymentContractRepository contractRepository
    ) {
        this.attendanceMonthService = attendanceMonthService;
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
    }

    /** Kỳ sai dạng → {@code periodinvalid}; bảng công tháng chưa chốt → {@code attendancenotclosed}. */
    public byte[] prefilled(String periodText) {
        YearMonth m = PayrollImportService.parsePeriod(periodText);
        if (!attendanceMonthService.isClosed(m)) {
            throw new EmployeeRuleException("Bảng công tháng " + m.format(MM_YYYY) + " chưa chốt", "attendancenotclosed");
        }
        Map<Long, AttendanceSnapshot> snapshot = attendanceMonthService.snapshot(m);
        Map<Long, Employee> people = new LinkedHashMap<>();
        employeeRepository.findAllById(snapshot.keySet()).forEach(e -> people.put(e.getId(), e));
        for (PersonType type : List.of(PersonType.THUC_TAP, PersonType.DICH_VU)) {
            employeeRepository
                .findByPersonTypeAndArchivedAtIsNull(type)
                .stream()
                .filter(e -> AttendanceMonthService.inMonth(e, m))
                .forEach(e -> people.put(e.getId(), e));
        }
        List<Employee> sorted = people
            .values()
            .stream()
            .sorted(Comparator.comparing(Employee::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        Map<Long, LocalDate> contractStart = firstLabourContract(people.keySet());

        List<PrefillRow> rows = new ArrayList<>();
        for (Employee e : sorted) {
            AttendanceSnapshot s = snapshot.get(e.getId());
            rows.add(
                new PrefillRow(
                    e.getCode(),
                    e.getFullName(),
                    e.getPosition(),
                    contractStart.get(e.getId()),
                    s == null ? null : s.getStandardDays(),
                    s == null ? null : s.getActualDays()
                )
            );
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new PayrollTemplateWriter().write(out, m, rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Ngày bắt đầu HĐLĐ đầu tiên đã ký (bỏ HĐ thử việc, đào tạo, dịch vụ và bản nháp) theo hồ sơ. */
    private Map<Long, LocalDate> firstLabourContract(Set<Long> ids) {
        Map<Long, LocalDate> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (EmploymentContract c : contractRepository.findByEmployeeIdIn(ids)) {
            if (!LABOUR.contains(c.getType()) || c.getStatus() == ContractStatus.NHAP || c.getStartDate() == null) {
                continue;
            }
            out.merge(c.getEmployee().getId(), c.getStartDate(), (a, b) -> a.isBefore(b) ? a : b);
        }
        return out;
    }
}
