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
import vn.doit.hrm.service.leave.LeaveOpeningImportService;
import vn.doit.hrm.service.leave.dto.LeaveOpeningCheckDTO;
import vn.doit.hrm.service.leave.dto.LeaveOpeningResultDTO;

/** Nhập số dư đầu kỳ (phép năm, nghỉ bù) từ Excel: chỉ Quản trị và HR. */
@RestController
@RequestMapping("/api/leave/opening")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
public class LeaveOpeningResource {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final LeaveOpeningImportService service;

    public LeaveOpeningResource(LeaveOpeningImportService service) {
        this.service = service;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment("mau-so-du-dau-ky.xlsx"))
            .contentType(XLSX)
            .body(service.template());
    }

    /** Đọc và kiểm tra, không ghi gì. */
    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LeaveOpeningCheckDTO check(@RequestParam("file") MultipartFile file) {
        return service.check(file);
    }

    /** Kiểm tra lại rồi ghi tất cả trong một giao dịch; còn lỗi → 400 kèm kết quả kiểm tra ở thuộc tính {@code check}. */
    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LeaveOpeningResultDTO> save(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(file));
    }
}
