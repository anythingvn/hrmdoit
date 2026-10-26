package vn.doit.hrm.web.rest;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.aop.logging.NoArgumentLogging;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.DefaultPasswordService;
import vn.doit.hrm.service.DefaultPasswordService.View;

/**
 * Cấu hình tài khoản cho người tạo tài khoản (Quản trị, Nhân sự): xem mật khẩu mặc định đang dùng và nguồn của nó; Quản trị
 * đặt mật khẩu mặc định trên web. Không cache, không ghi log.
 */
@NoArgumentLogging
@RestController
@RequestMapping("/api/admin/account-settings")
public class AccountSettingsResource {

    /** toString không in mật khẩu. */
    public record DefaultPasswordRequest(String defaultPassword) {
        @Override
        public String toString() {
            return "DefaultPasswordRequest[defaultPassword=" + (defaultPassword == null ? "null" : "[đã ẩn]") + "]";
        }
    }

    private final DefaultPasswordService defaultPasswordService;

    public AccountSettingsResource(DefaultPasswordService defaultPasswordService) {
        this.defaultPasswordService = defaultPasswordService;
    }

    /** {@code GET /admin/account-settings/default-password} : {@code {"defaultPassword": "...", "source": "WEB|ENV|DEFAULT"}}. */
    @GetMapping("/default-password")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    public ResponseEntity<View> defaultPassword() {
        return noStore(defaultPasswordService.view());
    }

    /** {@code PUT /admin/account-settings/default-password} : Quản trị đặt (hoặc bỏ, khi {@code null}) mật khẩu mặc định. */
    @PutMapping("/default-password")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public ResponseEntity<View> updateDefaultPassword(@RequestBody DefaultPasswordRequest request) {
        return noStore(defaultPasswordService.update(request.defaultPassword()));
    }

    private static ResponseEntity<View> noStore(View view) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(view);
    }
}
