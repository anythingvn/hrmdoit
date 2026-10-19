package vn.doit.hrm.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.service.WorkAssignmentService;
import vn.doit.hrm.service.dto.WorkAssignmentDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing {@link vn.doit.hrm.domain.WorkAssignment}.
 */
@RestController
@RequestMapping("/api/work-assignments")
public class WorkAssignmentResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkAssignmentResource.class);

    private static final String ENTITY_NAME = "workAssignment";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final WorkAssignmentService workAssignmentService;

    private final WorkAssignmentRepository workAssignmentRepository;

    public WorkAssignmentResource(WorkAssignmentService workAssignmentService, WorkAssignmentRepository workAssignmentRepository) {
        this.workAssignmentService = workAssignmentService;
        this.workAssignmentRepository = workAssignmentRepository;
    }

    /**
     * {@code POST  /work-assignments} : Create a new workAssignment.
     *
     * @param workAssignmentDTO the workAssignmentDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new workAssignmentDTO, or with status {@code 400 (Bad Request)} if the workAssignment has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("")
    public ResponseEntity<WorkAssignmentDTO> createWorkAssignment(@Valid @RequestBody WorkAssignmentDTO workAssignmentDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save WorkAssignment : {}", workAssignmentDTO);
        if (workAssignmentDTO.getId() != null) {
            throw new BadRequestAlertException("A new workAssignment cannot already have an ID", ENTITY_NAME, "idexists");
        }
        workAssignmentDTO = workAssignmentService.save(workAssignmentDTO);
        return ResponseEntity.created(new URI("/api/work-assignments/" + workAssignmentDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, workAssignmentDTO.getId().toString()))
            .body(workAssignmentDTO);
    }

    /**
     * {@code PUT  /work-assignments/:id} : Updates an existing workAssignment.
     *
     * @param id the id of the workAssignmentDTO to save.
     * @param workAssignmentDTO the workAssignmentDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated workAssignmentDTO,
     * or with status {@code 400 (Bad Request)} if the workAssignmentDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the workAssignmentDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PutMapping("/{id}")
    public ResponseEntity<WorkAssignmentDTO> updateWorkAssignment(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody WorkAssignmentDTO workAssignmentDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update WorkAssignment : {}, {}", id, workAssignmentDTO);
        if (workAssignmentDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, workAssignmentDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!workAssignmentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        workAssignmentDTO = workAssignmentService.update(workAssignmentDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, workAssignmentDTO.getId().toString()))
            .body(workAssignmentDTO);
    }

    /**
     * {@code PATCH  /work-assignments/:id} : Partial updates given fields of an existing workAssignment, field will ignore if it is null
     *
     * @param id the id of the workAssignmentDTO to save.
     * @param workAssignmentDTO the workAssignmentDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated workAssignmentDTO,
     * or with status {@code 400 (Bad Request)} if the workAssignmentDTO is not valid,
     * or with status {@code 404 (Not Found)} if the workAssignmentDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the workAssignmentDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<WorkAssignmentDTO> partialUpdateWorkAssignment(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody WorkAssignmentDTO workAssignmentDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update WorkAssignment partially : {}, {}", id, workAssignmentDTO);
        if (workAssignmentDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, workAssignmentDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!workAssignmentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<WorkAssignmentDTO> result = workAssignmentService.partialUpdate(workAssignmentDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, workAssignmentDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /work-assignments} : get all the workAssignments.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of workAssignments in body.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("")
    public List<WorkAssignmentDTO> getAllWorkAssignments(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all WorkAssignments");
        return workAssignmentService.findAll();
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

    /**
     * {@code DELETE  /work-assignments/:id} : delete the "id" workAssignment.
     *
     * @param id the id of the workAssignmentDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkAssignment(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete WorkAssignment : {}", id);
        workAssignmentService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
