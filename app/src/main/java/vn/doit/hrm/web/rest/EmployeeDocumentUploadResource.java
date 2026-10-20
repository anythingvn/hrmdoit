package vn.doit.hrm.web.rest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tech.jhipster.web.util.HeaderUtil;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.EmployeeDocumentService;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.dto.EmployeeDocumentDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/** Tải lên / liệt kê hồ sơ scan của một nhân viên. */
@RestController
@RequestMapping("/api/employees/{employeeId}/documents")
public class EmployeeDocumentUploadResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeDocumentUploadResource.class);

    private static final String ENTITY_NAME = "employeeDocument";

    private static final String WRITE = "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')";
    private static final String READ =
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    @Value("${spring.servlet.multipart.max-file-size:20MB}")
    private DataSize maxFileSize;

    private final EmployeeDocumentService employeeDocumentService;

    public EmployeeDocumentUploadResource(EmployeeDocumentService employeeDocumentService) {
        this.employeeDocumentService = employeeDocumentService;
    }

    /** {@code POST /employees/:employeeId/documents} : multipart {@code file}; chỉ PDF/JPEG/PNG. */
    @PreAuthorize(WRITE)
    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeeDocumentDTO> upload(@PathVariable("employeeId") Long employeeId, @RequestParam("file") MultipartFile file)
        throws URISyntaxException, IOException {
        LOG.debug("REST request to upload document for Employee {} : {}", employeeId, file.getOriginalFilename());
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new MaxUploadSizeExceededException(maxFileSize.toBytes());
        }
        EmployeeDocumentDTO created;
        try {
            created = employeeDocumentService
                .upload(employeeId, file.getOriginalFilename(), file.getContentType(), file.getInputStream())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
        return ResponseEntity.created(new URI("/api/employee-documents/" + created.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, created.getId().toString()))
            .body(created);
    }

    /** {@code GET /employees/:employeeId/documents} : danh sách hồ sơ, mới nhất trước. */
    @PreAuthorize(READ)
    @GetMapping("")
    public List<EmployeeDocumentDTO> list(@PathVariable("employeeId") Long employeeId) {
        return employeeDocumentService.findByEmployee(employeeId);
    }
}
