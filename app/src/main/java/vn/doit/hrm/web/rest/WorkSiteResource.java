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
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.WorkSiteService;
import vn.doit.hrm.service.dto.WorkSiteDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing {@link vn.doit.hrm.domain.WorkSite}.
 */
@RestController
@RequestMapping("/api/work-sites")
public class WorkSiteResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkSiteResource.class);

    private static final String ENTITY_NAME = "workSite";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final WorkSiteService workSiteService;

    private final WorkSiteRepository workSiteRepository;

    public WorkSiteResource(WorkSiteService workSiteService, WorkSiteRepository workSiteRepository) {
        this.workSiteService = workSiteService;
        this.workSiteRepository = workSiteRepository;
    }

    /**
     * {@code POST  /work-sites} : Create a new workSite.
     *
     * @param workSiteDTO the workSiteDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new workSiteDTO, or with status {@code 400 (Bad Request)} if the workSite has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("")
    public ResponseEntity<WorkSiteDTO> createWorkSite(@Valid @RequestBody WorkSiteDTO workSiteDTO) throws URISyntaxException {
        LOG.debug("REST request to save WorkSite : {}", workSiteDTO);
        if (workSiteDTO.getId() != null) {
            throw new BadRequestAlertException("A new workSite cannot already have an ID", ENTITY_NAME, "idexists");
        }
        workSiteDTO = workSiteService.save(workSiteDTO);
        return ResponseEntity.created(new URI("/api/work-sites/" + workSiteDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, workSiteDTO.getId().toString()))
            .body(workSiteDTO);
    }

    /**
     * {@code PUT  /work-sites/:id} : Updates an existing workSite.
     *
     * @param id the id of the workSiteDTO to save.
     * @param workSiteDTO the workSiteDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated workSiteDTO,
     * or with status {@code 400 (Bad Request)} if the workSiteDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the workSiteDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PutMapping("/{id}")
    public ResponseEntity<WorkSiteDTO> updateWorkSite(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody WorkSiteDTO workSiteDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update WorkSite : {}, {}", id, workSiteDTO);
        if (workSiteDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, workSiteDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!workSiteRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        workSiteDTO = workSiteService.update(workSiteDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, workSiteDTO.getId().toString()))
            .body(workSiteDTO);
    }

    /**
     * {@code PATCH  /work-sites/:id} : Partial updates given fields of an existing workSite, field will ignore if it is null
     *
     * @param id the id of the workSiteDTO to save.
     * @param workSiteDTO the workSiteDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated workSiteDTO,
     * or with status {@code 400 (Bad Request)} if the workSiteDTO is not valid,
     * or with status {@code 404 (Not Found)} if the workSiteDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the workSiteDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<WorkSiteDTO> partialUpdateWorkSite(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody WorkSiteDTO workSiteDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update WorkSite partially : {}, {}", id, workSiteDTO);
        if (workSiteDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, workSiteDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!workSiteRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<WorkSiteDTO> result = workSiteService.partialUpdate(workSiteDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, workSiteDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /work-sites} : get all the workSites.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of workSites in body.
     */
    @GetMapping("")
    public List<WorkSiteDTO> getAllWorkSites() {
        LOG.debug("REST request to get all WorkSites");
        return workSiteService.findAll();
    }

    /**
     * {@code GET  /work-sites/:id} : get the "id" workSite.
     *
     * @param id the id of the workSiteDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the workSiteDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkSiteDTO> getWorkSite(@PathVariable("id") Long id) {
        LOG.debug("REST request to get WorkSite : {}", id);
        Optional<WorkSiteDTO> workSiteDTO = workSiteService.findOne(id);
        return ResponseUtil.wrapOrNotFound(workSiteDTO);
    }
}
