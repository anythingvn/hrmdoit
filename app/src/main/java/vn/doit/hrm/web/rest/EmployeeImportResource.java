package vn.doit.hrm.web.rest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.employee.EmployeeImportService;
import vn.doit.hrm.service.employee.dto.EmployeeImportCheckDTO;
import vn.doit.hrm.service.employee.dto.EmployeeImportResultDTO;

/** Nhập danh sách nhân viên từ Excel: chỉ Quản trị và HR (như tạo hồ sơ). */
@RestController
@RequestMapping("/api/employees/import")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
public class EmployeeImportResource {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EmployeeImportService service;

    public EmployeeImportResource(EmployeeImportService service) {
        this.service = service;
    }

    @GetMapping("/template.xlsx")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment("mau-nhap-nhan-vien.xlsx"))
            .contentType(XLSX)
            .body(service.template());
    }

    /** Đọc và kiểm tra, không ghi gì. */
    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeImportCheckDTO check(@RequestParam("file") MultipartFile file) {
        return service.check(file);
    }

    @PostMapping(value = "/issues.xlsx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> issues(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment("loi-nhap-nhan-vien.xlsx"))
            .contentType(XLSX)
            .body(service.issueReport(file));
    }

    /** Kiểm tra lại rồi lưu tất cả trong một giao dịch; còn lỗi hoặc cảnh báo chưa xác nhận → 400 kèm kết quả kiểm tra. */
    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeeImportResultDTO> save(
        @RequestParam("file") MultipartFile file,
        @RequestParam(name = "warningsAcknowledged", defaultValue = "false") boolean warningsAcknowledged
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(file, warningsAcknowledged));
    }
}
