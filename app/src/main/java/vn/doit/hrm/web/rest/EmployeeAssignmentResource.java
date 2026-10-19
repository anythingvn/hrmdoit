package vn.doit.hrm.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.WorkAssignmentService;
import vn.doit.hrm.service.dto.AssignmentSegmentDTO;
import vn.doit.hrm.service.dto.WorkAssignmentDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * Lịch sử vị trí làm việc của một nhân viên. Ghi chỉ qua đây (thêm giai đoạn, đổi ngày kết thúc, xóa giai đoạn nhập sai) để mọi thay đổi
 * đi qua quy tắc của lõi. Lỗi quy tắc lõi ({@code AssignmentConflictException}) trả 400 với {@code detail} là thông báo tiếng Việt.
 */
@RestController
@RequestMapping("/api/employees/{employeeId}/assignments")
public class EmployeeAssignmentResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeAssignmentResource.class);

    private static final String ENTITY_NAME = "workAssignment";

    private static final String READ =
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')";

    private static final String WRITE = "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')";

    /** Thân yêu cầu đổi ngày kết thúc; {@code toDate} null = mở lại (không dùng cho công tác). */
    public record EndRequest(LocalDate toDate) {}

    private final WorkAssignmentService workAssignmentService;

    public EmployeeAssignmentResource(WorkAssignmentService workAssignmentService) {
        this.workAssignmentService = workAssignmentService;
    }

    /** {@code POST /employees/:employeeId/assignments} : thêm giai đoạn; có thể tự chốt nơi cũ và tạo giai đoạn quay về. */
    @PreAuthorize(WRITE)
    @PostMapping("")
    public ResponseEntity<WorkAssignmentDTO> add(@PathVariable("employeeId") Long employeeId, @RequestBody WorkAssignmentDTO dto)
        throws URISyntaxException {
        LOG.debug("REST request to add WorkAssignment for Employee {} : {}", employeeId, dto);
        WorkAssignmentDTO created = rule(() -> workAssignmentService.add(employeeId, dto));
        return ResponseEntity.created(new URI("/api/work-assignments/" + created.getId())).body(created);
    }

    /** {@code PATCH /employees/:employeeId/assignments/:id/end} : đổi ngày kết thúc (về sớm, gia hạn, chốt). */
    @PreAuthorize(WRITE)
    @PatchMapping("/{assignmentId}/end")
    public WorkAssignmentDTO changeEnd(
        @PathVariable("employeeId") Long employeeId,
        @PathVariable("assignmentId") Long assignmentId,
        @RequestBody EndRequest body
    ) {
        LOG.debug("REST request to change end of WorkAssignment {} of Employee {} : {}", assignmentId, employeeId, body);
        return rule(() -> workAssignmentService.changeEnd(employeeId, assignmentId, body.toDate()));
    }

    /** {@code DELETE /employees/:employeeId/assignments/:id} : xóa giai đoạn nhập sai (sửa lịch sử, có ghi nhật ký). */
    @PreAuthorize(WRITE)
    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> delete(@PathVariable("employeeId") Long employeeId, @PathVariable("assignmentId") Long assignmentId) {
        LOG.debug("REST request to delete WorkAssignment {} of Employee {}", assignmentId, employeeId);
        rule(() -> {
            workAssignmentService.delete(employeeId, assignmentId);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    /** {@code GET /employees/:employeeId/assignments/timeline} : dòng thời gian thực tế. */
    @PreAuthorize(READ)
    @GetMapping("/timeline")
    public List<AssignmentSegmentDTO> timeline(
        @PathVariable("employeeId") Long employeeId,
        @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return rule(() -> workAssignmentService.timeline(employeeId, from, to));
    }

    /** {@code GET /employees/:employeeId/assignments/current} : nơi làm việc hôm nay, hoặc 204 nếu chưa có dữ liệu. */
    @PreAuthorize(READ)
    @GetMapping("/current")
    public ResponseEntity<WorkAssignmentDTO> current(@PathVariable("employeeId") Long employeeId) {
        return rule(() -> workAssignmentService.current(employeeId))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** {@code GET /employees/:employeeId/assignments/gaps?from=&to=} : các khoảng chưa có nơi làm việc chính. */
    @PreAuthorize(READ)
    @GetMapping("/gaps")
    public List<AssignmentSegmentDTO> gaps(
        @PathVariable("employeeId") Long employeeId,
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return rule(() -> workAssignmentService.gaps(employeeId, from, to));
    }

    private static <T> T rule(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
    }
}
