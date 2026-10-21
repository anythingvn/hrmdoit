package vn.doit.hrm.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.AssignmentDecision;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.domain.AssignmentDecisionRecord;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.GeneratedDocument;
import vn.doit.hrm.domain.enumeration.DocumentOwnerType;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.domain.enumeration.GeneratedFileKind;
import vn.doit.hrm.domain.enumeration.TemplateKind;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AssignmentDecisionRecordRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.GeneratedDocumentRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.dto.AssignmentDecisionDTO;
import vn.doit.hrm.service.dto.AssignmentDecisionRequestDTO;
import vn.doit.hrm.service.dto.GeneratedDocumentDTO;
import vn.doit.hrm.service.dto.WorkAssignmentDTO;
import vn.doit.hrm.service.dto.WorkSiteDTO;

/**
 * Quyết định cử nhân sự. Trong MỘT transaction: cấp số QĐCNS, ghi giai đoạn vị trí (chồng lấn / lịch nghỉ việc → lỗi, không
 * lưu gì và dãy số không tăng), lưu quyết định, sinh .docx. PDF chuyển sau khi lưu xong (lỗi thì vẫn giữ .docx).
 */
@Service
public class AssignmentDecisionService {

    private final AssignmentDecisionRecordRepository decisionRepository;
    private final EmployeeRepository employeeRepository;
    private final EmploymentContractRepository contractRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final WorkAssignmentService workAssignmentService;
    private final DocumentNumberService numberService;
    private final DocumentTemplateService templateService;
    private final DocumentFiles files;
    private final ApplicationProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AssignmentDecisionService(
        AssignmentDecisionRecordRepository decisionRepository,
        EmployeeRepository employeeRepository,
        EmploymentContractRepository contractRepository,
        GeneratedDocumentRepository documentRepository,
        WorkAssignmentService workAssignmentService,
        DocumentNumberService numberService,
        DocumentTemplateService templateService,
        DocumentFiles files,
        ApplicationProperties properties,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.decisionRepository = decisionRepository;
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.workAssignmentService = workAssignmentService;
        this.numberService = numberService;
        this.templateService = templateService;
        this.files = files;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Kết quả tạo quyết định; {@code pdfError} có giá trị khi chưa tạo được PDF. */
    public record Result(AssignmentDecisionDTO decision, GeneratedDocumentDTO docx, GeneratedDocumentDTO pdf, String pdfError) {}

    /** Quyết định kèm các file văn bản (mới nhất trước). */
    public record DecisionWithDocuments(AssignmentDecisionDTO decision, List<GeneratedDocumentDTO> documents) {}

    /** Người ký mặc định (cấu hình công ty), để giao diện điền sẵn. */
    public record Defaults(String signerName, String signerTitle) {}

    private record Created(AssignmentDecisionRecord decision, GeneratedDocument docx, byte[] content) {}

    public Defaults defaults() {
        return new Defaults(properties.getCompany().getRepresentativeName(), properties.getCompany().getRepresentativeTitle());
    }

    public Result create(Long employeeId, AssignmentDecisionRequestDTO req) {
        if (req.type() != WorkLocationType.CONG_TAC && req.type() != WorkLocationType.KHACH_HANG) {
            throw new EmployeeRuleException("Quyết định cử chỉ dùng cho đi công tác hoặc làm việc tại khách hàng", "decisiontype");
        }
        if (req.fromDate() == null || blank(req.task()) || blank(req.benefits())) {
            throw new EmployeeRuleException("Cần ngày bắt đầu, nội dung công việc và chế độ", "decisionfields");
        }
        LocalDate signDate = req.signDate() != null ? req.signDate() : LocalDate.now(clock);
        String signerName = blank(req.signerName()) ? properties.getCompany().getRepresentativeName() : req.signerName().trim();
        String signerTitle = blank(req.signerTitle()) ? properties.getCompany().getRepresentativeTitle() : req.signerTitle().trim();
        if (blank(signerName) || blank(signerTitle)) {
            throw new EmployeeRuleException("Cần người ký và chức vụ người ký", "decisionsigner");
        }

        Created c = tx.execute(s -> {
            Employee e = employeeRepository
                .findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));
            DocumentTemplateService.ActiveTemplate template = templateService
                .active(TemplateKind.QD_CU_NHAN_SU)
                .orElseThrow(() -> new EmployeeRuleException("Chưa có mẫu quyết định cử nhân sự", "notemplate"));
            String number = numberService.next(DocumentType.QD_CU_NHAN_SU, signDate.getYear());

            WorkAssignmentDTO segment = new WorkAssignmentDTO();
            segment.setType(req.type());
            segment.setUnit(trim(req.unit()));
            segment.setAddress(trim(req.address()));
            if (req.siteId() != null) {
                WorkSiteDTO site = new WorkSiteDTO();
                site.setId(req.siteId());
                segment.setSite(site);
            }
            segment.setFromDate(req.fromDate());
            segment.setToDate(req.toDate());
            segment.setDecisionNo(number);
            WorkAssignmentDTO added = workAssignmentService.add(employeeId, segment);
            // Nơi làm việc đã chọn quyết định loại giai đoạn: nơi văn phòng / làm từ xa không dùng cho quyết định cử.
            if (added.getType() != WorkLocationType.CONG_TAC && added.getType() != WorkLocationType.KHACH_HANG) {
                throw new EmployeeRuleException(
                    "Nơi đã chọn không phải nơi công tác hoặc khách hàng; quyết định cử chỉ dùng cho hai loại này",
                    "decisiontype"
                );
            }

            AssignmentDecisionRecord d = new AssignmentDecisionRecord();
            d.setDecisionNo(number);
            d.setEmployeeId(employeeId);
            d.setAssignmentId(added.getId());
            d.setType(added.getType());
            d.setUnit(added.getUnit());
            d.setAddress(added.getAddress());
            d.setFromDate(added.getFromDate());
            d.setToDate(added.getToDate());
            d.setSignDate(signDate);
            d.setSignerName(signerName);
            d.setSignerTitle(signerTitle);
            d.setTask(req.task().trim());
            d.setBenefits(req.benefits().trim());
            d.setCreatedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
            d.setCreatedAt(Instant.now(clock));
            decisionRepository.saveAndFlush(d);

            byte[] docx = files.render(template.content(), toCore(d, e).toFields());
            GeneratedDocument saved = files.save(
                DocumentOwnerType.DECISION,
                d.getId(),
                GeneratedFileKind.DOCX,
                DocumentFiles.baseName(number, e.getFullName()) + ".docx",
                docx,
                TemplateKind.QD_CU_NHAN_SU,
                template.version(),
                null
            );
            return new Created(d, saved, docx);
        });
        DocumentFiles.PdfOutcome pdf = files.toPdf(c.docx(), c.content());
        return new Result(toDto(c.decision()), DocumentFiles.toDto(c.docx()), pdf.pdf(), pdf.error());
    }

    @Transactional(readOnly = true)
    public List<DecisionWithDocuments> list(Long employeeId) {
        return decisionRepository
            .findByEmployeeIdOrderByIdDesc(employeeId)
            .stream()
            .map(d ->
                new DecisionWithDocuments(
                    toDto(d),
                    documentRepository
                        .findByOwnerTypeAndOwnerIdOrderByIdDesc(DocumentOwnerType.DECISION, d.getId())
                        .stream()
                        .map(DocumentFiles::toDto)
                        .toList()
                )
            )
            .toList();
    }

    /** Tạo lại PDF từ .docx mới nhất của quyết định. */
    public Result regeneratePdf(Long decisionId) {
        AssignmentDecisionRecord d = decisionRepository
            .findById(decisionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy quyết định"));
        GeneratedDocument docx = documentRepository
            .findFirstByOwnerTypeAndOwnerIdAndKindOrderByIdDesc(DocumentOwnerType.DECISION, decisionId, GeneratedFileKind.DOCX)
            .orElseThrow(() -> new EmployeeRuleException("Quyết định chưa có văn bản .docx", "nodocx"));
        DocumentFiles.PdfOutcome pdf = files.toPdf(docx, files.read(docx));
        return new Result(toDto(d), DocumentFiles.toDto(docx), pdf.pdf(), pdf.error());
    }

    /** Dữ liệu cho mẫu: số HĐ là của hợp đồng đang hiệu lực vào ngày ký. */
    private AssignmentDecision toCore(AssignmentDecisionRecord d, Employee e) {
        List<EmploymentContract> contracts = contractRepository.findByEmployeeId(e.getId());
        String contractNo = ContractRules.currentContract(contracts, e.getStatus(), d.getSignDate())
            .map(EmploymentContract::getContractNo)
            .orElse(null);
        return new AssignmentDecision(
            d.getDecisionNo(),
            d.getSignDate(),
            d.getSignerName(),
            d.getSignerTitle(),
            e.getFullName(),
            e.getCode(),
            e.getPosition(),
            e.getDepartment() == null ? null : e.getDepartment().getName(),
            contractNo,
            new Assignment(
                0,
                e.getCode(),
                AssignmentMapping.toCore(d.getType()),
                d.getUnit(),
                d.getAddress(),
                d.getFromDate(),
                d.getToDate(),
                d.getDecisionNo(),
                null
            ),
            d.getTask(),
            d.getBenefits()
        );
    }

    private static AssignmentDecisionDTO toDto(AssignmentDecisionRecord d) {
        return new AssignmentDecisionDTO(
            d.getId(),
            d.getDecisionNo(),
            d.getAssignmentId(),
            d.getType(),
            d.getUnit(),
            d.getAddress(),
            d.getFromDate(),
            d.getToDate(),
            d.getSignDate(),
            d.getSignerName(),
            d.getSignerTitle(),
            d.getTask(),
            d.getBenefits()
        );
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }
}
