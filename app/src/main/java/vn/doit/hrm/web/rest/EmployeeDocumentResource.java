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
import vn.doit.hrm.repository.EmployeeDocumentRepository;
import vn.doit.hrm.service.EmployeeDocumentService;
import vn.doit.hrm.service.dto.EmployeeDocumentDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing {@link vn.doit.hrm.domain.EmployeeDocument}.
 */
@RestController
@RequestMapping("/api/employee-documents")
public class EmployeeDocumentResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeDocumentResource.class);

    private static final String ENTITY_NAME = "employeeDocument";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeDocumentService employeeDocumentService;

    private final EmployeeDocumentRepository employeeDocumentRepository;

    public EmployeeDocumentResource(
        EmployeeDocumentService employeeDocumentService,
        EmployeeDocumentRepository employeeDocumentRepository
    ) {
        this.employeeDocumentService = employeeDocumentService;
        this.employeeDocumentRepository = employeeDocumentRepository;
    }

    /**
     * {@code POST  /employee-documents} : Create a new employeeDocument.
     *
     * @param employeeDocumentDTO the employeeDocumentDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new employeeDocumentDTO, or with status {@code 400 (Bad Request)} if the employeeDocument has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("")
    public ResponseEntity<EmployeeDocumentDTO> createEmployeeDocument(@Valid @RequestBody EmployeeDocumentDTO employeeDocumentDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save EmployeeDocument : {}", employeeDocumentDTO);
        if (employeeDocumentDTO.getId() != null) {
            throw new BadRequestAlertException("A new employeeDocument cannot already have an ID", ENTITY_NAME, "idexists");
        }
        employeeDocumentDTO = employeeDocumentService.save(employeeDocumentDTO);
        return ResponseEntity.created(new URI("/api/employee-documents/" + employeeDocumentDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, employeeDocumentDTO.getId().toString()))
            .body(employeeDocumentDTO);
    }

    /**
     * {@code PUT  /employee-documents/:id} : Updates an existing employeeDocument.
     *
     * @param id the id of the employeeDocumentDTO to save.
     * @param employeeDocumentDTO the employeeDocumentDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated employeeDocumentDTO,
     * or with status {@code 400 (Bad Request)} if the employeeDocumentDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the employeeDocumentDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeDocumentDTO> updateEmployeeDocument(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody EmployeeDocumentDTO employeeDocumentDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update EmployeeDocument : {}, {}", id, employeeDocumentDTO);
        if (employeeDocumentDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, employeeDocumentDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!employeeDocumentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        employeeDocumentDTO = employeeDocumentService.update(employeeDocumentDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, employeeDocumentDTO.getId().toString()))
            .body(employeeDocumentDTO);
    }

    /**
     * {@code PATCH  /employee-documents/:id} : Partial updates given fields of an existing employeeDocument, field will ignore if it is null
     *
     * @param id the id of the employeeDocumentDTO to save.
     * @param employeeDocumentDTO the employeeDocumentDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated employeeDocumentDTO,
     * or with status {@code 400 (Bad Request)} if the employeeDocumentDTO is not valid,
     * or with status {@code 404 (Not Found)} if the employeeDocumentDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the employeeDocumentDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<EmployeeDocumentDTO> partialUpdateEmployeeDocument(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody EmployeeDocumentDTO employeeDocumentDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update EmployeeDocument partially : {}, {}", id, employeeDocumentDTO);
        if (employeeDocumentDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, employeeDocumentDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!employeeDocumentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<EmployeeDocumentDTO> result = employeeDocumentService.partialUpdate(employeeDocumentDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, employeeDocumentDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /employee-documents} : get all the employeeDocuments.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of employeeDocuments in body.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("")
    public List<EmployeeDocumentDTO> getAllEmployeeDocuments(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all EmployeeDocuments");
        return employeeDocumentService.findAll();
    }

    /**
     * {@code GET  /employee-documents/:id} : get the "id" employeeDocument.
     *
     * @param id the id of the employeeDocumentDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the employeeDocumentDTO, or with status {@code 404 (Not Found)}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeDocumentDTO> getEmployeeDocument(@PathVariable("id") Long id) {
        LOG.debug("REST request to get EmployeeDocument : {}", id);
        Optional<EmployeeDocumentDTO> employeeDocumentDTO = employeeDocumentService.findOne(id);
        return ResponseUtil.wrapOrNotFound(employeeDocumentDTO);
    }

    /**
     * {@code DELETE  /employee-documents/:id} : delete the "id" employeeDocument.
     *
     * @param id the id of the employeeDocumentDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployeeDocument(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete EmployeeDocument : {}", id);
        employeeDocumentService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
