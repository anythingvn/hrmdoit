package vn.doit.hrm.web.rest;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.payroll.PayrollImportService;
import vn.doit.hrm.service.payroll.PayrollMappingService;
import vn.doit.hrm.service.payroll.PayrollPeriodService;
import vn.doit.hrm.service.payroll.PayrollTemplateService;
import vn.doit.hrm.service.payroll.dto.ConfirmedDTO;
import vn.doit.hrm.service.payroll.dto.CandidateDTO;
import vn.doit.hrm.service.payroll.dto.NewEmployeeDTO;
import vn.doit.hrm.service.payroll.dto.ImportCheckDTO;
import vn.doit.hrm.service.payroll.dto.ImportSummaryDTO;

/** Bản nháp import bảng lương: chỉ Kế toán và Quản trị. */
@RestController
@RequestMapping("/api/payroll/imports")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.KE_TOAN + "')")
public class PayrollImportResource {

    private final PayrollImportService service;
    private final PayrollMappingService mappingService;
    private final PayrollPeriodService periodService;
    private final PayrollTemplateService templateService;

    public PayrollImportResource(
        PayrollImportService service,
        PayrollMappingService mappingService,
        PayrollPeriodService periodService,
        PayrollTemplateService templateService
    ) {
        this.service = service;
        this.mappingService = mappingService;
        this.periodService = periodService;
        this.templateService = templateService;
    }

    /** Xác nhận bản nháp thành phiên bản mới của kỳ. */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ConfirmedDTO> confirm(@PathVariable Long id) {
        ConfirmedDTO dto = periodService.confirm(id);
        return ResponseEntity.created(URI.create("/api/payroll/periods/" + dto.period())).body(dto);
    }

    public record MapRowVM(Long employeeId) {}

    @PutMapping("/{id}/rows/{excelRow}/employee")
    public ImportCheckDTO map(@PathVariable Long id, @PathVariable int excelRow, @RequestBody MapRowVM body) {
        return mappingService.map(id, excelRow, body.employeeId());
    }

    @DeleteMapping("/{id}/rows/{excelRow}/employee")
    public ImportCheckDTO unmap(@PathVariable Long id, @PathVariable int excelRow) {
        return mappingService.unmap(id, excelRow);
    }

    @GetMapping("/{id}/rows/{excelRow}/candidates")
    public List<CandidateDTO> candidates(@PathVariable Long id, @PathVariable int excelRow, @RequestParam(required = false) String q) {
        return mappingService.candidates(id, excelRow, q);
    }

    /** Tạo hồ sơ rút gọn (Kế toán được tạo, không được sửa hồ sơ có sẵn) rồi gắn với dòng. */
    @PostMapping("/{id}/rows/{excelRow}/new-employee")
    public ImportCheckDTO createEmployee(@PathVariable Long id, @PathVariable int excelRow, @RequestBody NewEmployeeDTO body) {
        return mappingService.createAndMap(id, excelRow, body);
    }

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportCheckDTO> create(@RequestParam("period") String period, @RequestParam("file") MultipartFile file) {
        ImportCheckDTO dto = service.create(period, file);
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
        return service.acknowledgeWarnings(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Không có {@code period}: file mẫu bảng lương trống (đúng các cột hệ thống đọc được, kèm sheet hướng dẫn) cho lần import đầu.
     * Có {@code period} (yyyy-MM): file bảng lương có ngày công của tháng đó, điền sẵn từ bảng công đã chốt; chưa chốt → 400
     * {@code attendancenotclosed}.
     */
    @GetMapping("/template.xlsx")
    public ResponseEntity<byte[]> template(@RequestParam(required = false) String period) throws java.io.IOException {
        if (period != null) {
            byte[] body = templateService.prefilled(period);
            return xlsx("bang-luong-" + period.trim() + ".xlsx", body);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        new vn.doit.hr.payroll.PayrollTemplateWriter().write(out);
        return xlsx("mau-bang-luong.xlsx", out.toByteArray());
    }

    private static ResponseEntity<byte[]> xlsx(String fileName, byte[] body) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment(fileName))
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }

    @GetMapping("/{id}/issues.xlsx")
    public ResponseEntity<byte[]> issues(@PathVariable Long id) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment("loi-bang-luong-" + id + ".xlsx"))
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(service.issueReport(id));
    }
}
