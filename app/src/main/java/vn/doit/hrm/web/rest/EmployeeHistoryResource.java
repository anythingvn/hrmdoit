package vn.doit.hrm.web.rest;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.EmployeeHistoryService;
import vn.doit.hrm.service.dto.HistoryEntryDTO;

/**
 * Nhật ký thay đổi hồ sơ nhân viên (chỉ Quản trị và Nhân sự).
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeHistoryResource {

    private final EmployeeHistoryService historyService;

    private final EmployeeRepository employeeRepository;

    public EmployeeHistoryResource(EmployeeHistoryService historyService, EmployeeRepository employeeRepository) {
        this.historyService = historyService;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    public ResponseEntity<List<HistoryEntryDTO>> history(@PathVariable("id") Long id) {
        if (!employeeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(historyService.history(id));
    }
}
