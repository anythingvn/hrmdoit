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
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.EmploymentContractService;
import vn.doit.hrm.service.dto.EmploymentContractDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing {@link vn.doit.hrm.domain.EmploymentContract}.
 */
@RestController
@RequestMapping("/api/employment-contracts")
public class EmploymentContractResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmploymentContractResource.class);

    private static final String ENTITY_NAME = "employmentContract";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmploymentContractService employmentContractService;

    private final EmploymentContractRepository employmentContractRepository;

    public EmploymentContractResource(
        EmploymentContractService employmentContractService,
        EmploymentContractRepository employmentContractRepository
    ) {
        this.employmentContractService = employmentContractService;
        this.employmentContractRepository = employmentContractRepository;
    }

    /**
     * {@code POST  /employment-contracts} : Create a new employmentContract.
     *
     * @param employmentContractDTO the employmentContractDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new employmentContractDTO, or with status {@code 400 (Bad Request)} if the employmentContract has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("")
    public ResponseEntity<EmploymentContractDTO> createEmploymentContract(@Valid @RequestBody EmploymentContractDTO employmentContractDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save EmploymentContract : {}", employmentContractDTO);
        if (employmentContractDTO.getId() != null) {
            throw new BadRequestAlertException("A new employmentContract cannot already have an ID", ENTITY_NAME, "idexists");
        }
        employmentContractDTO = employmentContractService.save(employmentContractDTO);
        return ResponseEntity.created(new URI("/api/employment-contracts/" + employmentContractDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, employmentContractDTO.getId().toString()))
            .body(employmentContractDTO);
    }

    /**
     * {@code PUT  /employment-contracts/:id} : Updates an existing employmentContract.
     *
     * @param id the id of the employmentContractDTO to save.
     * @param employmentContractDTO the employmentContractDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated employmentContractDTO,
     * or with status {@code 400 (Bad Request)} if the employmentContractDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the employmentContractDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PutMapping("/{id}")
    public ResponseEntity<EmploymentContractDTO> updateEmploymentContract(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody EmploymentContractDTO employmentContractDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update EmploymentContract : {}, {}", id, employmentContractDTO);
        if (employmentContractDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, employmentContractDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!employmentContractRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        employmentContractDTO = employmentContractService.update(employmentContractDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, employmentContractDTO.getId().toString()))
            .body(employmentContractDTO);
    }

    /**
     * {@code PATCH  /employment-contracts/:id} : Partial updates given fields of an existing employmentContract, field will ignore if it is null
     *
     * @param id the id of the employmentContractDTO to save.
     * @param employmentContractDTO the employmentContractDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated employmentContractDTO,
     * or with status {@code 400 (Bad Request)} if the employmentContractDTO is not valid,
     * or with status {@code 404 (Not Found)} if the employmentContractDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the employmentContractDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<EmploymentContractDTO> partialUpdateEmploymentContract(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody EmploymentContractDTO employmentContractDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update EmploymentContract partially : {}, {}", id, employmentContractDTO);
        if (employmentContractDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, employmentContractDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!employmentContractRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<EmploymentContractDTO> result = employmentContractService.partialUpdate(employmentContractDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, employmentContractDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /employment-contracts} : get all the employmentContracts.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of employmentContracts in body.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("")
    public List<EmploymentContractDTO> getAllEmploymentContracts(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all EmploymentContracts");
        return employmentContractService.findAll();
    }

    /**
     * {@code GET  /employment-contracts/:id} : get the "id" employmentContract.
     *
     * @param id the id of the employmentContractDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the employmentContractDTO, or with status {@code 404 (Not Found)}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("/{id}")
    public ResponseEntity<EmploymentContractDTO> getEmploymentContract(@PathVariable("id") Long id) {
        LOG.debug("REST request to get EmploymentContract : {}", id);
        Optional<EmploymentContractDTO> employmentContractDTO = employmentContractService.findOne(id);
        return ResponseUtil.wrapOrNotFound(employmentContractDTO);
    }

    /**
     * {@code DELETE  /employment-contracts/:id} : delete the "id" employmentContract.
     *
     * @param id the id of the employmentContractDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmploymentContract(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete EmploymentContract : {}", id);
        employmentContractService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
