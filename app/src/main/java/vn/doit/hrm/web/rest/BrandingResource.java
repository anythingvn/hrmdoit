package vn.doit.hrm.web.rest;

import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.service.CompanySettingService;
import vn.doit.hrm.service.dto.BrandingDTO;

/**
 * Thông tin công ty công khai cho màn đăng nhập, menu, favicon (không cần đăng nhập, không có người đại diện).
 * Logo SVG trả kèm {@code Content-Security-Policy: sandbox}: mở thẳng file cũng không chạy được script trong ảnh.
 */
@RestController
@RequestMapping("/api/public/branding")
public class BrandingResource {

    private static final CacheControl CACHE = CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic();

    private final CompanySettingService service;

    public BrandingResource(CompanySettingService service) {
        this.service = service;
    }

    @GetMapping("")
    public BrandingDTO branding() {
        return service.branding();
    }

    /** Ảnh logo; ETag là phiên bản logo, trùng {@code If-None-Match} thì trả 304 mà không đọc ảnh. */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String current = etag(service.logoVersion());
        if (matches(ifNoneMatch, current)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(current).cacheControl(CACHE).build();
        }
        CompanySettingService.Logo logo = service.logo();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(logo.contentType()))
            .eTag(etag(logo.version()))
            .cacheControl(CACHE);
        if (CompanySettingService.SVG.equals(logo.contentType())) {
            response.header("Content-Security-Policy", "sandbox");
        }
        return response.body(logo.bytes());
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String c = candidate.trim();
            if (c.startsWith("W/")) {
                c = c.substring(2);
            }
            if (c.equals(etag) || c.equals("*")) {
                return true;
            }
        }
        return false;
    }
}
