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
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.service.DepartmentService;
import vn.doit.hrm.service.dto.DepartmentDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing {@link vn.doit.hrm.domain.Department}.
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentResource {

    private static final Logger LOG = LoggerFactory.getLogger(DepartmentResource.class);

    private static final String ENTITY_NAME = "department";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DepartmentService departmentService;

    private final DepartmentRepository departmentRepository;

    public DepartmentResource(DepartmentService departmentService, DepartmentRepository departmentRepository) {
        this.departmentService = departmentService;
        this.departmentRepository = departmentRepository;
    }

    /**
     * {@code POST  /departments} : Create a new department.
     *
     * @param departmentDTO the departmentDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new departmentDTO, or with status {@code 400 (Bad Request)} if the department has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("")
    public ResponseEntity<DepartmentDTO> createDepartment(@Valid @RequestBody DepartmentDTO departmentDTO) throws URISyntaxException {
        LOG.debug("REST request to save Department : {}", departmentDTO);
        if (departmentDTO.getId() != null) {
            throw new BadRequestAlertException("A new department cannot already have an ID", ENTITY_NAME, "idexists");
        }
        departmentDTO = departmentService.save(departmentDTO);
        return ResponseEntity.created(new URI("/api/departments/" + departmentDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, departmentDTO.getId().toString()))
            .body(departmentDTO);
    }

    /**
     * {@code PUT  /departments/:id} : Updates an existing department.
     *
     * @param id the id of the departmentDTO to save.
     * @param departmentDTO the departmentDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated departmentDTO,
     * or with status {@code 400 (Bad Request)} if the departmentDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the departmentDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDTO> updateDepartment(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody DepartmentDTO departmentDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update Department : {}, {}", id, departmentDTO);
        if (departmentDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, departmentDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!departmentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        departmentDTO = departmentService.update(departmentDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, departmentDTO.getId().toString()))
            .body(departmentDTO);
    }

    /**
     * {@code PATCH  /departments/:id} : Partial updates given fields of an existing department, field will ignore if it is null
     *
     * @param id the id of the departmentDTO to save.
     * @param departmentDTO the departmentDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated departmentDTO,
     * or with status {@code 400 (Bad Request)} if the departmentDTO is not valid,
     * or with status {@code 404 (Not Found)} if the departmentDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the departmentDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DepartmentDTO> partialUpdateDepartment(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody DepartmentDTO departmentDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Department partially : {}, {}", id, departmentDTO);
        if (departmentDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, departmentDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!departmentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<DepartmentDTO> result = departmentService.partialUpdate(departmentDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, departmentDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /departments} : get all the departments.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of departments in body.
     */
    @GetMapping("")
    public List<DepartmentDTO> getAllDepartments() {
        LOG.debug("REST request to get all Departments");
        return departmentService.findAll();
    }

    /**
     * {@code GET  /departments/:id} : get the "id" department.
     *
     * @param id the id of the departmentDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the departmentDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DepartmentDTO> getDepartment(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Department : {}", id);
        Optional<DepartmentDTO> departmentDTO = departmentService.findOne(id);
        return ResponseUtil.wrapOrNotFound(departmentDTO);
    }
}
