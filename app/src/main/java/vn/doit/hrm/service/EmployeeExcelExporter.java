package vn.doit.hrm.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.AssignmentHistory;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.Gender;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.service.criteria.EmployeeCriteria;

/** Xuất danh sách nhân viên ra .xlsx. Không bao giờ xuất trường nhạy cảm. */
@Service
public class EmployeeExcelExporter {

    static final String SHEET = "Nhân viên";

    static final List<String> BASE_HEADERS = List.of(
        "Mã NV",
        "Họ và tên",
        "Giới tính",
        "Ngày sinh",
        "Email",
        "SĐT",
        "Phòng ban",
        "Chức danh",
        "Nơi làm việc hiện tại",
        "Loại HĐ hiện tại",
        "Tình trạng",
        "Ngày vào công ty"
    );

    static final List<String> SALARY_HEADERS = List.of("Lương hiện tại", "Net/Gross", "Lương Gross");

    private final EmployeeQueryService queryService;
    private final EmploymentContractRepository contractRepository;
    private final SalaryChangeRepository salaryChangeRepository;
    private final WorkAssignmentRepository workAssignmentRepository;
    private final Clock clock;

    public EmployeeExcelExporter(
        EmployeeQueryService queryService,
        EmploymentContractRepository contractRepository,
        SalaryChangeRepository salaryChangeRepository,
        WorkAssignmentRepository workAssignmentRepository,
        Clock clock
    ) {
        this.queryService = queryService;
        this.contractRepository = contractRepository;
        this.salaryChangeRepository = salaryChangeRepository;
        this.workAssignmentRepository = workAssignmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public byte[] export(EmployeeCriteria criteria, Sort sort, boolean includeSalary) {
        List<Employee> employees = queryService.findEntitiesByCriteria(criteria, sort);
        LocalDate today = LocalDate.now(clock);
        List<Long> ids = employees.stream().map(Employee::getId).toList();
        Map<Long, List<EmploymentContract>> contracts = ids.isEmpty() ? Map.of() : group(contractRepository.findByEmployeeIdIn(ids), c -> c.getEmployee().getId());
        Map<Long, List<SalaryChange>> changes = !includeSalary || ids.isEmpty() ? Map.of() : group(salaryChangeRepository.findByEmployeeIdIn(ids), s -> s.getEmployee().getId());
        Map<Long, List<WorkAssignment>> assignments = ids.isEmpty() ? Map.of() : group(workAssignmentRepository.findByEmployeeIdIn(ids), a -> a.getEmployee().getId());

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET);
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(helper.createDataFormat().getFormat("#,##0"));
            CellStyle headStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);

            List<String> headers = new ArrayList<>(BASE_HEADERS);
            if (includeSalary) headers.addAll(SALARY_HEADERS);
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = head.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headStyle);
            }
            sheet.createFreezePane(0, 1);

            int r = 1;
            for (Employee e : employees) {
                Row row = sheet.createRow(r++);
                int i = 0;
                text(row, i++, e.getCode());
                text(row, i++, e.getFullName());
                text(row, i++, e.getGender() == null ? null : e.getGender() == Gender.NAM ? "Nam" : "Nữ");
                date(row, i++, e.getBirthDate(), dateStyle);
                text(row, i++, e.getEmail());
                text(row, i++, e.getPhone());
                text(row, i++, e.getDepartment() == null ? null : e.getDepartment().getName());
                text(row, i++, e.getPosition());
                text(row, i++, placeLabel(e, assignments.getOrDefault(e.getId(), List.of()), today));
                List<EmploymentContract> myContracts = contracts.getOrDefault(e.getId(), List.of());
                text(row, i++, contractLabel(currentContractType(e, myContracts, today)));
                text(row, i++, e.getStatus() == EmploymentStatus.DA_NGHI ? "Đã nghỉ" : "Đang làm việc");
                date(row, i++, e.getJoinDate(), dateStyle);
                if (includeSalary) {
                    var salary = CurrentSalaryCalculator.of(e.getStatus(), myContracts, changes.getOrDefault(e.getId(), List.of()), today);
                    CurrentSalary cs = salary.orElse(null);
                    if (cs != null) {
                        money(row, i++, cs.amount(), moneyStyle);
                        text(row, i++, cs.basis().name());
                        money(row, i++, cs.grossAmount(), moneyStyle);
                    }
                }
            }
            for (int i = 0; i < headers.size(); i++) sheet.setColumnWidth(i, i == 1 || i == 8 ? 28 * 256 : 16 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static <T> Map<Long, List<T>> group(Collection<T> rows, java.util.function.Function<T, Long> key) {
        return rows.stream().collect(Collectors.groupingBy(key, HashMap::new, Collectors.toList()));
    }

    /** Cùng quy tắc với EmployeeService.findOne: HĐ đã ký bắt đầu muộn nhất ≤ hôm nay; trống nếu đã nghỉ. */
    static ContractType currentContractType(Employee e, List<EmploymentContract> contracts, LocalDate today) {
        if (e.getStatus() == EmploymentStatus.DA_NGHI) return null;
        return contracts
            .stream()
            .filter(c -> c.getStatus() == ContractStatus.DA_KY && c.getStartDate() != null && !c.getStartDate().isAfter(today))
            .max(Comparator.comparing(EmploymentContract::getStartDate))
            .map(EmploymentContract::getType)
            .orElse(null);
    }

    private static String placeLabel(Employee e, List<WorkAssignment> rows, LocalDate today) {
        if (rows.isEmpty()) return null;
        return AssignmentHistory
            .of(e.getCode(), rows.stream().map(a -> AssignmentMapping.toCore(a, e.getCode())).toList())
            .locationOn(today)
            .map(Assignment::placeLabel)
            .orElse(null);
    }

    static String contractLabel(ContractType t) {
        if (t == null) return null;
        return switch (t) {
            case HDDT -> "HĐ đào tạo";
            case HDTV -> "HĐ thử việc";
            case HDLD_XDTH -> "HĐLĐ xác định thời hạn";
            case HDLD_KXDTH -> "HĐLĐ không xác định thời hạn";
            case HDDV -> "HĐ dịch vụ";
        };
    }

    private static void text(Row row, int col, String v) {
        if (v != null) row.createCell(col).setCellValue(v);
    }

    private static void date(Row row, int col, LocalDate v, CellStyle style) {
        if (v == null) return;
        Cell c = row.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(style);
    }

    private static void money(Row row, int col, Long v, CellStyle style) {
        if (v == null) return;
        Cell c = row.createCell(col);
        c.setCellValue(v.doubleValue());
        c.setCellStyle(style);
    }
}
