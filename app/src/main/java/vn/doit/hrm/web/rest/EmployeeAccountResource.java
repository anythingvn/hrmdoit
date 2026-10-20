package vn.doit.hrm.web.rest;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.EmployeeAccountService;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.dto.AccountCandidateDTO;
import vn.doit.hrm.service.dto.EmployeeAccountCreateDTO;
import vn.doit.hrm.service.dto.EmployeeAccountDTO;
import vn.doit.hrm.service.dto.EmployeeAccountLinkDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * Tài khoản đăng nhập gắn với hồ sơ nhân viên: xem, tạo kèm mật khẩu ban đầu, gắn tài khoản có sẵn, bỏ gắn.
 * Chỉ ADMIN/HR; HR không được cấp hay gắn vai trò Quản trị (403).
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeAccountResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeAccountResource.class);

    private static final String ENTITY_NAME = "employee";

    private static final String MANAGE = "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')";

    private final EmployeeAccountService employeeAccountService;

    public EmployeeAccountResource(EmployeeAccountService employeeAccountService) {
        this.employeeAccountService = employeeAccountService;
    }

    /** {@code GET /employees/:id/account} : tài khoản đang gắn (các trường null nếu chưa gắn). */
    @PreAuthorize(MANAGE)
    @GetMapping("/{id}/account")
    public ResponseEntity<EmployeeAccountDTO> getAccount(@PathVariable("id") Long id) {
        return ResponseEntity.of(employeeAccountService.getAccount(id));
    }

    /** {@code POST /employees/:id/account} : tạo tài khoản kèm mật khẩu ban đầu và gắn vào hồ sơ. */
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/account")
    public ResponseEntity<EmployeeAccountDTO> createAccount(
        @PathVariable("id") Long id,
        @Valid @RequestBody EmployeeAccountCreateDTO request
    ) {
        LOG.debug("REST request to create account for Employee : {}, {}", id, request);
        Optional<EmployeeAccountDTO> created = rules(() -> employeeAccountService.createAndLink(id, request));
        return created.map(dto -> ResponseEntity.status(HttpStatus.CREATED).body(dto)).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** {@code PUT /employees/:id/account} : gắn tài khoản có sẵn. */
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/account")
    public ResponseEntity<EmployeeAccountDTO> linkAccount(@PathVariable("id") Long id, @Valid @RequestBody EmployeeAccountLinkDTO request) {
        LOG.debug("REST request to link account {} to Employee : {}", request.userId(), id);
        return ResponseEntity.of(rules(() -> employeeAccountService.link(id, request.userId())));
    }

    /** {@code DELETE /employees/:id/account} : bỏ gắn; tài khoản vẫn còn. */
    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/account")
    public ResponseEntity<Void> unlinkAccount(@PathVariable("id") Long id) {
        LOG.debug("REST request to unlink account of Employee : {}", id);
        return employeeAccountService.unlink(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** {@code GET /employees/account-candidates} : tài khoản chưa gắn hồ sơ nào. */
    @PreAuthorize(MANAGE)
    @GetMapping("/account-candidates")
    public List<AccountCandidateDTO> getCandidates() {
        return employeeAccountService.candidates();
    }

    private static <T> T rules(Supplier<T> action) {
        try {
            return action.get();
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
    }
}
