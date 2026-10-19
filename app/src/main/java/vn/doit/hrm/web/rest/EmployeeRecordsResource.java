package vn.doit.hrm.web.rest;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.AllowanceService;
import vn.doit.hrm.service.EmploymentContractService;
import vn.doit.hrm.service.SalaryChangeService;
import vn.doit.hrm.service.dto.AllowanceDTO;
import vn.doit.hrm.service.dto.EmploymentContractDTO;
import vn.doit.hrm.service.dto.SalaryChangeDTO;

/**
 * Hợp đồng, điều chỉnh lương và phụ cấp của một nhân viên (cho trang chi tiết). Chỉ đọc; 4 vai trò DOIT đều xem được.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeRecordsResource {

    private static final String READ =
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')";

    private final EmployeeRepository employeeRepository;

    private final EmploymentContractService contractService;

    private final SalaryChangeService salaryChangeService;

    private final AllowanceService allowanceService;

    public EmployeeRecordsResource(
        EmployeeRepository employeeRepository,
        EmploymentContractService contractService,
        SalaryChangeService salaryChangeService,
        AllowanceService allowanceService
    ) {
        this.employeeRepository = employeeRepository;
        this.contractService = contractService;
        this.salaryChangeService = salaryChangeService;
        this.allowanceService = allowanceService;
    }

    @PreAuthorize(READ)
    @GetMapping("/{id}/contracts")
    public ResponseEntity<List<EmploymentContractDTO>> contracts(@PathVariable("id") Long id) {
        return ofEmployee(id, () -> contractService.findByEmployee(id));
    }

    @PreAuthorize(READ)
    @GetMapping("/{id}/salary-changes")
    public ResponseEntity<List<SalaryChangeDTO>> salaryChanges(@PathVariable("id") Long id) {
        return ofEmployee(id, () -> salaryChangeService.findByEmployee(id));
    }

    @PreAuthorize(READ)
    @GetMapping("/{id}/allowances")
    public ResponseEntity<List<AllowanceDTO>> allowances(@PathVariable("id") Long id) {
        return ofEmployee(id, () -> allowanceService.findByEmployee(id));
    }

    private <T> ResponseEntity<List<T>> ofEmployee(Long id, Supplier<List<T>> load) {
        if (!employeeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(load.get());
    }
}
