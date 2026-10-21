package vn.doit.hrm.web.rest;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.ContractDocumentService;
import vn.doit.hrm.service.DocumentFiles;
import vn.doit.hrm.service.dto.EmploymentContractDTO;
import vn.doit.hrm.service.dto.GeneratedDocumentDTO;
import vn.doit.hrm.service.dto.OwnContractDTO;

/**
 * Văn bản hợp đồng sinh từ mẫu và bản đã ký. Chứa CCCD, tài khoản ngân hàng: chỉ Quản trị, HR.
 * Nhân viên chỉ tải được bản đã ký hợp đồng của chính mình.
 */
@RestController
@RequestMapping("/api")
public class ContractDocumentResource {

    private static final String WRITE = "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')";

    private final ContractDocumentService service;

    public ContractDocumentResource(ContractDocumentService service) {
        this.service = service;
    }

    /** {@code POST /employment-contracts/{id}/documents?force=} : sinh .docx (+ PDF nếu dịch vụ PDF chạy). */
    @PreAuthorize(WRITE)
    @PostMapping("/employment-contracts/{id}/documents")
    public ResponseEntity<ContractDocumentService.Result> generate(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean force) {
        ContractDocumentService.Result result = service.generate(id, force);
        return ResponseEntity.created(URI.create("/api/generated-documents/" + result.docx().id() + "/download")).body(result);
    }

    @PreAuthorize(WRITE)
    @PostMapping("/employment-contracts/{id}/documents/pdf")
    public ContractDocumentService.Result regeneratePdf(@PathVariable Long id) {
        return service.regeneratePdf(id);
    }

    @PreAuthorize(WRITE)
    @GetMapping("/employment-contracts/{id}/documents")
    public List<GeneratedDocumentDTO> list(@PathVariable Long id) {
        return service.list(id);
    }

    @PreAuthorize(WRITE)
    @PostMapping(value = "/employment-contracts/{id}/signed", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeneratedDocumentDTO> uploadSigned(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        GeneratedDocumentDTO saved = service.uploadSigned(id, file);
        return ResponseEntity.created(URI.create("/api/generated-documents/" + saved.id() + "/download")).body(saved);
    }

    @PreAuthorize(WRITE)
    @PostMapping("/employment-contracts/{id}/sign")
    public EmploymentContractDTO sign(@PathVariable Long id) {
        return service.sign(id);
    }

    @PreAuthorize(WRITE)
    @GetMapping("/generated-documents/{docId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long docId) {
        return service
            .download(docId)
            .map(ContractDocumentResource::file)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy văn bản"));
    }

    /** {@code GET /account/employee/contracts} : hợp đồng của chính mình (không có lương). */
    @GetMapping("/account/employee/contracts")
    public List<OwnContractDTO> ownContracts() {
        return service.ownContracts();
    }

    @GetMapping("/account/employee/contracts/{id}/signed")
    public ResponseEntity<byte[]> ownSigned(@PathVariable Long id) {
        return file(service.ownSigned(id));
    }

    static ResponseEntity<byte[]> file(DocumentFiles.StoredFile f) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment(f.fileName()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.parseMediaType(f.contentType()))
            .body(f.content());
    }
}
