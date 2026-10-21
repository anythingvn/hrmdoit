package vn.doit.hrm.web.rest;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.payroll.PayrollImportService;
import vn.doit.hrm.service.payroll.dto.ImportCheckDTO;
import vn.doit.hrm.service.payroll.dto.ImportSummaryDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/** Bản nháp import bảng lương: chỉ Kế toán và Quản trị. */
@RestController
@RequestMapping("/api/payroll/imports")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.KE_TOAN + "')")
public class PayrollImportResource {

    static final String ENTITY_NAME = "payrollImport";

    private final PayrollImportService service;

    public PayrollImportResource(PayrollImportService service) {
        this.service = service;
    }

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportCheckDTO> create(@RequestParam("period") String period, @RequestParam("file") MultipartFile file) {
        ImportCheckDTO dto = rule(() -> service.create(period, file));
        return ResponseEntity.created(URI.create("/api/payroll/imports/" + dto.id())).body(dto);
    }

    @GetMapping("")
    public List<ImportSummaryDTO> list(@RequestParam(required = false) PayrollImportStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public ImportCheckDTO check(@PathVariable Long id) {
        return service.check(id);
    }

    @PostMapping("/{id}/acknowledge-warnings")
    public ImportCheckDTO acknowledge(@PathVariable Long id) {
        return rule(() -> service.acknowledgeWarnings(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        rule(() -> {
            service.cancel(id);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/issues.xlsx")
    public ResponseEntity<byte[]> issues(@PathVariable Long id) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, DocumentTemplateResource.attachment("loi-bang-luong-" + id + ".xlsx"))
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(service.issueReport(id));
    }

    static <T> T rule(Supplier<T> action) {
        try {
            return action.get();
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
    }
}
