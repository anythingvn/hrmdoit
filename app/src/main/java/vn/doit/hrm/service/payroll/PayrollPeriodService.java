package vn.doit.hrm.service.payroll;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import vn.doit.hr.payroll.PayrollTemplateWriter;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollCalcDraftLine;
import vn.doit.hrm.domain.PayrollImport;
import vn.doit.hrm.domain.PayrollLineRecord;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.PayrollPeriodEvent;
import vn.doit.hrm.domain.PayrollVersion;
import vn.doit.hrm.domain.enumeration.PayrollCalcStatus;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodAction;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PayrollVersionSource;
import vn.doit.hrm.repository.PayrollImportRepository;
import vn.doit.hrm.repository.PayrollLineRecordRepository;
import vn.doit.hrm.repository.PayrollPeriodEventRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayrollVersionRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.FileStorageService;
import vn.doit.hrm.service.attendance.AttendanceMonthService;
import vn.doit.hrm.service.payroll.calc.PayrollCalcDraftService;
import vn.doit.hrm.service.payroll.dto.ConfirmedDTO;
import vn.doit.hrm.service.payroll.dto.ImportCheckDTO;
import vn.doit.hrm.service.payroll.dto.IssueDTO;
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
    private final vn.doit.hrm.repository.PayslipDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final AttendanceMonthService attendanceMonthService;
    private final Clock clock;
    private final PayrollCalcDraftService calcDraftService;
    private final FileStorageService storage;

    public PayrollPeriodService(
        PayrollPeriodRepository periodRepository,
        PayrollVersionRepository versionRepository,
        PayrollLineRecordRepository lineRepository,
        PayrollPeriodEventRepository eventRepository,
        PayrollImportRepository importRepository,
        PayrollImportService importService,
        RuleBookService ruleBookService,
        vn.doit.hrm.repository.PayslipDeliveryRepository deliveryRepository,
        ObjectMapper objectMapper,
        AttendanceMonthService attendanceMonthService,
        Clock clock,
        PayrollCalcDraftService calcDraftService,
        FileStorageService storage
    ) {
        this.calcDraftService = calcDraftService;
        this.storage = storage;
        this.attendanceMonthService = attendanceMonthService;
        this.periodRepository = periodRepository;
        this.versionRepository = versionRepository;
        this.lineRepository = lineRepository;
        this.eventRepository = eventRepository;
        this.importRepository = importRepository;
        this.importService = importService;
        this.ruleBookService = ruleBookService;
        this.deliveryRepository = deliveryRepository;
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
        String legalBasis,
        /** Thông số tính lương đã dùng (chỉ phiên bản tính tự động có). */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        vn.doit.hr.payroll.calc.CalcSettings calcSettings
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
        PayrollVersion v = createVersion(
            period,
            nextVersion(period),
            draft,
            PayrollVersionSource.NHAP_FILE,
            draft.getFileName(),
            draft.getStoragePath(),
            rulesSnapshot(YearMonth.parse(period.getPeriod()), null),
            objectMapper.writeValueAsString(check.issues())
        );

        Map<Integer, RowDTO> rows = new LinkedHashMap<>();
        check.rows().forEach(r -> rows.put(r.excelRow(), r));
        for (PayrollLine line : loaded.result().lines()) {
            RowDTO row = rows.get(line.excelRow());
            saveLine(
                v,
                loaded.result().resolved().get(line.excelRow()),
                line.excelRow(),
                row.fileCode(),
                line.fullName(),
                line.position(),
                row.mappedManually(),
                line.amounts()
            );
        }
        draft.setStatus(PayrollImportStatus.DA_XAC_NHAN);
        return new ConfirmedDTO(period.getPeriod(), v.getVersion());
    }

    /**
     * Xác nhận bản tính nháp thành phiên bản mới của kỳ (nguồn TINH_TOAN, không gắn bản nhập file): dòng lương như phiên bản nhập
     * file, file Excel 36 cột sinh từ số đã tính làm file của phiên bản. Khóa, điều kiện: xem {@link PayrollCalcDraftService#lockForConfirm}.
     */
    public ConfirmedDTO confirmCalc(Long draftId, boolean warningsAcknowledged) {
        PayrollCalcDraftService.Confirmable c = calcDraftService.lockForConfirm(draftId, warningsAcknowledged);
        PayrollPeriod period = c.draft().getPeriod();
        YearMonth month = YearMonth.parse(period.getPeriod());
        int next = nextVersion(period);

        List<PayrollTemplateWriter.FullRow> fileRows = new java.util.ArrayList<>();
        List<IssueDTO> issues = new java.util.ArrayList<>();
        int excelRow = 0;
        for (PayrollCalcDraftLine l : c.lines()) {
            excelRow++;
            fileRows.add(
                new PayrollTemplateWriter.FullRow(l.getCode(), l.getFullName(), l.getPosition(), null, calcDraftService.amountsOf(l))
            );
            for (String w : calcDraftService.warningsOf(l)) {
                issues.add(new IssueDTO("WARNING", excelRow, l.getCode(), null, null, w));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new PayrollTemplateWriter().writeFull(out, month, fileRows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String fileName = "bang-luong-" + period.getPeriod() + "-tinh-toan-v" + next + ".xlsx";
        String path = storage.store("payroll/" + period.getPeriod(), fileName, new ByteArrayInputStream(out.toByteArray()));
        try {
            PayrollVersion v = createVersion(
                period,
                next,
                null,
                PayrollVersionSource.TINH_TOAN,
                fileName,
                path,
                rulesSnapshot(month, c.settings()),
                objectMapper.writeValueAsString(issues)
            );
            excelRow = 0;
            for (PayrollCalcDraftLine l : c.lines()) {
                saveLine(
                    v,
                    l.getEmployee(),
                    ++excelRow,
                    l.getCode(),
                    l.getFullName(),
                    l.getPosition(),
                    false,
                    calcDraftService.amountsOf(l)
                );
            }
            c.draft().setStatus(PayrollCalcStatus.DA_XAC_NHAN);
            return new ConfirmedDTO(period.getPeriod(), v.getVersion());
        } catch (RuntimeException e) {
            storage.delete(path);
            throw e;
        }
    }

    private int nextVersion(PayrollPeriod period) {
        Integer max = versionRepository.maxVersion(period.getId());
        return max == null ? 1 : max + 1;
    }

    private PayrollVersion createVersion(
        PayrollPeriod period,
        int version,
        PayrollImport payrollImport,
        PayrollVersionSource source,
        String fileName,
        String storagePath,
        String rulesSnapshot,
        String issuesSnapshot
    ) {
        PayrollVersion v = new PayrollVersion();
        v.setPeriod(period);
        v.setVersion(version);
        v.setPayrollImport(payrollImport);
        v.setSource(source);
        v.setFileName(fileName);
        v.setStoragePath(storagePath);
        v.setImportedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        v.setImportedAt(Instant.now(clock));
        v.setRulesSnapshot(rulesSnapshot);
        v.setIssuesSnapshot(issuesSnapshot);
        return versionRepository.save(v);
    }

    private void saveLine(
        PayrollVersion v,
        Employee e,
        int excelRow,
        String fileCode,
        String name,
        String position,
        boolean mappedManually,
        Map<PayrollField, BigDecimal> lineAmounts
    ) {
        PayrollLineRecord rec = new PayrollLineRecord();
        rec.setVersion(v);
        rec.setEmployee(e);
        rec.setExcelRow(excelRow);
        rec.setFileCode(fileCode);
        rec.setFileName(name);
        rec.setPosition(position);
        rec.setMappedManually(mappedManually);
        Map<String, String> amounts = new LinkedHashMap<>();
        lineAmounts.forEach((f, a) -> amounts.put(f.name(), a.toPlainString()));
        rec.setAmounts(objectMapper.writeValueAsString(amounts));
        rec.setThucNhan(lineAmounts.getOrDefault(PayrollField.THUC_NHAN, BigDecimal.ZERO));
        rec.setTongChiPhi(lineAmounts.getOrDefault(PayrollField.TONG_CHI_PHI, BigDecimal.ZERO));
        rec.setThueTncn(lineAmounts.getOrDefault(PayrollField.THUE_TNCN, BigDecimal.ZERO));
        rec.setTongBhNld(lineAmounts.getOrDefault(PayrollField.TONG_BH_NLD, BigDecimal.ZERO));
        lineRepository.save(rec);
    }

    /** Duyệt phiên bản mới nhất. Bảng công tháng đó chưa chốt vẫn duyệt được, kèm cảnh báo trong {@code warnings}. */
    public PeriodDTO approve(String periodText) {
        YearMonth month = PayrollImportService.parsePeriod(periodText);
        String key = month.toString();
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
        List<String> warnings = attendanceMonthService.isClosed(month) ? List.of() : List.of(PayrollCheck.ATTENDANCE_NOT_CLOSED);
        return toDto(p, warnings);
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
        // Phiếu lương chưa gửi kịp của phiên bản cũ không gửi nữa.
        deliveryRepository.failPending(p.getId(), vn.doit.hrm.service.payslip.PayslipSendService.REOPENED);
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

    private String rulesSnapshot(YearMonth period, vn.doit.hr.payroll.calc.CalcSettings calcSettings) {
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
                        ruleBookService.legalBasis(l.effectiveFrom()).orElse(null),
                        calcSettings
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
        return toDto(p, List.of());
    }

    private static PeriodDTO toDto(PayrollPeriod p, List<String> warnings) {
        return new PeriodDTO(p.getPeriod(), p.getStatus(), p.getApprovedVersion(), p.getApprovedBy(), p.getApprovedAt(), warnings);
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
