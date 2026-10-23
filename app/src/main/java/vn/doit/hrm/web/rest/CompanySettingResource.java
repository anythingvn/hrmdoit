package vn.doit.hrm.web.rest;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.CompanySettingService;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.dto.AttendanceNoticeSettingDTO;
import vn.doit.hrm.service.dto.CompanySettingDTO;

/**
 * Cài đặt công ty: Quản trị và HR xem (có người đại diện), chỉ Quản trị sửa tên, người đại diện, logo và cài đặt thông báo tự chấm
 * công.
 */
@RestController
@RequestMapping("/api/company-setting")
public class CompanySettingResource {

    private final CompanySettingService service;

    public CompanySettingResource(CompanySettingService service) {
        this.service = service;
    }

    @GetMapping("")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    public CompanySettingDTO get() {
        return service.view();
    }

    @PutMapping("")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public CompanySettingDTO update(@RequestBody CompanySettingDTO body) {
        return service.update(body);
    }

    /** Cài đặt tự chấm công: nội dung thông báo thu thập vị trí, số ngày giữ tọa độ/IP. Quản trị và HR xem. */
    @GetMapping("/attendance-notice")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    public AttendanceNoticeSettingDTO getAttendanceNotice() {
        return service.attendanceNotice();
    }

    /** Chỉ Quản trị sửa; nội dung đổi thì phiên bản tăng 1. */
    @PutMapping("/attendance-notice")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public AttendanceNoticeSettingDTO updateAttendanceNotice(@RequestBody AttendanceNoticeSettingDTO body) {
        return service.updateAttendanceNotice(body);
    }

    @PostMapping("/logo")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public CompanySettingDTO uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file.getSize() > CompanySettingService.MAX_LOGO_BYTES) {
            // Không đọc cả file lớn vào bộ nhớ chỉ để báo lỗi.
            throw new EmployeeRuleException("Logo tối đa 1 MB", "logotoolarge");
        }
        try {
            return service.uploadLogo(file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public CompanySettingDTO resetLogo() {
        return service.resetLogo();
    }
}
