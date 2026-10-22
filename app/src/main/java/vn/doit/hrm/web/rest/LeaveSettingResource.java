package vn.doit.hrm.web.rest;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.leave.LeaveSettingService;
import vn.doit.hrm.service.leave.dto.LeaveSettingDTO;

/** Cài đặt nghỉ phép: mọi tài khoản đăng nhập đọc được; chỉ Quản trị sửa. */
@RestController
@RequestMapping("/api/leave/settings")
public class LeaveSettingResource {

    private final LeaveSettingService service;

    public LeaveSettingResource(LeaveSettingService service) {
        this.service = service;
    }

    @GetMapping("")
    public LeaveSettingDTO get() {
        return service.get();
    }

    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @PutMapping("")
    public LeaveSettingDTO update(@Valid @RequestBody LeaveSettingDTO dto) {
        return service.update(dto);
    }
}
