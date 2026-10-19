package vn.doit.hrm.web.rest;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import tech.jhipster.web.util.ResponseUtil;
import vn.doit.hrm.service.WorkAssignmentService;
import vn.doit.hrm.service.dto.WorkAssignmentDTO;

/**
 * REST controller for managing {@link vn.doit.hrm.domain.WorkAssignment}.
 */
@RestController
@RequestMapping("/api/work-assignments")
// Chỉ đọc: ghi lịch sử vị trí làm việc qua EmployeeAssignmentResource (/api/employees/{id}/assignments) để đi qua quy tắc lõi.
public class WorkAssignmentResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkAssignmentResource.class);

    private final WorkAssignmentService workAssignmentService;

    public WorkAssignmentResource(WorkAssignmentService workAssignmentService) {
        this.workAssignmentService = workAssignmentService;
    }

    /**
     * {@code GET  /work-assignments} : get all the Work Assignments.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Work Assignments in body.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("")
    public List<WorkAssignmentDTO> getAllWorkAssignments(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all WorkAssignments");
        if (eagerload) {
            return workAssignmentService.findAllWithEagerRelationships();
        } else {
            return workAssignmentService.findAll();
        }
    }

    /**
     * {@code GET  /work-assignments/:id} : get the "id" workAssignment.
     *
     * @param id the id of the workAssignmentDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the workAssignmentDTO, or with status {@code 404 (Not Found)}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("/{id}")
    public ResponseEntity<WorkAssignmentDTO> getWorkAssignment(@PathVariable("id") Long id) {
        LOG.debug("REST request to get WorkAssignment : {}", id);
        Optional<WorkAssignmentDTO> workAssignmentDTO = workAssignmentService.findOne(id);
        return ResponseUtil.wrapOrNotFound(workAssignmentDTO);
    }
}
