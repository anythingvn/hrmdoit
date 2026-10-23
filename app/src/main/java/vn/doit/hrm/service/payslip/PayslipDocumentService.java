package vn.doit.hrm.service.payslip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.contract.DocxTemplateRenderer;
import vn.doit.hr.payroll.Payslip;
import vn.doit.hr.payroll.PayslipFields;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollLineRecord;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.PayrollVersion;
import vn.doit.hrm.domain.SensitiveAccessLog;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.TemplateKind;
import vn.doit.hrm.repository.PayrollLineRecordRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayrollVersionRepository;
import vn.doit.hrm.repository.SensitiveAccessLogRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.CompanySettingService;
import vn.doit.hrm.service.DocumentTemplateService;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.payroll.PayrollAmounts;
import vn.doit.hrm.service.payroll.PayrollPeriodService;
import vn.doit.hrm.service.pdf.PdfService;
import vn.doit.hrm.service.pdf.PdfUnavailableException;

/**
 * Sinh phiếu lương của một người từ dòng lương đã lưu: mẫu Word "Phiếu lương" → .docx → PDF (qua dịch vụ PDF).
 * Không lưu file; ghi chú lấy theo bản chụp luật của phiên bản (luật đổi không tác động ngược).
 */
@Service
public class PayslipDocumentService {

    /** Phiếu đã dựng của một người, kèm độ lệch so với "Lương thực nhận" của dòng. */
    public record Prepared(YearMonth period, int version, Employee employee, Payslip payslip, BigDecimal differenceFromNet, byte[] docx) {
        public String fileName() {
            return PayslipDocumentService.fileName(period, employee.getCode());
        }
    }

    private final PayrollPeriodRepository periodRepository;
    private final PayrollVersionRepository versionRepository;
    private final PayrollLineRecordRepository lineRepository;
    private final PayrollPeriodService periodService;
    private final DocumentTemplateService templateService;
    private final PdfService pdfService;
    private final SensitiveAccessLogRepository accessLogRepository;
    private final CompanySettingService companySetting;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PayslipDocumentService(
        PayrollPeriodRepository periodRepository,
        PayrollVersionRepository versionRepository,
        PayrollLineRecordRepository lineRepository,
        PayrollPeriodService periodService,
        DocumentTemplateService templateService,
        PdfService pdfService,
        SensitiveAccessLogRepository accessLogRepository,
        CompanySettingService companySetting,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.periodRepository = periodRepository;
        this.versionRepository = versionRepository;
        this.lineRepository = lineRepository;
        this.periodService = periodService;
        this.templateService = templateService;
        this.pdfService = pdfService;
        this.accessLogRepository = accessLogRepository;
        this.companySetting = companySetting;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public static String fileName(YearMonth period, String employeeCode) {
        return "PhieuLuong_%02d-%d_%s.pdf".formatted(period.getMonthValue(), period.getYear(), employeeCode);
    }

    /** Kỳ đã duyệt; không có kỳ → 404, chưa duyệt → 400 {@code payslipnotapproved}. */
    @Transactional(readOnly = true)
    public PayrollPeriod approved(String period) {
        PayrollPeriod p = periodRepository
            .findByPeriod(period)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có kỳ lương " + period));
        if (p.getStatus() != PayrollPeriodStatus.DA_DUYET) {
            throw new EmployeeRuleException("Kỳ lương chưa được duyệt, chưa gửi hoặc xem phiếu lương được.", "payslipnotapproved");
        }
        return p;
    }

    /** Phiên bản chính thức của kỳ đã duyệt. */
    @Transactional(readOnly = true)
    public int officialVersion(PayrollPeriod p) {
        return periodService.officialVersion(p);
    }

    /** Dựng phiếu (.docx) của một người theo phiên bản; người không có dòng trong phiên bản → 400 {@code payslipnoline}. */
    @Transactional(readOnly = true)
    public Prepared prepare(PayrollPeriod period, int version, Long employeeId) {
        PayrollVersion v = versionRepository.findByPeriodIdAndVersion(period.getId(), version).orElseThrow();
        PayrollLineRecord line = lineRepository
            .findByVersionIdOrderByExcelRowAsc(v.getId())
            .stream()
            .filter(l -> l.getEmployee() != null && l.getEmployee().getId().equals(employeeId))
            .findFirst()
            .orElseThrow(() -> new EmployeeRuleException("Người này không có trong bảng lương của kỳ.", "payslipnoline"));
        Employee e = line.getEmployee();
        var amounts = PayrollAmounts.parse(objectMapper, line.getAmounts());
        Payslip payslip = Payslip.of(amounts);
        YearMonth ym = YearMonth.parse(period.getPeriod());
        var fields = PayslipFields.toMap(
            new PayslipFields.Header(companySetting.companyName(), ym, e.getFullName(), e.getCode()),
            payslip,
            notes(v)
        );
        byte[] template = templateService
            .activeTemplate(TemplateKind.PHIEU_LUONG)
            .orElseThrow(() -> new EmployeeRuleException("Chưa có mẫu phiếu lương", "notemplate"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new DocxTemplateRenderer().render(new ByteArrayInputStream(template), fields, out);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new Prepared(ym, version, e, payslip, payslip.differenceFromNet(amounts), out.toByteArray());
    }

    /** PDF không mật khẩu; dịch vụ PDF không chạy thì ném {@link PdfUnavailableException}. */
    public byte[] pdf(Prepared p) {
        return pdfService.toPdf(p.docx(), p.fileName().replaceFirst("\\.pdf$", ".docx"));
    }

    /** Kế toán, Quản trị tải phiếu (không mật khẩu) của một người trong kỳ đã duyệt để kiểm tra; ghi nhật ký truy cập. */
    public Prepared forReview(String period, Long employeeId) {
        PayrollPeriod p = approved(period);
        Prepared prepared = prepare(p, officialVersion(p), employeeId);
        logAccess(employeeId);
        return prepared;
    }

    public void logAccess(Long employeeId) {
        accessLogRepository.save(new SensitiveAccessLog(employeeId, SecurityUtils.getCurrentUserLogin().orElse(null), Instant.now(clock)));
    }

    private PayslipFields.Notes notes(PayrollVersion v) {
        if (v.getRulesSnapshot() == null) {
            return null;
        }
        PayrollPeriodService.RulesSnapshot r = objectMapper.readValue(v.getRulesSnapshot(), PayrollPeriodService.RulesSnapshot.class);
        return new PayslipFields.Notes(
            r.socialInsuranceCap(),
            r.unemploymentInsuranceCap(),
            r.personalDeduction(),
            r.dependantDeduction(),
            r.legalBasis()
        );
    }
}
