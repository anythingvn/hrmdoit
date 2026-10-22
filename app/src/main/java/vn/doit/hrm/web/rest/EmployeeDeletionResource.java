package vn.doit.hrm.web.rest;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;
import vn.doit.hrm.service.EmployeeDeletionService;
import vn.doit.hrm.service.dto.EmployeeDeleteRequestDTO;

/**
 * Xóa hẳn nhân viên đã lưu trữ và nhật ký xóa. Chỉ login {@code admin}: kiểm tra ở {@link EmployeeDeletionService} (403
 * {@code deleterequiressystemadmin} cho mọi người khác, kể cả tài khoản Quản trị khác).
 */
@RestController
@RequestMapping("/api")
public class EmployeeDeletionResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeDeletionResource.class);

    /** Một dòng nhật ký xóa: người xóa, thời điểm, lý do, mã, họ tên. */
    public record DeletionLogDTO(Long id, Instant deletedAt, String deletedBy, String reason, String employeeCode, String employeeName) {}

    private final EmployeeDeletionService deletionService;

    public EmployeeDeletionResource(EmployeeDeletionService deletionService) {
        this.deletionService = deletionService;
    }

    /**
     * {@code DELETE /api/employees/:id} với {@code {confirmCode, reason}} : xóa hẳn hồ sơ đang ở vùng lưu trữ. Không {@code @Valid}: kiểm
     * tra quyền đi trước mọi kiểm tra dữ liệu (người không phải admin luôn nhận 403); độ dài do dịch vụ kiểm tra.
     */
    @DeleteMapping("/employees/{id}")
    public ResponseEntity<Void> deleteEmployee(
        @PathVariable("id") Long id,
        @RequestBody(required = false) EmployeeDeleteRequestDTO request
    ) {
        LOG.debug("REST request to permanently delete Employee : {}", id);
        EmployeeDeletionService.requireSystemAdmin();
        deletionService.delete(id, request == null ? null : request.confirmCode(), request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }

    /** {@code GET /api/admin/employee-deletions} : nhật ký xóa hẳn, mới nhất trước. */
    @GetMapping("/admin/employee-deletions")
    public ResponseEntity<List<DeletionLogDTO>> deletions(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        Page<DeletionLogDTO> page = deletionService
            .log(pageable)
            .map(l ->
                new DeletionLogDTO(l.getId(), l.getDeletedAt(), l.getDeletedBy(), l.getReason(), l.getEmployeeCode(), l.getEmployeeName())
            );
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }
}
