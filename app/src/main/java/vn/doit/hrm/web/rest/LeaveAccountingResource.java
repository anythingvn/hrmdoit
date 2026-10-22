package vn.doit.hrm.web.rest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.leave.LeaveAccountingSummaryService;
import vn.doit.hrm.service.leave.dto.LeaveAccountingDTO;

/** Tổng hợp nghỉ và làm thêm theo tháng cho kế toán: Kế toán, HR, Quản trị. */
@RestController
@RequestMapping("/api/leave/accounting")
@PreAuthorize(
    "hasAnyAuthority('" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.ADMIN + "')"
)
public class LeaveAccountingResource {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final LeaveAccountingSummaryService service;

    public LeaveAccountingResource(LeaveAccountingSummaryService service) {
        this.service = service;
    }

    /** {@code month} = "yyyy-MM". */
    @GetMapping("")
    public LeaveAccountingDTO summary(@RequestParam(name = "month") String month) {
        return service.summary(month);
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> export(@RequestParam(name = "month") String month) {
        byte[] body = service.export(month);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment("tong-hop-nghi-lam-them-" + month + ".xlsx"))
            .contentType(XLSX)
            .body(body);
    }
}
