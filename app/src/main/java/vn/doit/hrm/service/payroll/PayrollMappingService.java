package vn.doit.hrm.service.payroll;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollImport;
import vn.doit.hrm.domain.PayrollImportMapping;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollImportMappingRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.ArchivedEmployeeGuard;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.EmployeeService;
import vn.doit.hrm.service.VietnameseText;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.payroll.dto.CandidateDTO;
import vn.doit.hrm.service.payroll.dto.ImportCheckDTO;
import vn.doit.hrm.service.payroll.dto.NewEmployeeDTO;
import vn.doit.hrm.service.payroll.dto.RowDTO;

/**
 * Gắn dòng lương với hồ sơ (Kế toán, Quản trị): chọn hồ sơ có gợi ý theo họ tên, hoặc tạo hồ sơ rút gọn rồi gắn luôn. Một hồ sơ
 * chỉ gắn với một dòng trong một lần import.
 */
@Service
@Transactional
public class PayrollMappingService {

    private static final int MAX_CANDIDATES = 10;

    private final PayrollImportService importService;
    private final PayrollImportMappingRepository mappingRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final Clock clock;

    public PayrollMappingService(
        PayrollImportService importService,
        PayrollImportMappingRepository mappingRepository,
        EmployeeRepository employeeRepository,
        EmployeeService employeeService,
        Clock clock
    ) {
        this.importService = importService;
        this.mappingRepository = mappingRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.clock = clock;
    }

    public ImportCheckDTO map(Long importId, int excelRow, Long employeeId) {
        PayrollImport draft = importService.open(importId);
        PayrollImportService.Loaded loaded = importService.load(importId);
        row(loaded, excelRow);
        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ"));
        if (employee.isArchived()) {
            throw new EmployeeRuleException(ArchivedEmployeeGuard.MESSAGE, ArchivedEmployeeGuard.KEY);
        }
        for (Map.Entry<Integer, Employee> e : loaded.result().resolved().entrySet()) {
            if (e.getKey() != excelRow && e.getValue().getId().equals(employeeId)) {
                RowDTO other = row(loaded, e.getKey());
                throw new EmployeeRuleException(
                    "Hồ sơ " + employee.getCode() + " – " + employee.getFullName() + " đã gắn với dòng " + e.getKey() + " (" + other.fileName() + ")",
                    "employeemappedtwice"
                );
            }
        }
        PayrollImportMapping m = mappingRepository.findByPayrollImportIdAndExcelRow(importId, excelRow).orElseGet(PayrollImportMapping::new);
        m.setPayrollImport(draft);
        m.setExcelRow(excelRow);
        m.setEmployee(employee);
        m.setMappedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        m.setMappedAt(Instant.now(clock));
        mappingRepository.saveAndFlush(m);
        return importService.toDto(importService.load(importId));
    }

    public ImportCheckDTO unmap(Long importId, int excelRow) {
        importService.open(importId);
        row(importService.load(importId), excelRow);
        mappingRepository.findByPayrollImportIdAndExcelRow(importId, excelRow).ifPresent(m -> {
            mappingRepository.delete(m);
            mappingRepository.flush();
        });
        return importService.toDto(importService.load(importId));
    }

    /** Gợi ý hồ sơ cho một dòng: theo {@code q} hoặc họ tên trong file; khớp tên hoàn toàn đứng trước. */
    @Transactional(readOnly = true)
    public List<CandidateDTO> candidates(Long importId, int excelRow, String q) {
        RowDTO row = row(importService.load(importId), excelRow);
        String folded = VietnameseText.fold(q == null || q.isBlank() ? row.fileName() : q);
        String[] tokens = folded.isEmpty() ? new String[0] : folded.split(" ");
        if (tokens.length == 0) {
            return List.of();
        }
        String fileName = VietnameseText.fold(row.fileName());
        return employeeRepository
            .findTop200BySearchTextContainingAndArchivedAtIsNullOrderByFullNameAsc(tokens[0])
            .stream()
            .filter(e -> Arrays.stream(tokens).allMatch(t -> e.getSearchText() != null && e.getSearchText().contains(t)))
            .map(e -> {
                String name = VietnameseText.fold(e.getFullName());
                boolean exact = name.equals(folded) || name.equals(fileName);
                return new CandidateDTO(e.getId(), e.getCode(), e.getFullName(), e.getPosition(), e.getPersonType(), e.getStatus(), exact);
            })
            .sorted(Comparator.comparing((CandidateDTO c) -> !c.exactName()).thenComparing(CandidateDTO::fullName))
            .limit(MAX_CANDIDATES)
            .toList();
    }

    /** Tạo hồ sơ rút gọn (qua {@link EmployeeService}: mã HDDVn tự sinh, kiểm trùng mã) rồi gắn với dòng. */
    public ImportCheckDTO createAndMap(Long importId, int excelRow, NewEmployeeDTO body) {
        importService.open(importId);
        row(importService.load(importId), excelRow);
        if (body.fullName() == null || body.fullName().isBlank()) {
            throw new EmployeeRuleException("Cần họ tên", "fullnamerequired");
        }
        EmployeeDTO dto = new EmployeeDTO();
        dto.setFullName(body.fullName().trim());
        dto.setPersonType(body.personType() == null ? PersonType.NHAN_VIEN : body.personType());
        dto.setCode(dto.getPersonType() == PersonType.DICH_VU ? null : body.code());
        dto.setStatus(EmploymentStatus.DANG_LAM_VIEC);
        dto.setDependants(0);
        EmployeeDTO saved = employeeService.save(dto);
        return map(importId, excelRow, saved.getId());
    }

    private static RowDTO row(PayrollImportService.Loaded loaded, int excelRow) {
        return loaded
            .result()
            .rows()
            .stream()
            .filter(r -> r.excelRow() == excelRow)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có dòng " + excelRow + " trong file"));
    }
}
