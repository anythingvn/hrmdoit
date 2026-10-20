package vn.doit.hrm.web.rest;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.security.AuthoritiesConstants;

/**
 * Cấu hình tài khoản cho người tạo tài khoản (Quản trị, Nhân sự): mật khẩu mặc định đang dùng
 * ({@code application.account.default-password}) để giao diện hiện đúng giá trị. Không cache, không ghi log.
 */
@RestController
@RequestMapping("/api/admin/account-settings")
public class AccountSettingsResource {

    private final ApplicationProperties applicationProperties;

    public AccountSettingsResource(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    /** {@code GET /admin/account-settings/default-password} : {@code {"defaultPassword": "..."}}. */
    @GetMapping("/default-password")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    public ResponseEntity<Map<String, String>> defaultPassword() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("Pragma", "no-cache")
            .body(Map.of("defaultPassword", applicationProperties.getAccount().getDefaultPassword()));
    }
}
