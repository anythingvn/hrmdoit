package vn.doit.hrm.service.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hr.employee.EmployeeField.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hr.employee.EmployeeField;
import vn.doit.hr.employee.EmployeeRow;
import vn.doit.hr.employee.ParsedEmployees;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.DocumentSequenceRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.DocumentNumberService;
import vn.doit.hrm.service.EmployeeService;
import vn.doit.hrm.service.dto.DocumentSequenceDTO;
import vn.doit.hrm.service.dto.NumberingSettingsDTO;
import vn.doit.hrm.service.employee.EmployeeImportCheck;
import vn.doit.hrm.service.employee.ImportDirectory;

/** Cấu hình số văn bản / mã nhân viên được áp dụng khi cấp mới; dữ liệu đã có không đổi. */
@IntegrationTest
class NumberingAppliedIT {

    @Autowired
    private DocumentNumberService numberService;

    @Autowired
    private NumberingSettingsService settingsService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private DocumentSequenceRepository sequenceRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TransactionTemplate tx;

    private final List<Long> createdEmployees = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void clean() {
        sequenceRepository.deleteAll();
        tx.executeWithoutResult(s -> {
            createdEmployees.forEach(id -> {
                contractRepository.findByEmployeeId(id).forEach(contractRepository::delete);
                employeeRepository.deleteById(id);
            });
        });
        createdEmployees.clear();
        // Đặt lại qua service để không lệch bộ nhớ đệm của cấu hình.
        configure(null, null, null, null, null);
    }

    private void configure(String docPrefix, Map<DocumentType, String> codes, String staff, String intern, String service) {
        settingsService.update(new NumberingSettingsDTO(docPrefix, codes, staff, intern, service, null));
    }

    private Employee newEmployee(String code, PersonType type) {
        Long id = tx.execute(s ->
            employeeRepository
                .save(
                    new Employee()
                        .code(code)
                        .fullName("Người Thử " + code)
                        .personType(type)
                        .dependants(0)
                        .status(EmploymentStatus.DANG_LAM_VIEC)
                )
                .getId()
        );
        createdEmployees.add(id);
        return employeeRepository.findById(id).orElseThrow();
    }

    private void contract(Employee e, String no) {
        tx.executeWithoutResult(s ->
            contractRepository.save(
                new EmploymentContract()
                    .contractNo(no)
                    .type(ContractType.HDLD_KXDTH)
                    .startDate(LocalDate.of(2026, 1, 1))
                    .status(ContractStatus.DA_KY)
                    .employee(e)
            )
        );
    }

    @Test
    void docPrefixChangeMidYearContinues() {
        assertThat(numberService.next(DocumentType.HDLD, 2026)).isEqualTo("DOIT/2026/HĐLĐ/1");
        configure("ABC", null, null, null, null);
        assertThat(numberService.next(DocumentType.HDLD, 2026)).isEqualTo("ABC/2026/HĐLĐ/2");
    }

    @Test
    void skipsManualNumberUnderNewPrefix() {
        assertThat(numberService.next(DocumentType.HDLD, 2026)).isEqualTo("DOIT/2026/HĐLĐ/1");
        configure("ABC", null, null, null, null);
        contract(newEmployee("NUM-IT-1", PersonType.NHAN_VIEN), "ABC/2026/HĐLĐ/2");
        assertThat(numberService.next(DocumentType.HDLD, 2026)).isEqualTo("ABC/2026/HĐLĐ/3");
    }

    @Test
    void customTypeCode() {
        Map<DocumentType, String> codes = new EnumMap<>(DocumentType.class);
        codes.put(DocumentType.HDLD, "LD");
        configure("ABC", codes, null, null, null);
        assertThat(numberService.next(DocumentType.HDLD, 2026)).isEqualTo("ABC/2026/LD/1");
        DocumentSequenceDTO hdld = numberService
            .list(2026)
            .stream()
            .filter(d -> d.type() == DocumentType.HDLD)
            .findFirst()
            .orElseThrow();
        assertThat(hdld.code()).isEqualTo("LD");
        assertThat(hdld.nextNumber()).isEqualTo("ABC/2026/LD/2");
    }

    @Test
    void staffPrefixChangeStartsNewSequence() {
        newEmployee("DOIT2601", PersonType.NHAN_VIEN);
        assertThat(employeeService.nextCode(PersonType.NHAN_VIEN, LocalDate.of(2026, 3, 1))).contains("DOIT2602");
        configure(null, null, "NV", null, null);
        assertThat(employeeService.nextCode(PersonType.NHAN_VIEN, LocalDate.of(2026, 3, 1))).contains("NV2601");
        assertThat(employeeRepository.findCodesStartingWith("DOIT")).contains("DOIT2601");
    }

    @Test
    void servicePrefixKeepsCounter() {
        Employee first = employeeService.createImported(
            new Employee().fullName("Dịch vụ 1").personType(PersonType.DICH_VU).dependants(0).status(EmploymentStatus.DANG_LAM_VIEC)
        );
        createdEmployees.add(first.getId());
        assertThat(first.getCode()).startsWith("HDDV");
        long before = Long.parseLong(first.getCode().substring(4));
        configure(null, null, null, null, "DV");
        Employee second = employeeService.createImported(
            new Employee().fullName("Dịch vụ 2").personType(PersonType.DICH_VU).dependants(0).status(EmploymentStatus.DANG_LAM_VIEC)
        );
        createdEmployees.add(second.getId());
        assertThat(second.getCode()).startsWith("DV");
        assertThat(Long.parseLong(second.getCode().substring(2))).isGreaterThan(before);
        assertThat(employeeRepository.findById(first.getId()).orElseThrow().getCode()).isEqualTo(first.getCode());
    }

    @Test
    void importUsesConfiguredPrefixes() {
        configure(null, null, "NV", null, "DV");
        NumberingSettings s = settingsService.current();
        ImportDirectory dir = new ImportDirectory() {
            public List<String> codesStartingWith(String prefix) {
                return List.of();
            }

            public Optional<Long> employeeIdByCode(String code) {
                return Optional.empty();
            }

            public Optional<Long> employeeIdByIdNumber(String id) {
                return Optional.empty();
            }

            public boolean isArchived(long id) {
                return false;
            }

            public List<DepartmentRef> departments() {
                return List.of();
            }
        };
        EmployeeRow ok = row(2, "NV2605");
        EmployeeRow service = row(3, "DV12");
        EmployeeRow odd = row(4, "XYZ9");
        EmployeeImportCheck.Result r = EmployeeImportCheck.run(
            new ParsedEmployees(Map.of(), List.of(ok, service, odd), List.of()),
            dir,
            LocalDate.of(2026, 5, 1),
            s
        );
        assertThat(
            r
                .issues()
                .stream()
                .filter(i -> i.excelRow() == 2)
        ).isEmpty();
        assertThat(
            r
                .issues()
                .stream()
                .filter(i -> i.excelRow() == 3)
                .map(i -> i.message())
        ).anyMatch(m -> m.contains("Mã bắt đầu bằng DV dành riêng cho HĐ dịch vụ"));
        assertThat(
            r
                .issues()
                .stream()
                .filter(i -> i.excelRow() == 4)
                .map(i -> i.message())
        ).anyMatch(m -> m.contains("NV/TT + số"));
    }

    private static EmployeeRow row(int excelRow, String code) {
        Map<EmployeeField, Object> v = new EnumMap<>(EmployeeField.class);
        v.put(FULL_NAME, "Người " + excelRow);
        v.put(ID_NUMBER, "0791234567" + (10 + excelRow));
        v.put(CODE, code);
        return new EmployeeRow(excelRow, v);
    }
}
