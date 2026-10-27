package vn.doit.hrm.web.rest;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.dto.CalcSettingDTO;
import vn.doit.hrm.service.payroll.calc.CalcSettingsService;

/** Thông số tính lương theo tháng hiệu lực: Quản trị, Kế toán, HR xem; chỉ Quản trị thêm, sửa, xóa bộ chưa dùng cho kỳ đã duyệt. */
@RestController
@RequestMapping("/api/payroll/calc-settings")
public class PayrollCalcSettingResource {

    private static final String ADMIN = "hasAuthority('" + AuthoritiesConstants.ADMIN + "')";

    private final CalcSettingsService service;

    public PayrollCalcSettingResource(CalcSettingsService service) {
        this.service = service;
    }

    @GetMapping("")
    @PreAuthorize(
        "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.HR + "')"
    )
    public List<CalcSettingDTO> list() {
        return service.list();
    }

    @PostMapping("")
    @PreAuthorize(ADMIN)
    public ResponseEntity<CalcSettingDTO> add(@RequestBody JsonNode body) {
        return ResponseEntity.created(URI.create("/api/payroll/calc-settings")).body(service.add(service.parse(body)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    public CalcSettingDTO update(@PathVariable Long id, @RequestBody JsonNode body) {
        return service.update(id, service.parse(body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
