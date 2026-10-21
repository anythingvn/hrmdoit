package vn.doit.hrm.service.payroll;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hr.payroll.Issue;
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.PoiSheetGrid;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollImport;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollImportMappingRepository;
import vn.doit.hrm.repository.PayrollImportRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.FileStorageService;
import vn.doit.hrm.service.UploadNames;
import vn.doit.hrm.service.payroll.dto.ImportCheckDTO;
import vn.doit.hrm.service.payroll.dto.ImportSummaryDTO;
import vn.doit.hrm.service.payroll.dto.RowDTO;

/**
 * Bản nháp import bảng lương (Kế toán, Quản trị): tải file → đọc, nhận diện cột, kiểm tra → gắn hồ sơ → xác nhận. File gốc lưu
 * một lần và không đổi; kết quả đọc và kiểm tra được tính lại từ file mỗi lần cần, cùng các dòng đã gắn tay.
 */
@Service
@Transactional
public class PayrollImportService {

    static final long MAX_BYTES = 20L * 1024 * 1024;

    private static final int PARSED_CACHE_SIZE = 16;

    /** Kết quả đọc file theo đường dẫn lưu trữ (file không đổi); giữ {@value #PARSED_CACHE_SIZE} bản gần nhất. */
    private final Map<String, ParsedPayroll> parsedCache = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ParsedPayroll> eldest) {
            return size() > PARSED_CACHE_SIZE;
        }
    };

    private final PayrollImportRepository importRepository;
    private final PayrollImportMappingRepository mappingRepository;
    private final EmployeeRepository employeeRepository;
    private final FileStorageService storage;
    private final RuleBookService ruleBookService;
    private final PayrollPeriodRepository periodRepository;
    private final Clock clock;

    public PayrollImportService(
        PayrollImportRepository importRepository,
        PayrollImportMappingRepository mappingRepository,
        EmployeeRepository employeeRepository,
        FileStorageService storage,
        RuleBookService ruleBookService,
        PayrollPeriodRepository periodRepository,
        Clock clock
    ) {
        this.periodRepository = periodRepository;
        this.importRepository = importRepository;
        this.mappingRepository = mappingRepository;
        this.employeeRepository = employeeRepository;
        this.storage = storage;
        this.ruleBookService = ruleBookService;
        this.clock = clock;
    }

    /** Kết quả đọc một bản nháp: bản nháp, file đã đọc, kết quả kiểm tra. */
    record Loaded(PayrollImport draft, ParsedPayroll parsed, PayrollCheck.Result result) {}

    public ImportCheckDTO create(String periodText, MultipartFile file) {
        YearMonth period = parsePeriod(periodText);
        periodRepository
            .findByPeriod(period.toString())
            .filter(p -> p.getStatus() == PayrollPeriodStatus.DA_DUYET)
            .ifPresent(p -> {
                throw new EmployeeRuleException(
                    "Kỳ " + PayrollPeriodService.display(p.getPeriod()) + " đã được duyệt; cần Quản trị mở lại trước khi import",
                    "periodapproved"
                );
            });
        String name = UploadNames.displayName(file.getOriginalFilename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw notXlsx();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_BYTES);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ParsedPayroll parsed = parse(content);
        if (parsed.columns().isEmpty()) {
            String detail = parsed
                .issues()
                .stream()
                .filter(i -> i.severity() == Issue.Severity.ERROR)
                .map(Issue::message)
                .collect(Collectors.joining(" "));
            throw new EmployeeRuleException(detail.isEmpty() ? "Không đọc được bảng lương trong file" : detail, "payrollfilelayout");
        }
        String path = storage.store("payroll/" + period, name, new ByteArrayInputStream(content));
        PayrollImport draft = new PayrollImport();
        draft.setPeriod(period.toString());
        draft.setFileName(name);
        draft.setStoragePath(path);
        draft.setStatus(PayrollImportStatus.DANG_LAM);
        draft.setCreatedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        draft.setCreatedAt(Instant.now(clock));
        try {
            importRepository.saveAndFlush(draft);
        } catch (RuntimeException e) {
            storage.delete(path);
            throw e;
        }
        return toDto(load(draft.getId()));
    }

    @Transactional(readOnly = true)
    public ImportCheckDTO check(Long id) {
        return toDto(load(id));
    }

    @Transactional(readOnly = true)
    public List<ImportSummaryDTO> list(PayrollImportStatus status) {
        List<PayrollImport> drafts = status == null ? importRepository.findAllByOrderByIdDesc() : importRepository.findByStatusOrderByIdDesc(status);
        return drafts
            .stream()
            .map(d -> new ImportSummaryDTO(d.getId(), d.getPeriod(), d.getFileName(), d.getStatus(), d.getCreatedBy(), d.getCreatedAt()))
            .toList();
    }

    public ImportCheckDTO acknowledgeWarnings(Long id) {
        PayrollImport d = open(id);
        d.setWarningsAcknowledged(true);
        return toDto(load(id));
    }

    public void cancel(Long id) {
        open(id).setStatus(PayrollImportStatus.HUY);
    }

    @Transactional(readOnly = true)
    public byte[] issueReport(Long id) {
        Loaded l = load(id);
        return IssueReportWriter.write(l.result().issues(), namesByRow(l.result().rows()));
    }

    /** Đọc lại bản nháp: file gốc + các dòng gắn tay, chạy kiểm tra theo mức luật hiện hành. */
    Loaded load(Long id) {
        PayrollImport d = importRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản nháp"));
        ParsedPayroll parsed = parsedFile(d);
        Map<Integer, Employee> mappings = new HashMap<>();
        mappingRepository.findByPayrollImportId(id).forEach(m -> mappings.put(m.getExcelRow(), m.getEmployee()));
        PayrollCheck.Result result = PayrollCheck.run(
            parsed,
            YearMonth.parse(d.getPeriod()),
            mappings,
            employeeRepository::findOneByCode,
            ruleBookService.current()
        );
        return new Loaded(d, parsed, result);
    }

    /**
     * File gốc của bản nháp đã đọc. File lưu một lần và không đổi, nên kết quả đọc được giữ trong bộ nhớ (vài bản nháp gần nhất)
     * thay vì đọc lại từ đĩa ở mỗi lần xem / gắn hồ sơ.
     */
    private ParsedPayroll parsedFile(PayrollImport d) {
        synchronized (parsedCache) {
            ParsedPayroll cached = parsedCache.get(d.getStoragePath());
            if (cached != null) {
                return cached;
            }
        }
        byte[] content;
        try (InputStream in = storage.load(d.getStoragePath()).getInputStream()) {
            content = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ParsedPayroll parsed = parse(content);
        synchronized (parsedCache) {
            parsedCache.put(d.getStoragePath(), parsed);
        }
        return parsed;
    }

    /** Bản nháp còn đang làm; đã xác nhận hoặc đã hủy → 400 {@code importclosed}. */
    PayrollImport open(Long id) {
        PayrollImport d = importRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản nháp"));
        if (d.getStatus() != PayrollImportStatus.DANG_LAM) {
            throw new EmployeeRuleException("Bản nháp này đã xác nhận hoặc đã hủy", "importclosed");
        }
        return d;
    }

    ImportCheckDTO toDto(Loaded l) {
        PayrollImport d = l.draft();
        PayrollCheck.Result r = l.result();
        boolean canConfirm =
            d.getStatus() == PayrollImportStatus.DANG_LAM &&
            r.errorCount() == 0 &&
            r.missingRequired().isEmpty() &&
            (r.warningCount() == 0 || d.isWarningsAcknowledged());
        return new ImportCheckDTO(
            d.getId(),
            d.getPeriod(),
            d.getFileName(),
            d.getStatus(),
            d.isWarningsAcknowledged(),
            d.getCreatedBy(),
            d.getCreatedAt(),
            r.columns(),
            r.missingRequired(),
            r.rows(),
            r.issues(),
            r.errorCount(),
            r.warningCount(),
            canConfirm
        );
    }

    static YearMonth parsePeriod(String text) {
        try {
            return YearMonth.parse(text == null ? "" : text.trim());
        } catch (DateTimeParseException e) {
            throw new EmployeeRuleException("Kỳ lương phải có dạng yyyy-MM", "periodinvalid");
        }
    }

    private static ParsedPayroll parse(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') {
            throw notXlsx();
        }
        try {
            return new PayrollSheetParser().parse(PoiSheetGrid.read(new ByteArrayInputStream(content)));
        } catch (IOException | RuntimeException e) {
            throw notXlsx();
        }
    }

    private static EmployeeRuleException notXlsx() {
        return new EmployeeRuleException("Chỉ nhận file Excel .xlsx (Excel 2007 trở lên), lưu từ mẫu bảng lương DOIT", "payrollfilenotxlsx");
    }

    /** Họ tên trong file theo dòng (dùng cho thông báo). */
    static Map<Integer, String> namesByRow(List<RowDTO> rows) {
        Map<Integer, String> m = new HashMap<>();
        rows.forEach(r -> m.put(r.excelRow(), r.fileName()));
        return m;
    }
}
