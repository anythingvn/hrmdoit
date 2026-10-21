package vn.doit.hrm.web.rest;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.payroll.PayrollViewService;
import vn.doit.hrm.service.payroll.dto.PayrollViewDTOs;

/** Bảng lương theo nhân viên (khối trên hồ sơ) và danh sách 36 trường của mẫu: chỉ Kế toán, Quản trị. */
@RestController
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.KE_TOAN + "')")
public class PayrollInfoResource {

    private final PayrollViewService viewService;

    public PayrollInfoResource(PayrollViewService viewService) {
        this.viewService = viewService;
    }

    @GetMapping("/api/employees/{id}/payroll")
    public List<PayrollViewDTOs.EmployeePayroll> employeePayroll(@PathVariable Long id) {
        return viewService.forEmployee(id);
    }

    @GetMapping("/api/payroll/fields")
    public List<PayrollViewDTOs.Field> fields() {
        return viewService.fields();
    }
}
