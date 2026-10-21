package vn.doit.hrm.web.rest;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @Value("${jhipster.clientApp.name:hrm}")
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
     * {@code GET  /employee-documents} : get all the employeeDocuments.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Employee Documents in body.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("")
    public List<EmployeeDocumentDTO> getAllEmployeeDocuments(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all EmployeeDocuments");
        if (eagerload) {
            return employeeDocumentService.findAllWithEagerRelationships();
        } else {
            return employeeDocumentService.findAll();
        }
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

    /** {@code GET /employee-documents/:id/download} : tải file gốc, giữ nguyên tên file. */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id) {
        LOG.debug("REST request to download EmployeeDocument : {}", id);
        return employeeDocumentService
            .download(id)
            .map(f -> {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment(f.fileName()))
                    .contentType(MediaType.parseMediaType(f.contentType()))
                    .contentLength(f.sizeBytes())
                    .body(f.resource());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
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
