package vn.doit.hrm.web.rest;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.AssignmentDecisionService;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.dto.AssignmentDecisionRequestDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/** Quyết định cử nhân sự (Quản trị, HR): tạo kèm giai đoạn vị trí, danh sách, tạo lại PDF. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
public class AssignmentDecisionResource {

    private static final String ENTITY_NAME = "assignmentDecision";

    private final AssignmentDecisionService service;

    public AssignmentDecisionResource(AssignmentDecisionService service) {
        this.service = service;
    }

    @PostMapping("/employees/{employeeId}/assignment-decisions")
    public ResponseEntity<AssignmentDecisionService.Result> create(
        @PathVariable Long employeeId,
        @RequestBody AssignmentDecisionRequestDTO request
    ) {
        try {
            AssignmentDecisionService.Result result = service.create(employeeId, request);
            return ResponseEntity.created(URI.create("/api/generated-documents/" + result.docx().id() + "/download")).body(result);
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
    }

    @GetMapping("/employees/{employeeId}/assignment-decisions")
    public List<AssignmentDecisionService.DecisionWithDocuments> list(@PathVariable Long employeeId) {
        return service.list(employeeId);
    }

    @PostMapping("/assignment-decisions/{id}/documents/pdf")
    public AssignmentDecisionService.Result regeneratePdf(@PathVariable Long id) {
        try {
            return service.regeneratePdf(id);
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
    }

    /** Người ký mặc định để giao diện điền sẵn. */
    @GetMapping("/assignment-decisions/defaults")
    public AssignmentDecisionService.Defaults defaults() {
        return service.defaults();
    }
}
