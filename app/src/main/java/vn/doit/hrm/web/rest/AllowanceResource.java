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
import vn.doit.hrm.repository.AllowanceRepository;
import vn.doit.hrm.service.AllowanceService;
import vn.doit.hrm.service.dto.AllowanceDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing {@link vn.doit.hrm.domain.Allowance}.
 */
@RestController
@RequestMapping("/api/allowances")
public class AllowanceResource {

    private static final Logger LOG = LoggerFactory.getLogger(AllowanceResource.class);

    private static final String ENTITY_NAME = "allowance";

    @Value("${jhipster.clientApp.name:hrm}")
    private String applicationName;

    private final AllowanceService allowanceService;

    private final AllowanceRepository allowanceRepository;

    public AllowanceResource(AllowanceService allowanceService, AllowanceRepository allowanceRepository) {
        this.allowanceService = allowanceService;
        this.allowanceRepository = allowanceRepository;
    }

    /**
     * {@code POST  /allowances} : Create a new allowance.
     *
     * @param allowanceDTO the allowanceDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new allowanceDTO, or with status {@code 400 (Bad Request)} if the allowance already has an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("")
    public ResponseEntity<AllowanceDTO> createAllowance(@Valid @RequestBody AllowanceDTO allowanceDTO) throws URISyntaxException {
        LOG.debug("REST request to save Allowance : {}", allowanceDTO);
        if (allowanceDTO.getId() != null) {
            throw new BadRequestAlertException("A new allowance cannot already have an ID", ENTITY_NAME, "idexists");
        }
        allowanceDTO = allowanceService.save(allowanceDTO);
        return ResponseEntity.created(new URI("/api/allowances/" + allowanceDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, allowanceDTO.getId().toString()))
            .body(allowanceDTO);
    }

    /**
     * {@code PUT  /allowances/:id} : Updates an existing allowance.
     *
     * @param id the id of the allowanceDTO to save.
     * @param allowanceDTO the allowanceDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated allowanceDTO,
     * or with status {@code 400 (Bad Request)} if the allowanceDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the allowanceDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PutMapping("/{id}")
    public ResponseEntity<AllowanceDTO> updateAllowance(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody AllowanceDTO allowanceDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update Allowance : {}, {}", id, allowanceDTO);
        if (allowanceDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, allowanceDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!allowanceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        allowanceDTO = allowanceService.update(allowanceDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, allowanceDTO.getId().toString()))
            .body(allowanceDTO);
    }

    /**
     * {@code PATCH  /allowances/:id} : Partial updates given fields of an existing allowance, field will ignore if it is null
     *
     * @param id the id of the allowanceDTO to save.
     * @param allowanceDTO the allowanceDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated allowanceDTO,
     * or with status {@code 400 (Bad Request)} if the allowanceDTO is not valid,
     * or with status {@code 404 (Not Found)} if the allowanceDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the allowanceDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<AllowanceDTO> partialUpdateAllowance(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody AllowanceDTO allowanceDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partially update Allowance : {}, {}", id, allowanceDTO);
        if (allowanceDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, allowanceDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!allowanceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<AllowanceDTO> result = allowanceService.partialUpdate(allowanceDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, allowanceDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /allowances} : get all the Allowances.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Allowances in body.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("")
    public List<AllowanceDTO> getAllAllowances(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all Allowances");
        if (eagerload) {
            return allowanceService.findAllWithEagerRelationships();
        } else {
            return allowanceService.findAll();
        }
    }

    /**
     * {@code GET  /allowances/:id} : get the "id" allowance.
     *
     * @param id the id of the allowanceDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the allowanceDTO, or with status {@code 404 (Not Found)}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("/{id}")
    public ResponseEntity<AllowanceDTO> getAllowance(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Allowance : {}", id);
        Optional<AllowanceDTO> allowanceDTO = allowanceService.findOne(id);
        return ResponseUtil.wrapOrNotFound(allowanceDTO);
    }

    /**
     * {@code DELETE  /allowances/:id} : delete the "id" allowance.
     *
     * @param id the id of the allowanceDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAllowance(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Allowance : {}", id);
        allowanceService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
