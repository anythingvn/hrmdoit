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
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.SalaryChangeService;
import vn.doit.hrm.service.dto.SalaryChangeDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing {@link vn.doit.hrm.domain.SalaryChange}.
 */
@RestController
@RequestMapping("/api/salary-changes")
public class SalaryChangeResource {

    private static final Logger LOG = LoggerFactory.getLogger(SalaryChangeResource.class);

    private static final String ENTITY_NAME = "salaryChange";

    @Value("${jhipster.clientApp.name:hrm}")
    private String applicationName;

    private final SalaryChangeService salaryChangeService;

    private final SalaryChangeRepository salaryChangeRepository;

    public SalaryChangeResource(SalaryChangeService salaryChangeService, SalaryChangeRepository salaryChangeRepository) {
        this.salaryChangeService = salaryChangeService;
        this.salaryChangeRepository = salaryChangeRepository;
    }

    /**
     * {@code POST  /salary-changes} : Create a new salaryChange.
     *
     * @param salaryChangeDTO the salaryChangeDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new salaryChangeDTO, or with status {@code 400 (Bad Request)} if the salaryChange already has an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("")
    public ResponseEntity<SalaryChangeDTO> createSalaryChange(@Valid @RequestBody SalaryChangeDTO salaryChangeDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save SalaryChange : {}", salaryChangeDTO);
        if (salaryChangeDTO.getId() != null) {
            throw new BadRequestAlertException("A new salaryChange cannot already have an ID", ENTITY_NAME, "idexists");
        }
        salaryChangeDTO = salaryChangeService.save(salaryChangeDTO);
        return ResponseEntity.created(new URI("/api/salary-changes/" + salaryChangeDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, salaryChangeDTO.getId().toString()))
            .body(salaryChangeDTO);
    }

    /**
     * {@code PUT  /salary-changes/:id} : Updates an existing salaryChange.
     *
     * @param id the id of the salaryChangeDTO to save.
     * @param salaryChangeDTO the salaryChangeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated salaryChangeDTO,
     * or with status {@code 400 (Bad Request)} if the salaryChangeDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the salaryChangeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PutMapping("/{id}")
    public ResponseEntity<SalaryChangeDTO> updateSalaryChange(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody SalaryChangeDTO salaryChangeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update SalaryChange : {}, {}", id, salaryChangeDTO);
        if (salaryChangeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, salaryChangeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!salaryChangeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            salaryChangeDTO = salaryChangeService.update(salaryChangeDTO);
        } catch (vn.doit.hrm.service.EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, salaryChangeDTO.getId().toString()))
            .body(salaryChangeDTO);
    }

    /**
     * {@code PATCH  /salary-changes/:id} : Partial updates given fields of an existing salaryChange, field will ignore if it is null
     *
     * @param id the id of the salaryChangeDTO to save.
     * @param salaryChangeDTO the salaryChangeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated salaryChangeDTO,
     * or with status {@code 400 (Bad Request)} if the salaryChangeDTO is not valid,
     * or with status {@code 404 (Not Found)} if the salaryChangeDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the salaryChangeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<SalaryChangeDTO> partialUpdateSalaryChange(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody SalaryChangeDTO salaryChangeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partially update SalaryChange : {}, {}", id, salaryChangeDTO);
        if (salaryChangeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, salaryChangeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!salaryChangeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<SalaryChangeDTO> result;
        try {
            result = salaryChangeService.partialUpdate(salaryChangeDTO);
        } catch (vn.doit.hrm.service.EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, salaryChangeDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /salary-changes} : get all the Salary Changes.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Salary Changes in body.
     */
    @PreAuthorize(
        "hasAnyAuthority('" +
            AuthoritiesConstants.ADMIN +
            "', '" +
            AuthoritiesConstants.HR +
            "', '" +
            AuthoritiesConstants.KE_TOAN +
            "', '" +
            AuthoritiesConstants.BGD +
            "')"
    )
    @GetMapping("")
    public List<SalaryChangeDTO> getAllSalaryChanges(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all SalaryChanges");
        if (eagerload) {
            return salaryChangeService.findAllWithEagerRelationships();
        } else {
            return salaryChangeService.findAll();
        }
    }

    /**
     * {@code GET  /salary-changes/:id} : get the "id" salaryChange.
     *
     * @param id the id of the salaryChangeDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the salaryChangeDTO, or with status {@code 404 (Not Found)}.
     */
    @PreAuthorize(
        "hasAnyAuthority('" +
            AuthoritiesConstants.ADMIN +
            "', '" +
            AuthoritiesConstants.HR +
            "', '" +
            AuthoritiesConstants.KE_TOAN +
            "', '" +
            AuthoritiesConstants.BGD +
            "')"
    )
    @GetMapping("/{id}")
    public ResponseEntity<SalaryChangeDTO> getSalaryChange(@PathVariable("id") Long id) {
        LOG.debug("REST request to get SalaryChange : {}", id);
        Optional<SalaryChangeDTO> salaryChangeDTO = salaryChangeService.findOne(id);
        return ResponseUtil.wrapOrNotFound(salaryChangeDTO);
    }

    /**
     * {@code DELETE  /salary-changes/:id} : delete the "id" salaryChange.
     *
     * @param id the id of the salaryChangeDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSalaryChange(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete SalaryChange : {}", id);
        salaryChangeService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
