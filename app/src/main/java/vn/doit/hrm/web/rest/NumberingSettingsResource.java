package vn.doit.hrm.web.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.dto.NumberingSettingsDTO;
import vn.doit.hrm.service.numbering.NumberingSettingsService;

/** Cấu hình số văn bản và mã nhân viên: Quản trị, HR, Kế toán xem; chỉ Quản trị sửa. */
@RestController
@RequestMapping("/api/company-setting/numbering")
public class NumberingSettingsResource {

    private final NumberingSettingsService service;

    public NumberingSettingsResource(NumberingSettingsService service) {
        this.service = service;
    }

    @GetMapping("")
    @PreAuthorize(
        "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "')"
    )
    public NumberingSettingsDTO get() {
        return service.view();
    }

    @PutMapping("")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public NumberingSettingsDTO update(@RequestBody NumberingSettingsDTO body) {
        return service.update(body);
    }
}
