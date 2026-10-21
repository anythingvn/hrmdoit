package vn.doit.hrm.web.rest;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hrm.domain.enumeration.TemplateKind;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.DocumentTemplateService;
import vn.doit.hrm.service.dto.DocumentTemplateDTO;
import vn.doit.hrm.service.dto.TemplateKindDTO;

/** Mẫu văn bản (Quản trị, Nhân sự): xem, tải về, tải lên phiên bản mới, quay lại phiên bản cũ hoặc mẫu gốc. */
@RestController
@RequestMapping("/api/document-templates")
@PreAuthorize(
    "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "')"
)
public class DocumentTemplateResource {

    static final MediaType DOCX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final DocumentTemplateService service;

    public DocumentTemplateResource(DocumentTemplateService service) {
        this.service = service;
    }

    @GetMapping("")
    public List<TemplateKindDTO> list() {
        return service.list().stream().filter(k -> canManage(k.kind())).toList();
    }

    /** Mẫu phiếu lương: Quản trị, Kế toán; các mẫu khác: Quản trị, HR. */
    private static boolean canManage(TemplateKind kind) {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(kind.managers().toArray(String[]::new));
    }

    private static void requireManage(TemplateKind kind) {
        if (!canManage(kind)) {
            throw new AccessDeniedException("Không có quyền với mẫu " + kind.label());
        }
    }

    /** {@code GET /document-templates/{kind}/download?version=} : 0 = mẫu gốc. */
    @GetMapping("/{kind}/download")
    public ResponseEntity<byte[]> download(@PathVariable TemplateKind kind, @RequestParam int version) {
        requireManage(kind);
        return service
            .download(kind, version)
            .map(f -> ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment(f.fileName())).contentType(DOCX).body(f.content()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có phiên bản mẫu này"));
    }

    @PostMapping(value = "/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentTemplateDTO> upload(@PathVariable TemplateKind kind, @RequestParam("file") MultipartFile file) {
        requireManage(kind);
        DocumentTemplateDTO created = service.upload(kind, file);
        return ResponseEntity.created(URI.create("/api/document-templates/" + kind + "/download?version=" + created.version())).body(created);
    }

    @PostMapping("/{kind}/versions/{version}/activate")
    public ResponseEntity<Void> activate(@PathVariable TemplateKind kind, @PathVariable int version) {
        requireManage(kind);
        if (!service.activate(kind, version)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có phiên bản mẫu này");
        }
        return ResponseEntity.ok().build();
    }
}
