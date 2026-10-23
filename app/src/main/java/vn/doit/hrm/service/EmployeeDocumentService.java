package vn.doit.hrm.service;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmployeeDocument;
import vn.doit.hrm.repository.EmployeeDocumentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.dto.EmployeeDocumentDTO;
import vn.doit.hrm.service.mapper.EmployeeDocumentMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.EmployeeDocument}.
 */
@Service
@Transactional
public class EmployeeDocumentService {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeDocumentService.class);

    private final EmployeeDocumentRepository employeeDocumentRepository;

    private final EmployeeDocumentMapper employeeDocumentMapper;

    private final EmployeeRepository employeeRepository;

    private final FileStorageService fileStorageService;

    private final Clock clock;

    private final ArchivedEmployeeGuard archivedGuard;

    public EmployeeDocumentService(
        EmployeeDocumentRepository employeeDocumentRepository,
        EmployeeDocumentMapper employeeDocumentMapper,
        EmployeeRepository employeeRepository,
        FileStorageService fileStorageService,
        Clock clock,
        ArchivedEmployeeGuard archivedGuard
    ) {
        this.archivedGuard = archivedGuard;
        this.employeeDocumentRepository = employeeDocumentRepository;
        this.employeeDocumentMapper = employeeDocumentMapper;
        this.employeeRepository = employeeRepository;
        this.fileStorageService = fileStorageService;
        this.clock = clock;
    }

    /**
     * Giấy nghỉ ốm (đánh dấu {@code sickCertificate} lúc tải lên, kể cả giấy cũ đã bị thay) chỉ người gửi, người duyệt của đơn, HR và
     * Quản trị được xem: ở các API hồ sơ scan (Kế toán, BGĐ cũng gọi được) chỉ HR / Quản trị thấy; người gửi và người duyệt tải qua
     * API đơn nghỉ.
     */
    private Predicate<EmployeeDocument> visibleToCurrentUser() {
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN)) {
            return d -> true;
        }
        return d -> !d.isSickCertificate();
    }

    /** Tệp đã lưu cùng thông tin để tải về. */
    public record StoredFile(String fileName, String contentType, long sizeBytes, Resource resource) {}

    private static final Map<String, String> TYPE_BY_EXTENSION = Map.of(
        "pdf",
        "application/pdf",
        "jpg",
        "image/jpeg",
        "jpeg",
        "image/jpeg",
        "png",
        "image/png"
    );

    /**
     * Tải lên hồ sơ scan cho nhân viên. Chỉ nhận PDF/JPEG/PNG; đuôi file, loại khai báo và byte đầu file phải khớp nhau.
     *
     * @return empty nếu không có nhân viên.
     * @throws EmployeeRuleException nếu loại file không hợp lệ.
     */
    public Optional<EmployeeDocumentDTO> upload(Long employeeId, String originalName, String declaredType, InputStream in) {
        return store(employeeId, originalName, declaredType, in, false);
    }

    /** Tải lên giấy chứng nhận nghỉ hưởng BHXH cho đơn nghỉ: cùng kiểm tra file, đánh dấu cố định là giấy nghỉ ốm. */
    public Optional<EmployeeDocumentDTO> uploadSickCertificate(Long employeeId, String originalName, String declaredType, InputStream in) {
        return store(employeeId, originalName, declaredType, in, true);
    }

    private Optional<EmployeeDocumentDTO> store(Long employeeId, String originalName, String declaredType, InputStream in, boolean sick) {
        Optional<Employee> employee = employeeRepository.findById(employeeId);
        if (employee.isEmpty()) {
            return Optional.empty();
        }
        archivedGuard.requireActive(employee.orElseThrow());
        String displayName = UploadNames.displayName(originalName);
        String ext = displayName.contains(".") ? displayName.substring(displayName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String typeByExt = TYPE_BY_EXTENSION.get(ext);
        if (typeByExt == null) {
            throw new EmployeeRuleException("Chỉ nhận file PDF, JPEG hoặc PNG", "filetypenotallowed");
        }
        // Một số trình duyệt/máy quét gửi "application/octet-stream" hoặc bỏ trống loại file: khi đó chỉ dựa vào đuôi file
        // và byte đầu file (kiểm tra ngay bên dưới). Loại khai báo cụ thể khác thì phải khớp đuôi file.
        String declared = declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT).trim();
        boolean genericType = declared.isEmpty() || "application/octet-stream".equals(declared);
        if (!genericType && !typeByExt.equals(declared)) {
            throw new EmployeeRuleException("Loại file không khớp với đuôi file", "filetypemismatch");
        }
        try {
            BufferedInputStream buffered = new BufferedInputStream(in);
            buffered.mark(16);
            byte[] head = buffered.readNBytes(8);
            buffered.reset();
            if (!typeByExt.equals(detect(head))) {
                throw new EmployeeRuleException(
                    "Nội dung file không đúng định dạng " + ext.toUpperCase(Locale.ROOT),
                    "filecontentmismatch"
                );
            }
            CountingInputStream counting = new CountingInputStream(buffered);
            String path = fileStorageService.store("employees/" + employeeId, displayName, counting);
            try {
                EmployeeDocument doc = new EmployeeDocument()
                    .fileName(displayName)
                    .contentType(typeByExt)
                    .sizeBytes(counting.count)
                    .storagePath(path)
                    .uploadedBy(SecurityUtils.getCurrentUserLogin().orElse(null))
                    .uploadedAt(Instant.now(clock))
                    .employee(employee.orElseThrow());
                doc.setSickCertificate(sick);
                return Optional.of(employeeDocumentMapper.toDto(employeeDocumentRepository.saveAndFlush(doc)));
            } catch (RuntimeException e) {
                fileStorageService.delete(path);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String detect(byte[] h) {
        if (h.length >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-') {
            return "application/pdf";
        }
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        if (h.length >= 8 && Arrays.equals(Arrays.copyOf(h, 8), png)) {
            return "image/png";
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDocumentDTO> findByEmployee(Long employeeId) {
        return employeeDocumentRepository
            .findByEmployeeIdOrderByUploadedAtDescIdDesc(employeeId)
            .stream()
            .filter(visibleToCurrentUser())
            .map(employeeDocumentMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<StoredFile> download(Long docId) {
        return employeeDocumentRepository
            .findById(docId)
            .filter(visibleToCurrentUser())
            .filter(d -> d.getStoragePath() != null)
            .map(d -> new StoredFile(d.getFileName(), d.getContentType(), d.getSizeBytes(), fileStorageService.load(d.getStoragePath())));
    }

    /** Tải giấy nghỉ ốm cho API đơn nghỉ (người gọi đã kiểm tra quyền theo đơn). */
    @Transactional(readOnly = true)
    public Optional<StoredFile> downloadSickDocument(Long docId) {
        return employeeDocumentRepository
            .findById(docId)
            .filter(d -> d.getStoragePath() != null)
            .map(d -> new StoredFile(d.getFileName(), d.getContentType(), d.getSizeBytes(), fileStorageService.load(d.getStoragePath())));
    }

    private static final class CountingInputStream extends FilterInputStream {

        long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }
    }

    /**
     * Save a employeeDocument.
     *
     * @param employeeDocumentDTO the entity to save.
     * @return the persisted entity.
     */
    public EmployeeDocumentDTO save(EmployeeDocumentDTO employeeDocumentDTO) {
        LOG.debug("Request to save EmployeeDocument : {}", employeeDocumentDTO);
        archivedGuard.requireActive(employeeDocumentDTO.getEmployee() == null ? null : employeeDocumentDTO.getEmployee().getId());
        EmployeeDocument employeeDocument = employeeDocumentMapper.toEntity(employeeDocumentDTO);
        employeeDocument = employeeDocumentRepository.save(employeeDocument);
        return employeeDocumentMapper.toDto(employeeDocument);
    }

    /**
     * Update a employeeDocument.
     *
     * @param employeeDocumentDTO the entity to save.
     * @return the persisted entity.
     */
    public EmployeeDocumentDTO update(EmployeeDocumentDTO employeeDocumentDTO) {
        LOG.debug("Request to update EmployeeDocument : {}", employeeDocumentDTO);
        archivedGuard.requireActive(employeeDocumentDTO.getEmployee() == null ? null : employeeDocumentDTO.getEmployee().getId());
        employeeDocumentRepository.findById(employeeDocumentDTO.getId()).ifPresent(d -> archivedGuard.requireActive(d.getEmployee()));
        EmployeeDocument employeeDocument = employeeDocumentMapper.toEntity(employeeDocumentDTO);
        employeeDocument = employeeDocumentRepository.save(employeeDocument);
        return employeeDocumentMapper.toDto(employeeDocument);
    }

    /**
     * Partially update a employeeDocument.
     *
     * @param employeeDocumentDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<EmployeeDocumentDTO> partialUpdate(EmployeeDocumentDTO employeeDocumentDTO) {
        LOG.debug("Request to partially update EmployeeDocument : {}", employeeDocumentDTO);

        return employeeDocumentRepository
            .findById(employeeDocumentDTO.getId())
            .map(existingEmployeeDocument -> {
                archivedGuard.requireActive(existingEmployeeDocument.getEmployee());
                employeeDocumentMapper.partialUpdate(existingEmployeeDocument, employeeDocumentDTO);

                return existingEmployeeDocument;
            })
            .map(employeeDocumentRepository::save)
            .map(employeeDocumentMapper::toDto);
    }

    /**
     * Get all the employeeDocuments.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<EmployeeDocumentDTO> findAll() {
        LOG.debug("Request to get all EmployeeDocuments");
        return employeeDocumentRepository
            .findAll()
            .stream()
            .filter(visibleToCurrentUser())
            .map(employeeDocumentMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the employeeDocuments with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public List<EmployeeDocumentDTO> findAllWithEagerRelationships() {
        return employeeDocumentRepository
            .findAllWithEagerRelationships()
            .stream()
            .filter(visibleToCurrentUser())
            .map(employeeDocumentMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one employeeDocument by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<EmployeeDocumentDTO> findOne(Long id) {
        LOG.debug("Request to get EmployeeDocument : {}", id);
        return employeeDocumentRepository.findOneWithEagerRelationships(id).filter(visibleToCurrentUser()).map(employeeDocumentMapper::toDto);
    }

    /**
     * Delete the employeeDocument by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete EmployeeDocument : {}", id);
        Optional<EmployeeDocument> doc = employeeDocumentRepository.findById(id);
        doc.ifPresent(d -> archivedGuard.requireActive(d.getEmployee()));
        if (doc.filter(EmployeeDocument::isSickCertificate).isPresent()) {
            throw new EmployeeRuleException("Hồ sơ này là giấy nghỉ ốm của một đơn nghỉ, không xóa được", "documentinuse");
        }
        employeeDocumentRepository.deleteById(id);
        // Chỉ xóa file sau khi giao dịch DB đã commit, để lỗi DB không để lại bản ghi trỏ vào file đã mất.
        String path = doc.map(EmployeeDocument::getStoragePath).orElse(null);
        if (path != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            fileStorageService.delete(path);
                        }
                    }
                );
            } else {
                fileStorageService.delete(path);
            }
        }
    }
}
