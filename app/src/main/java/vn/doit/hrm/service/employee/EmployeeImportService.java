package vn.doit.hrm.service.employee;

import static vn.doit.hr.employee.EmployeeField.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hr.employee.EmployeeIssue;
import vn.doit.hr.employee.EmployeeRow;
import vn.doit.hr.employee.EmployeeSheetParser;
import vn.doit.hr.employee.EmployeeTemplateWriter;
import vn.doit.hr.employee.ParsedEmployees;
import vn.doit.hr.payroll.PoiSheetGrid;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.EmployeeService;
import vn.doit.hrm.service.UploadNames;
import vn.doit.hrm.service.employee.EmployeeImportCheck.PlannedRow;
import vn.doit.hrm.service.employee.EmployeeImportCheck.Result;
import vn.doit.hrm.service.employee.dto.EmployeeImportCheckDTO;
import vn.doit.hrm.service.employee.dto.EmployeeImportResultDTO;

/**
 * Nhập danh sách nhân viên từ Excel (Quản trị, HR): kiểm tra không ghi gì; lưu thì đọc và kiểm tra lại từ đầu rồi tạo tất cả
 * trong một giao dịch. File chỉ đọc trong bộ nhớ; không ghi nội dung dòng vào log.
 */
@Service
@Transactional
public class EmployeeImportService {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeImportService.class);

    static final long MAX_BYTES = 20L * 1024 * 1024;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeService employeeService;
    private final Clock clock;

    public EmployeeImportService(
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        EmployeeService employeeService,
        Clock clock
    ) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.employeeService = employeeService;
        this.clock = clock;
    }

    /** File mẫu trống. */
    @Transactional(readOnly = true)
    public byte[] template() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new EmployeeTemplateWriter().write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public EmployeeImportCheckDTO check(MultipartFile file) {
        return toDto(run(read(file)));
    }

    @Transactional(readOnly = true)
    public byte[] issueReport(MultipartFile file) {
        return EmployeeIssueReportWriter.write(toDto(run(read(file))).issues());
    }

    /**
     * Lưu cả file trong một giao dịch: khóa cấp mã, kiểm tra lại (dùng đúng mã của lần kiểm tra này), tạo phòng ban mới, tạo hồ
     * sơ theo thứ tự dòng, rồi gán quản lý. Lỗi bất kỳ làm hoàn tác toàn bộ.
     */
    public EmployeeImportResultDTO save(MultipartFile file, boolean warningsAcknowledged) {
        ParsedEmployees parsed = read(file);
        // Khóa trước khi kiểm tra: mã dự kiến tính trong lúc giữ khóa nên không ai chen được mã giữa chừng.
        employeeRepository.lockCodeAssignment();
        Result result = run(parsed);
        if (!result.canSave() || (result.warningCount() > 0 && !warningsAcknowledged)) {
            throw new EmployeeImportInvalidException(toDto(result));
        }

        Map<String, Department> newDepartments = new HashMap<>();
        List<String> createdDepartments = new ArrayList<>();
        for (EmployeeImportCheck.NewDepartment nd : result.newDepartments()) {
            Department d = new Department();
            d.setName(nd.name());
            d.setActive(true);
            newDepartments.put(nd.name(), departmentRepository.save(d));
            createdDepartments.add(nd.name());
        }

        Map<Integer, Employee> byRow = new LinkedHashMap<>();
        for (PlannedRow p : result.rows()) {
            Employee e = toEmployee(p);
            if (p.departmentId() != null) {
                e.setDepartment(departmentRepository.getReferenceById(p.departmentId()));
            } else if (p.newDepartmentName() != null) {
                e.setDepartment(newDepartments.get(p.newDepartmentName()));
            }
            byRow.put(p.row().excelRow(), employeeService.createImported(e));
        }
        // Lượt hai: mọi hồ sơ đã có id mới gán được quản lý là người trong cùng file.
        for (PlannedRow p : result.rows()) {
            EmployeeImportCheck.ManagerRef m = p.manager();
            if (m == null) {
                continue;
            }
            Employee manager = m.employeeId() != null ? employeeRepository.getReferenceById(m.employeeId()) : byRow.get(m.excelRow());
            byRow.get(p.row().excelRow()).setManager(manager);
        }

        List<EmployeeImportResultDTO.Created> created = byRow
            .entrySet()
            .stream()
            .map(en -> new EmployeeImportResultDTO.Created(en.getKey(), en.getValue().getFullName(), en.getValue().getCode()))
            .toList();
        LOG.info("Nhập {} hồ sơ bởi {}", created.size(), SecurityUtils.getCurrentUserLogin().orElse(null));
        return new EmployeeImportResultDTO(created.size(), created, createdDepartments);
    }

    private static Employee toEmployee(PlannedRow p) {
        EmployeeRow r = p.row();
        Employee e = new Employee();
        e.setCode(p.code());
        e.setPersonType(p.personType());
        e.setFullName(r.text(FULL_NAME));
        e.setGender(p.gender());
        e.setBirthDate(r.date(BIRTH_DATE));
        e.setIdNumber(p.idNumber());
        e.setIdIssueDate(r.date(ID_ISSUE_DATE));
        e.setIdIssuePlace(r.text(ID_ISSUE_PLACE));
        e.setResidence(r.text(RESIDENCE));
        e.setPhone(r.text(PHONE));
        e.setEmail(r.text(EMAIL));
        e.setEducation(r.text(EDUCATION));
        e.setTaxCode(r.text(TAX_CODE));
        e.setBankAccount(r.text(BANK_ACCOUNT));
        e.setBankName(r.text(BANK_NAME));
        e.setSocialInsuranceNo(r.text(SOCIAL_INSURANCE_NO));
        e.setSocialInsuranceStart(r.date(SOCIAL_INSURANCE_START));
        e.setSocialInsuranceSalary(r.integer(SOCIAL_INSURANCE_SALARY));
        e.setInsuranceExemption(p.exemption());
        e.setInsuranceExemptionNote(p.exemption() == null ? null : r.text(INSURANCE_EXEMPTION_NOTE));
        Long dependants = r.integer(DEPENDANTS);
        e.setDependants(dependants == null ? 0 : dependants.intValue());
        e.setPosition(r.text(POSITION));
        e.setJoinDate(r.date(JOIN_DATE));
        e.setNote(r.text(NOTE));
        return e;
    }

    private Result run(ParsedEmployees parsed) {
        return EmployeeImportCheck.run(parsed, new RepositoryDirectory(), LocalDate.now(clock));
    }

    /** Đọc file: chỉ nhận .xlsx (đuôi và chữ ký PK), ≤ 20 MB; sai mẫu (không nhận được cột) → lỗi cấp file. */
    private static ParsedEmployees read(MultipartFile file) {
        String name = UploadNames.displayName(file.getOriginalFilename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw notXlsx();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_BYTES);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') {
            throw notXlsx();
        }
        PoiSheetGrid grid;
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheet(EmployeeSheetParser.SHEET);
            grid = PoiSheetGrid.of(sheet != null ? sheet : wb.getSheetAt(0));
        } catch (IOException | RuntimeException e) {
            throw notXlsx();
        }
        ParsedEmployees parsed = new EmployeeSheetParser().parse(grid);
        String fileErrors = parsed
            .issues()
            .stream()
            .filter(i -> i.excelRow() == 0 && i.severity() == EmployeeIssue.Severity.ERROR)
            .map(EmployeeIssue::message)
            .collect(Collectors.joining(" "));
        if (!fileErrors.isEmpty()) {
            throw new EmployeeRuleException(fileErrors, "employeeimportlayout");
        }
        return parsed;
    }

    private static EmployeeRuleException notXlsx() {
        return new EmployeeRuleException("Chỉ nhận file Excel .xlsx (Excel 2007 trở lên), lưu từ mẫu nhập nhân viên", "employeeimportnotxlsx");
    }

    static EmployeeImportCheckDTO toDto(Result r) {
        List<EmployeeImportCheckDTO.Row> rows = r
            .rows()
            .stream()
            .map(p ->
                new EmployeeImportCheckDTO.Row(
                    p.row().excelRow(),
                    p.row().text(FULL_NAME),
                    p.idNumber(),
                    p.personType().name(),
                    p.code(),
                    p.codeProvisional(),
                    p.row().text(DEPARTMENT),
                    p.row().text(MANAGER)
                )
            )
            .toList();
        List<EmployeeImportCheckDTO.Issue> issues = r
            .issues()
            .stream()
            .map(i ->
                new EmployeeImportCheckDTO.Issue(
                    i.severity().name(),
                    i.excelRow(),
                    i.fullName(),
                    i.field() == null ? null : i.field().name(),
                    i.field() == null ? null : i.field().header(),
                    i.message()
                )
            )
            .toList();
        List<EmployeeImportCheckDTO.NewDepartment> newDepartments = r
            .newDepartments()
            .stream()
            .map(d -> new EmployeeImportCheckDTO.NewDepartment(d.name(), List.copyOf(d.excelRows())))
            .toList();
        return new EmployeeImportCheckDTO(rows.size(), rows, issues, newDepartments, r.errorCount(), r.warningCount(), r.canSave());
    }

    /** Tra cứu trên repository, tạo mới cho mỗi lần kiểm tra; phòng ban và kết quả tra cứu được nhớ trong lần đó. */
    private final class RepositoryDirectory implements ImportDirectory {

        private List<DepartmentRef> departments;
        private final Map<String, Optional<Long>> byCode = new HashMap<>();
        private final Map<String, Optional<Long>> byIdNumber = new HashMap<>();

        @Override
        public List<String> codesStartingWith(String prefix) {
            return employeeRepository.findCodesStartingWith(prefix);
        }

        @Override
        public Optional<Long> employeeIdByCode(String code) {
            if (code == null) {
                return Optional.empty();
            }
            return byCode.computeIfAbsent(code, c -> employeeRepository.findOneByCode(c).map(Employee::getId));
        }

        @Override
        public Optional<Long> employeeIdByIdNumber(String trimmedIdNumber) {
            if (trimmedIdNumber == null) {
                return Optional.empty();
            }
            return byIdNumber.computeIfAbsent(trimmedIdNumber, id ->
                employeeRepository.findAllByTrimmedIdNumber(id).stream().findFirst().map(Employee::getId)
            );
        }

        @Override
        public List<DepartmentRef> departments() {
            if (departments == null) {
                departments = departmentRepository
                    .findAll()
                    .stream()
                    .map(d -> new DepartmentRef(d.getId(), d.getName(), Boolean.TRUE.equals(d.getActive())))
                    .toList();
            }
            return departments;
        }
    }
}
