package vn.doit.hrm.web.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.payroll.PayrollPeriodService;
import vn.doit.hrm.service.payroll.dto.PeriodDTO;

/** Kỳ lương: xem (Kế toán, Quản trị); duyệt và mở lại (chỉ Quản trị). */
@RestController
@RequestMapping("/api/payroll/periods")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.KE_TOAN + "')")
public class PayrollPeriodResource {

    private static final String ADMIN = "hasAuthority('" + AuthoritiesConstants.ADMIN + "')";

    private final PayrollPeriodService periodService;

    public PayrollPeriodResource(PayrollPeriodService periodService) {
        this.periodService = periodService;
    }

    public record ReopenVM(String reason) {}

    @PostMapping("/{period}/approve")
    @PreAuthorize(ADMIN)
    public PeriodDTO approve(@PathVariable String period) {
        return PayrollImportResource.rule(() -> periodService.approve(period));
    }

    @PostMapping("/{period}/reopen")
    @PreAuthorize(ADMIN)
    public PeriodDTO reopen(@PathVariable String period, @RequestBody ReopenVM body) {
        return PayrollImportResource.rule(() -> periodService.reopen(period, body.reason()));
    }
}
