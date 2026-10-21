package vn.doit.hrm.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import vn.doit.hr.contract.DocxTemplateRenderer;
import vn.doit.hrm.domain.GeneratedDocument;
import vn.doit.hrm.domain.enumeration.DocumentOwnerType;
import vn.doit.hrm.domain.enumeration.GeneratedFileKind;
import vn.doit.hrm.domain.enumeration.TemplateKind;
import vn.doit.hrm.repository.GeneratedDocumentRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.dto.GeneratedDocumentDTO;
import vn.doit.hrm.service.pdf.PdfService;
import vn.doit.hrm.service.pdf.PdfUnavailableException;

/** Phần dùng chung khi sinh văn bản hợp đồng và quyết định: điền mẫu, lưu file, chuyển PDF, đọc lại file. */
@Component
public class DocumentFiles {

    public static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final GeneratedDocumentRepository repository;
    private final FileStorageService storage;
    private final PdfService pdfService;
    private final Clock clock;
    private final DocxTemplateRenderer renderer = new DocxTemplateRenderer();

    public DocumentFiles(GeneratedDocumentRepository repository, FileStorageService storage, PdfService pdfService, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.pdfService = pdfService;
        this.clock = clock;
    }

    /** Kết quả chuyển PDF: có bản PDF, hoặc câu báo lỗi (file .docx vẫn giữ). */
    public record PdfOutcome(GeneratedDocumentDTO pdf, String error) {}

    /** File tải về. */
    public record StoredFile(String fileName, String contentType, byte[] content, DocumentOwnerType ownerType, Long ownerId) {}

    public byte[] render(byte[] template, Map<String, ?> fields) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            renderer.render(new ByteArrayInputStream(template), fields, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Lưu file và ghi một dòng {@code generated_document}; lỗi khi ghi dòng thì xóa file vừa lưu. */
    public GeneratedDocument save(
        DocumentOwnerType ownerType,
        Long ownerId,
        GeneratedFileKind kind,
        String fileName,
        byte[] content,
        TemplateKind templateKind,
        Integer templateVersion,
        Long sourceDocumentId
    ) {
        String folder = (ownerType == DocumentOwnerType.CONTRACT ? "contracts/" : "decisions/") + ownerId;
        String path = storage.store(folder, fileName, new ByteArrayInputStream(content));
        try {
            return repository.saveAndFlush(
                new GeneratedDocument()
                    .ownerType(ownerType)
                    .ownerId(ownerId)
                    .kind(kind)
                    .fileName(fileName)
                    .storagePath(path)
                    .templateKind(templateKind)
                    .templateVersion(templateVersion)
                    .sourceDocumentId(sourceDocumentId)
                    .createdBy(SecurityUtils.getCurrentUserLogin().orElse(null))
                    .createdAt(Instant.now(clock))
            );
        } catch (RuntimeException e) {
            storage.delete(path);
            throw e;
        }
    }

    /** Chuyển .docx sang PDF và lưu; gọi ngoài transaction (dịch vụ PDF có thể chờ đến 60 giây). */
    public PdfOutcome toPdf(GeneratedDocument docx, byte[] content) {
        byte[] pdf;
        try {
            pdf = pdfService.toPdf(content, docx.getFileName());
        } catch (PdfUnavailableException e) {
            return new PdfOutcome(null, e.getMessage());
        }
        String name = docx.getFileName().replaceFirst("(?i)\\.docx$", "") + ".pdf";
        GeneratedDocument saved = save(
            docx.getOwnerType(),
            docx.getOwnerId(),
            GeneratedFileKind.PDF,
            name,
            pdf,
            docx.getTemplateKind(),
            docx.getTemplateVersion(),
            docx.getId()
        );
        return new PdfOutcome(toDto(saved), null);
    }

    public byte[] read(GeneratedDocument d) {
        try (InputStream in = storage.load(d.getStoragePath()).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<StoredFile> load(Long id) {
        return repository
            .findById(id)
            .map(d ->
                new StoredFile(
                    d.getFileName(),
                    d.getKind() == GeneratedFileKind.DOCX ? DOCX_TYPE : "application/pdf",
                    read(d),
                    d.getOwnerType(),
                    d.getOwnerId()
                )
            );
    }

    /** Tên file an toàn từ số văn bản và họ tên: "DOIT-2026-HĐLĐ-1 Nguyễn Văn A". */
    public static String baseName(String number, String fullName) {
        String n = number == null ? "" : number.replaceAll("[\\\\/:*?\"<>|]", "-");
        String name = fullName == null ? "" : fullName.replaceAll("[\\\\/:*?\"<>|]", " ").trim();
        String base = (n + " " + name).trim();
        return base.isEmpty() ? "van-ban" : base;
    }

    /** Bản PDF đã ký: kiểm tra byte đầu file. */
    public static boolean isPdf(byte[] head) {
        return head.length >= 5 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-';
    }

    public static GeneratedDocumentDTO toDto(GeneratedDocument d) {
        return new GeneratedDocumentDTO(
            d.getId(),
            d.getKind(),
            d.getFileName(),
            d.getTemplateKind(),
            d.getTemplateVersion(),
            d.getSourceDocumentId(),
            d.getCreatedBy(),
            d.getCreatedAt()
        );
    }
}
