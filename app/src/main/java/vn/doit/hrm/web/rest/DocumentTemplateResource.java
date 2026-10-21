package vn.doit.hrm.web.rest;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hrm.domain.enumeration.TemplateKind;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.DocumentTemplateService;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.dto.DocumentTemplateDTO;
import vn.doit.hrm.service.dto.TemplateKindDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/** Mẫu văn bản (Quản trị, Nhân sự): xem, tải về, tải lên phiên bản mới, quay lại phiên bản cũ hoặc mẫu gốc. */
@RestController
@RequestMapping("/api/document-templates")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
public class DocumentTemplateResource {

    private static final String ENTITY_NAME = "documentTemplate";

    static final MediaType DOCX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final DocumentTemplateService service;

    public DocumentTemplateResource(DocumentTemplateService service) {
        this.service = service;
    }

    @GetMapping("")
    public List<TemplateKindDTO> list() {
        return service.list();
    }

    /** {@code GET /document-templates/{kind}/download?version=} : 0 = mẫu gốc. */
    @GetMapping("/{kind}/download")
    public ResponseEntity<byte[]> download(@PathVariable TemplateKind kind, @RequestParam int version) {
        return service
            .download(kind, version)
            .map(f -> ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, attachment(f.fileName())).contentType(DOCX).body(f.content()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có phiên bản mẫu này"));
    }

    @PostMapping(value = "/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentTemplateDTO> upload(@PathVariable TemplateKind kind, @RequestParam("file") MultipartFile file) {
        try {
            DocumentTemplateDTO created = service.upload(kind, file);
            return ResponseEntity.created(URI.create("/api/document-templates/" + kind + "/download?version=" + created.version())).body(created);
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
    }

    @PostMapping("/{kind}/versions/{version}/activate")
    public ResponseEntity<Void> activate(@PathVariable TemplateKind kind, @PathVariable int version) {
        if (!service.activate(kind, version)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có phiên bản mẫu này");
        }
        return ResponseEntity.ok().build();
    }

    /** Header tải file giữ nguyên tên tiếng Việt. */
    static String attachment(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
