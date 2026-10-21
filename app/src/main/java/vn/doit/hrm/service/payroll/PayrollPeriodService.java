package vn.doit.hrm.service.payroll;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollRules;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollImport;
import vn.doit.hrm.domain.PayrollLineRecord;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.PayrollPeriodEvent;
import vn.doit.hrm.domain.PayrollVersion;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodAction;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.repository.PayrollImportRepository;
import vn.doit.hrm.repository.PayrollLineRecordRepository;
import vn.doit.hrm.repository.PayrollPeriodEventRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayrollVersionRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.payroll.dto.ConfirmedDTO;
import vn.doit.hrm.service.payroll.dto.ImportCheckDTO;
import vn.doit.hrm.service.payroll.dto.PeriodDTO;
import vn.doit.hrm.service.payroll.dto.RowDTO;

/**
 * Kỳ lương: xác nhận bản nháp thành phiên bản mới (khóa dòng kỳ), Quản trị duyệt / mở lại. Mỗi phiên bản lưu bản chụp mốc luật và
 * lỗi/cảnh báo lúc xác nhận; số liệu lưu đúng như file, không bao giờ tính lại khi luật đổi.
 */
@Service
@Transactional
public class PayrollPeriodService {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollVersionRepository versionRepository;
    private final PayrollLineRecordRepository lineRepository;
    private final PayrollPeriodEventRepository eventRepository;
    private final PayrollImportRepository importRepository;
    private final PayrollImportService importService;
    private final RuleBookService ruleBookService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PayrollPeriodService(
        PayrollPeriodRepository periodRepository,
        PayrollVersionRepository versionRepository,
        PayrollLineRecordRepository lineRepository,
        PayrollPeriodEventRepository eventRepository,
        PayrollImportRepository importRepository,
        PayrollImportService importService,
        RuleBookService ruleBookService,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.periodRepository = periodRepository;
        this.versionRepository = versionRepository;
        this.lineRepository = lineRepository;
        this.eventRepository = eventRepository;
        this.importRepository = importRepository;
        this.importService = importService;
        this.ruleBookService = ruleBookService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Mốc luật đã dùng cho phiên bản, lưu cùng phiên bản. */
    public record RulesSnapshot(
        String effectiveFrom,
        boolean estimated,
        BigDecimal personalDeduction,
        BigDecimal dependantDeduction,
        BigDecimal socialInsuranceCap,
        BigDecimal unemploymentInsuranceCap,
        List<PayrollRules.Bracket> brackets,
        /** Căn cứ văn bản của mốc luật; phiên bản lưu trước Đợt 2 không có. */
        String legalBasis
    ) {}

    public ConfirmedDTO confirm(Long importId) {
        PayrollImport draft = importService.open(importId);
        // Khóa kỳ trước, rồi khóa bản nháp và kiểm tra lại: hai lần xác nhận / duyệt cùng lúc chạy lần lượt.
        PayrollPeriod period = lock(draft.getPeriod());
        draft = importRepository.findForUpdate(importId).orElseThrow();
        if (draft.getStatus() != PayrollImportStatus.DANG_LAM) {
            throw new EmployeeRuleException("Bản nháp này đã xác nhận hoặc đã hủy", "importclosed");
        }
        if (period.getStatus() == PayrollPeriodStatus.DA_DUYET) {
            throw approved(period.getPeriod());
        }
        PayrollImportService.Loaded loaded = importService.load(importId);
        ImportCheckDTO check = importService.toDto(loaded);
        if (!check.canConfirm()) {
            throw new EmployeeRuleException("Bản nháp còn lỗi hoặc chưa xác nhận cảnh báo", "importnotready");
        }
        Integer max = versionRepository.maxVersion(period.getId());
        PayrollVersion v = new PayrollVersion();
        v.setPeriod(period);
        v.setVersion(max == null ? 1 : max + 1);
        v.setPayrollImport(draft);
        v.setFileName(draft.getFileName());
        v.setStoragePath(draft.getStoragePath());
        v.setImportedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        v.setImportedAt(Instant.now(clock));
        v.setRulesSnapshot(rulesSnapshot(YearMonth.parse(period.getPeriod())));
        v.setIssuesSnapshot(objectMapper.writeValueAsString(check.issues()));
        versionRepository.save(v);

        Map<Integer, RowDTO> rows = new LinkedHashMap<>();
        check.rows().forEach(r -> rows.put(r.excelRow(), r));
        for (PayrollLine line : loaded.result().lines()) {
            RowDTO row = rows.get(line.excelRow());
            Employee e = loaded.result().resolved().get(line.excelRow());
            PayrollLineRecord rec = new PayrollLineRecord();
            rec.setVersion(v);
            rec.setEmployee(e);
            rec.setExcelRow(line.excelRow());
            rec.setFileCode(row.fileCode());
            rec.setFileName(line.fullName());
            rec.setPosition(line.position());
            rec.setMappedManually(row.mappedManually());
            Map<String, String> amounts = new LinkedHashMap<>();
            line.amounts().forEach((f, a) -> amounts.put(f.name(), a.toPlainString()));
            rec.setAmounts(objectMapper.writeValueAsString(amounts));
            rec.setThucNhan(line.get(PayrollField.THUC_NHAN));
            rec.setTongChiPhi(line.get(PayrollField.TONG_CHI_PHI));
            rec.setThueTncn(line.get(PayrollField.THUE_TNCN));
            rec.setTongBhNld(line.get(PayrollField.TONG_BH_NLD));
            lineRepository.save(rec);
        }
        draft.setStatus(PayrollImportStatus.DA_XAC_NHAN);
        return new ConfirmedDTO(period.getPeriod(), v.getVersion());
    }

    public PeriodDTO approve(String periodText) {
        String key = PayrollImportService.parsePeriod(periodText).toString();
        PayrollPeriod p = periodRepository.findForUpdate(key).orElseThrow(PayrollPeriodService::noVersion);
        if (p.getStatus() == PayrollPeriodStatus.DA_DUYET) {
            throw new EmployeeRuleException("Kỳ " + display(key) + " đã được duyệt", "alreadyapproved");
        }
        Integer latest = versionRepository.maxVersion(p.getId());
        if (latest == null) {
            throw noVersion();
        }
        p.setStatus(PayrollPeriodStatus.DA_DUYET);
        p.setApprovedVersion(latest);
        p.setApprovedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        p.setApprovedAt(Instant.now(clock));
        event(p, PayrollPeriodAction.DUYET, latest, null);
        return toDto(p);
    }

    public PeriodDTO reopen(String periodText, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new EmployeeRuleException("Cần nhập lý do mở lại kỳ lương", "reasonrequired");
        }
        String key = PayrollImportService.parsePeriod(periodText).toString();
        PayrollPeriod p = periodRepository.findForUpdate(key).orElseThrow(() -> notApproved(key));
        if (p.getStatus() != PayrollPeriodStatus.DA_DUYET) {
            throw notApproved(key);
        }
        // Phiên bản đã duyệt được ghi trong sự kiện; kỳ về Nháp, phiên bản chính thức là phiên bản mới nhất cho đến khi duyệt lại.
        event(p, PayrollPeriodAction.MO_LAI, p.getApprovedVersion(), reason.trim());
        p.setStatus(PayrollPeriodStatus.NHAP);
        p.setApprovedVersion(null);
        p.setApprovedBy(null);
        p.setApprovedAt(null);
        return toDto(p);
    }

    /** Phiên bản chính thức: phiên bản đã duyệt nếu kỳ đang Đã duyệt, ngược lại phiên bản mới nhất (null nếu chưa có). */
    @Transactional(readOnly = true)
    public Integer officialVersion(PayrollPeriod p) {
        if (p.getStatus() == PayrollPeriodStatus.DA_DUYET && p.getApprovedVersion() != null) {
            return p.getApprovedVersion();
        }
        return versionRepository.maxVersion(p.getId());
    }

    private PayrollPeriod lock(String period) {
        periodRepository.insertIfMissing(period);
        return periodRepository.findForUpdate(period).orElseThrow();
    }

    private String rulesSnapshot(YearMonth period) {
        return ruleBookService
            .current()
            .forPeriodOrLatest(period)
            .map(l ->
                objectMapper.writeValueAsString(
                    new RulesSnapshot(
                        l.effectiveFrom().toString(),
                        l.estimated(),
                        l.rules().personalDeduction(),
                        l.rules().dependantDeduction(),
                        l.rules().socialInsuranceCap(),
                        l.rules().unemploymentInsuranceCap(),
                        l.rules().pitBrackets(),
                        ruleBookService.legalBasis(l.effectiveFrom()).orElse(null)
                    )
                )
            )
            .orElse(null);
    }

    private void event(PayrollPeriod p, PayrollPeriodAction action, Integer version, String reason) {
        PayrollPeriodEvent e = new PayrollPeriodEvent();
        e.setPeriod(p);
        e.setAction(action);
        e.setVersion(version);
        e.setActor(SecurityUtils.getCurrentUserLogin().orElse(null));
        e.setEventAt(Instant.now(clock));
        e.setReason(reason);
        eventRepository.save(e);
    }

    static PeriodDTO toDto(PayrollPeriod p) {
        return new PeriodDTO(p.getPeriod(), p.getStatus(), p.getApprovedVersion(), p.getApprovedBy(), p.getApprovedAt());
    }

    static String display(String period) {
        return period.substring(5) + "/" + period.substring(0, 4);
    }

    private static EmployeeRuleException approved(String period) {
        return new EmployeeRuleException(
            "Kỳ " + display(period) + " đã được duyệt; cần Quản trị mở lại trước khi import",
            "periodapproved"
        );
    }

    private static EmployeeRuleException noVersion() {
        return new EmployeeRuleException("Kỳ này chưa có phiên bản bảng lương nào để duyệt", "noversion");
    }

    private static EmployeeRuleException notApproved(String period) {
        return new EmployeeRuleException("Kỳ " + display(period) + " chưa được duyệt", "notapproved");
    }
}
