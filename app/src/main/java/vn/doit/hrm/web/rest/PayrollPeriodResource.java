package vn.doit.hrm.web.rest;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.payroll.PayrollPeriodService;
import vn.doit.hrm.service.payroll.PayrollViewService;
import vn.doit.hrm.service.payroll.dto.PayrollViewDTOs;
import vn.doit.hrm.service.payroll.dto.PeriodDTO;

/** Kỳ lương: xem (Kế toán, Quản trị); duyệt và mở lại (chỉ Quản trị). */
@RestController
@RequestMapping("/api/payroll/periods")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.KE_TOAN + "')")
public class PayrollPeriodResource {

    private static final String ADMIN = "hasAuthority('" + AuthoritiesConstants.ADMIN + "')";

    private final PayrollPeriodService periodService;
    private final PayrollViewService viewService;

    public PayrollPeriodResource(PayrollPeriodService periodService, PayrollViewService viewService) {
        this.periodService = periodService;
        this.viewService = viewService;
    }

    @GetMapping("")
    public List<PayrollViewDTOs.PeriodSummary> list() {
        return viewService.periods();
    }

    @GetMapping("/{period}")
    public PayrollViewDTOs.PeriodDetail detail(@PathVariable String period) {
        return PayrollImportResource.rule(() -> viewService.detail(period));
    }

    @GetMapping("/{period}/versions/{version}/lines")
    public List<PayrollViewDTOs.Line> lines(@PathVariable String period, @PathVariable int version) {
        return PayrollImportResource.rule(() -> viewService.lines(period, version));
    }

    @GetMapping("/{period}/versions/{version}/file")
    public ResponseEntity<byte[]> file(@PathVariable String period, @PathVariable int version) {
        PayrollViewService.OriginalFile f = PayrollImportResource.rule(() -> viewService.file(period, version));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, DocumentTemplateResource.attachment(f.fileName()))
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(f.content());
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
