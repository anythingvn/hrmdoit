package vn.doit.hrm.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hr.contract.ContractData;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.GeneratedDocument;
import vn.doit.hrm.domain.SensitiveAccessLog;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.DocumentOwnerType;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.domain.enumeration.GeneratedFileKind;
import vn.doit.hrm.domain.enumeration.TemplateKind;
import vn.doit.hrm.repository.AllowanceRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.GeneratedDocumentRepository;
import vn.doit.hrm.repository.SensitiveAccessLogRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.dto.EmploymentContractDTO;
import vn.doit.hrm.service.dto.GeneratedDocumentDTO;
import vn.doit.hrm.service.dto.OwnContractDTO;
import vn.doit.hrm.service.mapper.EmploymentContractMapper;

/**
 * Sinh văn bản hợp đồng từ mẫu: cấp số (nếu HĐ chưa có), điền mẫu, lưu .docx, rồi chuyển PDF sau khi lưu xong (dịch vụ PDF lỗi
 * thì vẫn giữ .docx). Bản đã ký tải lên lưu kèm hợp đồng. Dữ liệu nhạy cảm (CCCD, tài khoản) đọc thẳng từ thực thể, không qua DTO.
 */
@Service
public class ContractDocumentService {

    private final EmploymentContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final AllowanceRepository allowanceRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final SensitiveAccessLogRepository accessLogRepository;
    private final DocumentNumberService numberService;
    private final DocumentTemplateService templateService;
    private final ContractDataFactory dataFactory;
    private final DocumentFiles files;
    private final PendingTerminationGuard pendingTerminationGuard;
    private final EmploymentContractMapper contractMapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ContractDocumentService(
        EmploymentContractRepository contractRepository,
        EmployeeRepository employeeRepository,
        AllowanceRepository allowanceRepository,
        GeneratedDocumentRepository documentRepository,
        SensitiveAccessLogRepository accessLogRepository,
        DocumentNumberService numberService,
        DocumentTemplateService templateService,
        ContractDataFactory dataFactory,
        DocumentFiles files,
        PendingTerminationGuard pendingTerminationGuard,
        EmploymentContractMapper contractMapper,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.contractRepository = contractRepository;
        this.employeeRepository = employeeRepository;
        this.allowanceRepository = allowanceRepository;
        this.documentRepository = documentRepository;
        this.accessLogRepository = accessLogRepository;
        this.numberService = numberService;
        this.templateService = templateService;
        this.dataFactory = dataFactory;
        this.files = files;
        this.pendingTerminationGuard = pendingTerminationGuard;
        this.contractMapper = contractMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Kết quả sinh văn bản; {@code pdfError} có giá trị khi chưa tạo được PDF. */
    public record Result(String contractNo, GeneratedDocumentDTO docx, GeneratedDocumentDTO pdf, String pdfError) {}

    private record Generated(String contractNo, GeneratedDocument docx, byte[] content) {}

    public Result generate(Long contractId, boolean force) {
        Generated g = tx.execute(s -> {
            EmploymentContract c = contractRepository.findForUpdate(contractId).orElseThrow(ContractDocumentService::notFound);
            Employee e = c.getEmployee();
            TemplateKind kind = TemplateKind.forContract(c.getType());
            DocumentTemplateService.ActiveTemplate template = templateService
                .active(kind)
                .orElseThrow(() ->
                    new EmployeeRuleException("Chưa có mẫu cho loại hợp đồng này — tải mẫu ở Hợp đồng & mẫu", "notemplate")
                );
            var allowances = allowanceRepository.findByEmployeeId(e.getId());
            List<String> missing = dataFactory.missingFields(dataFactory.build(e, c, allowances));
            if (!missing.isEmpty() && !force) {
                throw new MissingFieldsException(missing);
            }
            if (c.getContractNo() == null || c.getContractNo().isBlank()) {
                c.setContractNo(numberService.next(DocumentType.forContract(c.getType()), LocalDate.now(clock).getYear()));
            }
            ContractData data = dataFactory.build(e, c, allowances);
            byte[] docx = files.render(template.content(), dataFactory.fields(data, c));
            GeneratedDocument saved = files.save(
                DocumentOwnerType.CONTRACT,
                c.getId(),
                GeneratedFileKind.DOCX,
                DocumentFiles.baseName(c.getContractNo(), e.getFullName()) + ".docx",
                docx,
                kind,
                template.version(),
                null
            );
            logAccess(e.getId());
            return new Generated(c.getContractNo(), saved, docx);
        });
        DocumentFiles.PdfOutcome pdf = files.toPdf(g.docx(), g.content());
        return new Result(g.contractNo(), DocumentFiles.toDto(g.docx()), pdf.pdf(), pdf.error());
    }

    /** Tạo lại PDF từ .docx mới nhất của hợp đồng. */
    public Result regeneratePdf(Long contractId) {
        EmploymentContract c = contractRepository.findById(contractId).orElseThrow(ContractDocumentService::notFound);
        GeneratedDocument docx = documentRepository
            .findFirstByOwnerTypeAndOwnerIdAndKindOrderByIdDesc(DocumentOwnerType.CONTRACT, contractId, GeneratedFileKind.DOCX)
            .orElseThrow(() -> new EmployeeRuleException("Hợp đồng chưa có văn bản .docx — bấm Tạo văn bản trước", "nodocx"));
        DocumentFiles.PdfOutcome pdf = files.toPdf(docx, files.read(docx));
        return new Result(c.getContractNo(), DocumentFiles.toDto(docx), pdf.pdf(), pdf.error());
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentDTO> list(Long contractId) {
        if (!contractRepository.existsById(contractId)) {
            throw notFound();
        }
        return documentRepository
            .findByOwnerTypeAndOwnerIdOrderByIdDesc(DocumentOwnerType.CONTRACT, contractId)
            .stream()
            .map(DocumentFiles::toDto)
            .toList();
    }

    /** Quản trị/HR tải một file văn bản; ghi nhật ký xem dữ liệu nhạy cảm của nhân viên. */
    @Transactional
    public Optional<DocumentFiles.StoredFile> download(Long docId) {
        Optional<DocumentFiles.StoredFile> f = files.load(docId);
        f.ifPresent(file -> logAccess(employeeOf(file)));
        return f;
    }

    @Transactional
    public GeneratedDocumentDTO uploadSigned(Long contractId, MultipartFile file) {
        EmploymentContract c = contractRepository.findForUpdate(contractId).orElseThrow(ContractDocumentService::notFound);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!DocumentFiles.isPdf(content)) {
            throw new EmployeeRuleException("Bản đã ký phải là file PDF", "signednotpdf");
        }
        String base = DocumentFiles.baseName(c.getContractNo(), c.getEmployee().getFullName());
        GeneratedDocument saved = files.save(
            DocumentOwnerType.CONTRACT,
            contractId,
            GeneratedFileKind.SIGNED,
            base + " - ban da ky.pdf",
            content,
            null,
            null,
            null
        );
        c.setSignedFileId(saved.getId());
        return DocumentFiles.toDto(saved);
    }

    /** Nháp → Đã ký. */
    @Transactional
    public EmploymentContractDTO sign(Long contractId) {
        EmploymentContract c = contractRepository.findForUpdate(contractId).orElseThrow(ContractDocumentService::notFound);
        if (c.getStatus() != ContractStatus.NHAP) {
            throw new EmployeeRuleException("Chỉ hợp đồng nháp mới đánh dấu đã ký được", "notdraft");
        }
        pendingTerminationGuard.checkStart(c.getEmployee().getId(), c.getStartDate());
        c.setStatus(ContractStatus.DA_KY);
        return contractMapper.toDto(c);
    }

    /** Hợp đồng của nhân viên gắn với tài khoản đang đăng nhập (rỗng nếu chưa gắn hồ sơ). */
    @Transactional(readOnly = true)
    public List<OwnContractDTO> ownContracts() {
        return currentEmployee()
            .map(e ->
                contractRepository
                    .findByEmployeeId(e.getId())
                    .stream()
                    .sorted(java.util.Comparator.comparing(EmploymentContract::getStartDate).reversed())
                    .map(c ->
                        new OwnContractDTO(
                            c.getId(),
                            c.getType(),
                            c.getContractNo(),
                            c.getStartDate(),
                            c.getEndDate(),
                            c.getStatus(),
                            c.getSignedFileId() != null
                        )
                    )
                    .toList()
            )
            .orElse(List.of());
    }

    /** Bản đã ký của chính mình; hợp đồng của người khác → 403. */
    @Transactional(readOnly = true)
    public DocumentFiles.StoredFile ownSigned(Long contractId) {
        EmploymentContract c = contractRepository.findById(contractId).orElseThrow(ContractDocumentService::notFound);
        Long own = currentEmployee().map(Employee::getId).orElse(null);
        if (own == null || c.getEmployee() == null || !own.equals(c.getEmployee().getId())) {
            throw new AccessDeniedException("Không phải hợp đồng của bạn");
        }
        if (c.getSignedFileId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hợp đồng chưa có bản đã ký");
        }
        return files.load(c.getSignedFileId()).orElseThrow(ContractDocumentService::notFound);
    }

    private Optional<Employee> currentEmployee() {
        return SecurityUtils.getCurrentUserLogin().flatMap(employeeRepository::findOneByUserLogin);
    }

    private Long employeeOf(DocumentFiles.StoredFile file) {
        if (file.ownerType() == DocumentOwnerType.CONTRACT) {
            return contractRepository.findById(file.ownerId()).map(c -> c.getEmployee().getId()).orElse(null);
        }
        return null;
    }

    private void logAccess(Long employeeId) {
        if (employeeId != null) {
            accessLogRepository.save(
                new SensitiveAccessLog(employeeId, SecurityUtils.getCurrentUserLogin().orElse(null), Instant.now(clock))
            );
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng");
    }
}
