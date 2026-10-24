package vn.doit.hrm.web.rest;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.dto.RuleMilestoneDTO;
import vn.doit.hrm.service.payroll.RuleBookService;

/** Mức luật định theo ngày hiệu lực: Quản trị, Kế toán, HR xem; chỉ Quản trị thêm, sửa, xóa mốc chưa dùng và đổi "đã biết luật đến". */
@RestController
@RequestMapping("/api/payroll/rules")
public class PayrollRuleResource {

    private static final String ADMIN = "hasAuthority('" + AuthoritiesConstants.ADMIN + "')";

    private final RuleBookService service;

    public PayrollRuleResource(RuleBookService service) {
        this.service = service;
    }

    public record RulesVM(String knownThrough, List<RuleMilestoneDTO> milestones) {}

    public record KnownThroughVM(String knownThrough) {}

    @GetMapping("")
    @PreAuthorize(
        "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.HR + "')"
    )
    public RulesVM list() {
        return new RulesVM(service.knownThrough().toString(), service.list());
    }

    @PostMapping("")
    @PreAuthorize(ADMIN)
    public ResponseEntity<RuleMilestoneDTO> add(@RequestBody RuleMilestoneDTO body) {
        return ResponseEntity.created(URI.create("/api/payroll/rules")).body(service.add(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    public RuleMilestoneDTO update(@PathVariable Long id, @RequestBody RuleMilestoneDTO body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/known-through")
    @PreAuthorize(ADMIN)
    public KnownThroughVM setKnownThrough(@RequestBody KnownThroughVM body) {
        service.setKnownThrough(RuleBookService.parseMonth(body.knownThrough()));
        return new KnownThroughVM(service.knownThrough().toString());
    }
}
