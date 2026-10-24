package vn.doit.hrm.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hr.contract.TemplateTags;
import vn.doit.hrm.domain.DocumentTemplate;
import vn.doit.hrm.domain.DocumentTemplateCounter;
import vn.doit.hrm.domain.enumeration.TemplateKind;
import vn.doit.hrm.repository.DocumentTemplateCounterRepository;
import vn.doit.hrm.repository.DocumentTemplateRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.dto.DocumentTemplateDTO;
import vn.doit.hrm.service.dto.TemplateKindDTO;

/**
 * Kho mẫu văn bản có phiên bản. Mẫu tải lên được kiểm tra trước khi nhận: đúng .docx, không có thẻ bị cắt, không có thẻ lạ.
 * Phiên bản 0 là mẫu gốc đóng gói sẵn (nếu loại đó có).
 */
@Service
@Transactional
public class DocumentTemplateService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentTemplateService.class);

    static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Số mẫu đang lưu tối đa cho mỗi loại (không tính mẫu gốc). */
    public static final int MAX_TEMPLATES_PER_KIND = 5;

    private final DocumentTemplateRepository repository;
    private final DocumentTemplateCounterRepository counters;
    private final FileStorageService storage;
    private final Clock clock;

    public DocumentTemplateService(
        DocumentTemplateRepository repository,
        DocumentTemplateCounterRepository counters,
        FileStorageService storage,
        Clock clock
    ) {
        this.repository = repository;
        this.counters = counters;
        this.storage = storage;
        this.clock = clock;
    }

    /** Nội dung mẫu đang dùng kèm số phiên bản (0 = mẫu gốc). */
    public record ActiveTemplate(byte[] content, int version) {}

    @Transactional(readOnly = true)
    public Optional<ActiveTemplate> active(TemplateKind kind) {
        Optional<DocumentTemplate> uploaded = repository.findFirstByKindAndActiveTrue(kind);
        if (uploaded.isPresent()) {
            return Optional.of(new ActiveTemplate(read(uploaded.orElseThrow()), uploaded.orElseThrow().getVersion()));
        }
        return defaultTemplate(kind).map(bytes -> new ActiveTemplate(bytes, 0));
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> activeTemplate(TemplateKind kind) {
        return active(kind).map(ActiveTemplate::content);
    }

    public DocumentTemplateDTO upload(TemplateKind kind, MultipartFile file) {
        if (repository.countByKind(kind) >= MAX_TEMPLATES_PER_KIND) {
            throw new EmployeeRuleException("Đã có " + MAX_TEMPLATES_PER_KIND + " mẫu, hãy xoá bớt mẫu không cần", "templatelimit");
        }
        String name = UploadNames.displayName(file.getOriginalFilename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new EmployeeRuleException("Chỉ nhận file Word .docx", "templatenotdocx");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new EmployeeRuleException("Mẫu tối đa 5 MB", "templatetoolarge");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!isDocx(content)) {
            throw new EmployeeRuleException("File không phải văn bản Word .docx", "templatenotdocx");
        }
        TemplateTags.TemplateScan scan;
        try {
            scan = TemplateTags.scan(new ByteArrayInputStream(content));
        } catch (IOException | RuntimeException e) {
            throw new EmployeeRuleException("Không đọc được file Word .docx", "templatenotdocx");
        }
        if (!scan.brokenTags().isEmpty()) {
            throw new EmployeeRuleException(
                "Thẻ bị cắt: " +
                tagList(scan.brokenTags()) +
                ". Trong Word, xóa thẻ rồi gõ lại cả thẻ một lần, không định dạng riêng một phần của thẻ.",
                "templatebrokentags"
            );
        }
        TreeSet<String> unknown = new TreeSet<>(scan.tags());
        unknown.removeAll(kind.allowedTags());
        if (!unknown.isEmpty()) {
            throw new EmployeeRuleException("Thẻ không dùng được cho " + kind.label() + ": " + tagList(unknown), "templateunknowntags");
        }
        String path = storage.store("templates/" + kind.name(), name, new ByteArrayInputStream(content));
        try {
            repository.findByKindOrderByVersionDesc(kind).forEach(t -> t.setActive(false));
            DocumentTemplate t = new DocumentTemplate()
                .kind(kind)
                .version(nextVersion(kind))
                .fileName(name)
                .storagePath(path)
                .uploadedBy(SecurityUtils.getCurrentUserLogin().orElse(null))
                .uploadedAt(Instant.now(clock));
            t.setActive(true);
            return toDto(repository.saveAndFlush(t));
        } catch (RuntimeException e) {
            storage.delete(path);
            throw e;
        }
    }

    /** Cấp số phiên bản tiếp theo; số đã cấp không bao giờ dùng lại, kể cả khi mẫu đã bị xóa. */
    private int nextVersion(TemplateKind kind) {
        DocumentTemplateCounter counter = counters
            .lockByKind(kind)
            .orElseGet(() -> counters.saveAndFlush(new DocumentTemplateCounter(kind, repository.maxVersion(kind))));
        return counter.next();
    }

    /**
     * Xóa một phiên bản mẫu đã tải lên (không xóa được mẫu gốc hay mẫu đang dùng). Văn bản đã sinh giữ file riêng và chỉ ghi lại
     * số phiên bản dạng số, không khóa ngoại, nên không bị ảnh hưởng. File mẫu chỉ xóa sau khi giao dịch commit; xóa file lỗi chỉ ghi log.
     * Trả false nếu không có phiên bản đó.
     */
    public boolean delete(TemplateKind kind, int version) {
        Optional<DocumentTemplate> target = version == 0 ? Optional.empty() : repository.findByKindAndVersion(kind, version);
        if (target.isEmpty()) {
            return false;
        }
        DocumentTemplate t = target.orElseThrow();
        if (t.isActive()) {
            throw new EmployeeRuleException("Không xóa được mẫu đang dùng; hãy chọn dùng mẫu khác trước", "templateactive");
        }
        String path = t.getStoragePath();
        repository.delete(t);
        repository.flush();
        deleteFileAfterCommit(path);
        return true;
    }

    private void deleteFileAfterCommit(String path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(path);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(path);
                }
            }
        );
    }

    private void deleteQuietly(String path) {
        try {
            storage.delete(path);
        } catch (RuntimeException e) {
            LOG.warn("Could not delete file {} of a deleted document template: {}", path, e.getMessage());
        }
    }

    /** Dùng lại một phiên bản cũ; 0 = mẫu gốc. Trả false nếu không có phiên bản đó. */
    public boolean activate(TemplateKind kind, int version) {
        if (version == 0 && kind.defaultResource() == null) {
            return false;
        }
        Optional<DocumentTemplate> target = version == 0 ? Optional.empty() : repository.findByKindAndVersion(kind, version);
        if (version != 0 && target.isEmpty()) {
            return false;
        }
        repository.findByKindOrderByVersionDesc(kind).forEach(t -> t.setActive(t.getVersion() == version));
        return true;
    }

    @Transactional(readOnly = true)
    public List<TemplateKindDTO> list() {
        return Arrays.stream(TemplateKind.values())
            .map(kind -> {
                List<DocumentTemplate> versions = repository.findByKindOrderByVersionDesc(kind);
                boolean hasDefault = kind.defaultResource() != null;
                Integer activeVersion = versions
                    .stream()
                    .filter(DocumentTemplate::isActive)
                    .map(DocumentTemplate::getVersion)
                    .findFirst()
                    .orElse(hasDefault ? 0 : null);
                return new TemplateKindDTO(
                    kind,
                    kind.label(),
                    activeVersion,
                    hasDefault,
                    new TreeSet<>(kind.allowedTags()),
                    versions.stream().map(DocumentTemplateService::toDto).toList()
                );
            })
            .toList();
    }

    /** Tên file và nội dung của một phiên bản (0 = mẫu gốc). */
    public record TemplateFile(String fileName, byte[] content) {}

    @Transactional(readOnly = true)
    public Optional<TemplateFile> download(TemplateKind kind, int version) {
        if (version == 0) {
            return defaultTemplate(kind).map(b -> new TemplateFile(kind.defaultResource().substring("templates/".length()), b));
        }
        return repository.findByKindAndVersion(kind, version).map(t -> new TemplateFile(t.getFileName(), read(t)));
    }

    private byte[] read(DocumentTemplate t) {
        try (InputStream in = storage.load(t.getStoragePath()).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<byte[]> defaultTemplate(TemplateKind kind) {
        if (kind.defaultResource() == null) {
            return Optional.empty();
        }
        try (InputStream in = DocumentTemplateService.class.getClassLoader().getResourceAsStream(kind.defaultResource())) {
            if (in == null) {
                throw new IllegalStateException("Thiếu mẫu gốc " + kind.defaultResource());
            }
            return Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** ZIP (PK\3\4) có word/document.xml. */
    static boolean isDocx(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K' || content[2] != 3 || content[3] != 4) {
            return false;
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String tagList(java.util.Collection<String> tags) {
        return tags.stream().map(t -> "{{" + t + "}}").collect(Collectors.joining(", "));
    }

    private static DocumentTemplateDTO toDto(DocumentTemplate t) {
        return new DocumentTemplateDTO(t.getId(), t.getKind(), t.getVersion(), t.getFileName(), t.getUploadedBy(), t.getUploadedAt(), t.isActive());
    }
}
