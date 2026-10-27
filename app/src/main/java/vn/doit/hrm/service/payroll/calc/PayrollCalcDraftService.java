package vn.doit.hrm.service.payroll.calc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.payroll.EmployeeDirectory;
import vn.doit.hr.payroll.Issue;
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollRules;
import vn.doit.hr.payroll.PayrollValidator;
import vn.doit.hr.payroll.RuleBook;
import vn.doit.hr.payroll.calc.CalcInput;
import vn.doit.hr.payroll.calc.CalcInput.PersonKind;
import vn.doit.hr.payroll.calc.CalcResult;
import vn.doit.hr.payroll.calc.CalcSettings;
import vn.doit.hr.payroll.calc.PayrollCalculator;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollCalcDraft;
import vn.doit.hrm.domain.PayrollCalcDraftLine;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.enumeration.InsuranceExemption;
import vn.doit.hrm.domain.enumeration.PayrollCalcStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollCalcDraftLineRepository;
import vn.doit.hrm.repository.PayrollCalcDraftRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.payroll.PayrollImportService;
import vn.doit.hrm.service.payroll.PayrollTemplateService;
import vn.doit.hrm.service.payroll.RuleBookService;
import vn.doit.hrm.service.payroll.calc.PayrollInputCollector.CollectedRow;
import vn.doit.hrm.service.payroll.dto.CalcColumnDTO;
import vn.doit.hrm.service.payroll.dto.CalcDraftDTO;
import vn.doit.hrm.service.payroll.dto.CalcEditVM;
import vn.doit.hrm.service.payroll.dto.CalcExcludedDTO;
import vn.doit.hrm.service.payroll.dto.CalcLineDTO;

/**
 * Bản tính lương nháp: tạo từ đầu vào của kỳ, Kế toán nhập / sửa ô, tính lại từ hồ sơ (spec §3). Mỗi dòng lưu đầu vào gom từ hồ sơ,
 * ô nhập tay, ô sửa tay và kết quả; sửa một ô thì dòng được tính lại từ đầu vào + nhập tay + sửa tay. Mọi thao tác ghi khóa dòng kỳ
 * rồi dòng bản nháp (cùng thứ tự với xác nhận kỳ), nên hai người thao tác cùng lúc chạy lần lượt.
 */
@Service
@Transactional
public class PayrollCalcDraftService {

    static final String STALE_ERROR = "Không còn thuộc kỳ";
    static final String NO_TAX_CODE = "Chưa có mã số thuế";

    /** Ô Kế toán nhập tay. */
    static final Set<PayrollField> MANUAL_FIELDS = EnumSet.of(
        PayrollField.THUONG_DU_AN,
        PayrollField.THUONG_KPI,
        PayrollField.TRO_CAP_THOI_VIEC,
        PayrollField.THUA_THIEU,
        PayrollField.QUYET_TOAN_THUE,
        PayrollField.GIAM_TRU_KHAC
    );

    /** Cột tổng: luôn tính, không sửa tay. */
    private static final Set<PayrollField> TOTAL_FIELDS = EnumSet.of(
        PayrollField.TONG_LUONG_GOP,
        PayrollField.TONG_BH_NLD,
        PayrollField.TONG_BH_NSDLD,
        PayrollField.THUC_NHAN,
        PayrollField.TONG_CHI_PHI
    );

    /** Cột số ngày / số người, không phải tiền. */
    private static final Set<PayrollField> COUNT_FIELDS = EnumSet.of(
        PayrollField.NGAY_CONG_CHUAN,
        PayrollField.NGAY_CONG_THUC_TE,
        PayrollField.SO_NGUOI_PHU_THUOC
    );

    /** Khoản tiền được phép âm khi nhập. */
    private static final Set<PayrollField> MAY_BE_NEGATIVE = EnumSet.of(PayrollField.THUA_THIEU, PayrollField.QUYET_TOAN_THUE);

    private static final BigDecimal MAX_DAYS = BigDecimal.valueOf(31);

    private static final TypeReference<Map<PayrollField, BigDecimal>> AMOUNTS = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM/yyyy");

    private final PayrollPeriodRepository periodRepository;
    private final PayrollCalcDraftRepository draftRepository;
    private final PayrollCalcDraftLineRepository lineRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollInputCollector collector;
    private final CalcSettingsService settingsService;
    private final RuleBookService ruleBookService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PayrollTemplateService templateService;

    public PayrollCalcDraftService(
        PayrollPeriodRepository periodRepository,
        PayrollCalcDraftRepository draftRepository,
        PayrollCalcDraftLineRepository lineRepository,
        EmployeeRepository employeeRepository,
        PayrollInputCollector collector,
        CalcSettingsService settingsService,
        RuleBookService ruleBookService,
        ObjectMapper objectMapper,
        Clock clock,
        PayrollTemplateService templateService
    ) {
        this.periodRepository = periodRepository;
        this.draftRepository = draftRepository;
        this.lineRepository = lineRepository;
        this.employeeRepository = employeeRepository;
        this.collector = collector;
        this.settingsService = settingsService;
        this.ruleBookService = ruleBookService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.templateService = templateService;
    }

    /**
     * Mức luật, thông số và bộ đối chiếu dùng để tính các dòng của một kỳ; {@code contractStarts}: ngày bắt đầu HĐLĐ đầu tiên theo
     * nhân viên (đọc dần, đưa vào đối chiếu như cột ngày ký HĐLĐ của file).
     */
    private record Context(
        YearMonth month,
        CalcSettings settings,
        PayrollRules.RulesLookup rules,
        PayrollValidator validator,
        Map<Long, Optional<LocalDate>> contractStarts
    ) {}

    /** Tạo bản nháp của kỳ, hoặc mở bản đang làm (mỗi kỳ một bản). */
    public CalcDraftDTO open(String periodText) {
        YearMonth month = PayrollImportService.parsePeriod(periodText);
        PayrollPeriod period = lock(month.toString());
        if (period.getStatus() == PayrollPeriodStatus.DA_DUYET) {
            throw approved(month);
        }
        Optional<PayrollCalcDraft> existing = draftRepository.findByPeriodAndStatus(period.getId(), PayrollCalcStatus.DANG_LAM);
        if (existing.isPresent()) {
            return toDto(existing.orElseThrow());
        }
        List<CollectedRow> rows = collector.collect(month);
        Context ctx = context(month);
        preloadContractStarts(ctx, rows);
        PayrollCalcDraft draft = new PayrollCalcDraft();
        draft.setPeriod(period);
        draft.setStatus(PayrollCalcStatus.DANG_LAM);
        draft.setCreatedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        draft.setCreatedAt(Instant.now(clock));
        draft.setWarningsAcknowledged(false);
        draftRepository.saveAndFlush(draft);
        Map<Long, Employee> people = employeesOf(rows);
        for (CollectedRow row : rows) {
            PayrollCalcDraftLine line = new PayrollCalcDraftLine();
            line.setDraft(draft);
            line.setManual("{}");
            line.setOverrides("{}");
            fill(line, row, people.get(row.employeeId()), ctx);
            lineRepository.save(line);
        }
        return toDto(draft);
    }

    /** Bản nháp đã khóa và kiểm tra sẵn sàng xác nhận: các dòng được giữ (không bị bỏ), thông số tính của kỳ. */
    public record Confirmable(PayrollCalcDraft draft, List<PayrollCalcDraftLine> lines, CalcSettings settings) {}

    /**
     * Bước đầu của xác nhận (dùng chung quy tắc khóa và điều kiện với các thao tác sửa): khóa kỳ rồi bản nháp, bản đã đóng →
     * {@code calcdraftclosed}, kỳ đã duyệt → {@code periodapproved}. Rồi tính lại mọi dòng từ hồ sơ như "Tính lại từ hồ sơ" (giữ
     * nhập tay, sửa tay, dòng đã bỏ) với thông số, mức luật và bảng công hiện hành: bảng công không còn chốt →
     * {@code payrollattendancenotclosed}; có số, lỗi hay cảnh báo khác số đang lưu → lưu số mới, bỏ "Đã xem và xác nhận", ném
     * {@link CalcStaleException} (không hoàn tác số mới). Cuối cùng còn lỗi / chưa xác nhận cảnh báo → {@code calcnotready}. Cờ
     * xác nhận cảnh báo của máy khách không được dùng: Kế toán phải gọi {@link #acknowledge} trên số mới nhất.
     */
    @Transactional(noRollbackFor = CalcStaleException.class)
    public Confirmable lockForConfirm(Long draftId) {
        PayrollCalcDraft draft = openForUpdate(draftId);
        Map<Long, String> before = signatures(draftId);
        refreshFromProfiles(draft);
        if (!before.equals(signatures(draftId))) {
            draft.setWarningsAcknowledged(false);
            lineRepository.flush();
            throw new CalcStaleException();
        }
        if (!toDto(draft).canConfirm()) {
            throw new EmployeeRuleException("Bản tính còn lỗi hoặc chưa xác nhận cảnh báo.", "calcnotready");
        }
        List<PayrollCalcDraftLine> lines = lineRepository
            .findByDraftIdOrderByCodeAscIdAsc(draftId)
            .stream()
            .filter(l -> !l.isExcluded())
            .toList();
        return new Confirmable(draft, lines, settingsService.forPeriod(YearMonth.parse(draft.getPeriod().getPeriod())));
    }

    /** Dấu của các dòng đang giữ: số (so theo giá trị), lỗi, cảnh báo, trạng thái "không còn thuộc kỳ". */
    private Map<Long, String> signatures(Long draftId) {
        Map<Long, String> out = new HashMap<>();
        for (PayrollCalcDraftLine l : lineRepository.findByDraftIdOrderByCodeAscIdAsc(draftId)) {
            if (l.isExcluded()) {
                continue;
            }
            Map<String, String> amounts = new TreeMap<>();
            readAmounts(l.getAmounts()).forEach((f, v) -> amounts.put(f.name(), v.stripTrailingZeros().toPlainString()));
            out.put(l.getId(), amounts + "|" + readStrings(l.getErrors()) + "|" + readStrings(l.getWarnings()) + "|" + l.isStale());
        }
        return out;
    }

    /** Ngày bắt đầu HĐLĐ đầu tiên của người có dòng (ghi vào file Excel của phiên bản). */
    public Map<Long, LocalDate> contractStarts(List<PayrollCalcDraftLine> lines) {
        return templateService.firstLabourContract(
            new HashSet<>(
                lines
                    .stream()
                    .map(l -> l.getEmployee().getId())
                    .toList()
            )
        );
    }

    public Map<PayrollField, BigDecimal> amountsOf(PayrollCalcDraftLine line) {
        return readAmounts(line.getAmounts());
    }

    public List<String> warningsOf(PayrollCalcDraftLine line) {
        return readStrings(line.getWarnings());
    }

    @Transactional(readOnly = true)
    public CalcDraftDTO get(Long id) {
        return toDto(draftRepository.findById(id).orElseThrow(PayrollCalcDraftService::notFound));
    }

    /** Cập nhật ô nhập tay / sửa tay của một dòng rồi tính lại dòng đó. */
    public CalcLineDTO updateLine(Long draftId, Long lineId, CalcEditVM edit) {
        PayrollCalcDraft draft = openForUpdate(draftId);
        PayrollCalcDraftLine line = lineOf(draft, lineId);
        Map<PayrollField, BigDecimal> manual = readAmounts(line.getManual());
        Map<PayrollField, BigDecimal> overrides = readAmounts(line.getOverrides());
        if (edit != null && edit.manual() != null) {
            edit.manual().forEach((name, v) -> {
                PayrollField f = field(name);
                if (!MANUAL_FIELDS.contains(f)) {
                    throw invalidField("Ô \"" + f.label() + "\" không phải ô nhập tay.");
                }
                checkValue(f, v);
                put(manual, f, v);
            });
        }
        if (edit != null && edit.overrides() != null) {
            edit.overrides().forEach((name, v) -> {
                PayrollField f = field(name);
                if (TOTAL_FIELDS.contains(f) || MANUAL_FIELDS.contains(f)) {
                    throw invalidField("Ô \"" + f.label() + "\" không sửa được theo kiểu ghi đè số tự tính.");
                }
                checkValue(f, v);
                put(overrides, f, v);
            });
        }
        line.setManual(json(manual));
        line.setOverrides(json(overrides));
        compute(line, context(YearMonth.parse(draft.getPeriod().getPeriod())));
        draft.setWarningsAcknowledged(false);
        return toDto(line);
    }

    public void deleteLine(Long draftId, Long lineId) {
        PayrollCalcDraft draft = openForUpdate(draftId);
        // Bỏ dòng = đánh dấu loại: tính lại từ hồ sơ không đưa người này trở lại.
        lineOf(draft, lineId).setExcluded(true);
    }

    /**
     * Tính lại từ hồ sơ: đọc lại đầu vào của kỳ, giữ ô nhập tay và sửa tay, thêm người mới, đánh dấu người không còn thuộc kỳ (lỗi
     * "Không còn thuộc kỳ", bỏ dòng để đi tiếp).
     */
    public CalcDraftDTO recalculate(Long draftId) {
        PayrollCalcDraft draft = openForUpdate(draftId);
        refreshFromProfiles(draft);
        draft.setWarningsAcknowledged(false);
        return toDto(draft);
    }

    /** Thân của "Tính lại từ hồ sơ" (cũng chạy khi xác nhận): không đổi cờ xác nhận cảnh báo. */
    private void refreshFromProfiles(PayrollCalcDraft draft) {
        YearMonth month = YearMonth.parse(draft.getPeriod().getPeriod());
        List<CollectedRow> rows = collector.collect(month);
        Context ctx = context(month);
        preloadContractStarts(ctx, rows);
        Map<Long, Employee> people = employeesOf(rows);
        Map<Long, PayrollCalcDraftLine> existing = new HashMap<>();
        lineRepository.findByDraftIdOrderByCodeAscIdAsc(draft.getId()).forEach(l -> existing.put(l.getEmployee().getId(), l));
        Set<Long> seen = new HashSet<>();
        for (CollectedRow row : rows) {
            seen.add(row.employeeId());
            PayrollCalcDraftLine line = existing.get(row.employeeId());
            if (line != null && line.isExcluded()) {
                continue;
            }
            if (line == null) {
                line = new PayrollCalcDraftLine();
                line.setDraft(draft);
                line.setManual("{}");
                line.setOverrides("{}");
            }
            line.setStale(false);
            fill(line, row, people.get(row.employeeId()), ctx);
            lineRepository.save(line);
        }
        existing.forEach((employeeId, line) -> {
            if (!line.isExcluded() && !seen.contains(employeeId)) {
                markStale(line, ctx);
            }
        });
    }

    private void markStale(PayrollCalcDraftLine line, Context ctx) {
        line.setStale(true);
        line.setErrors(json(List.of(STALE_ERROR)));
        compute(line, ctx);
    }

    /** Khôi phục dòng đã bỏ: đọc lại đầu vào của người đó từ hồ sơ (giữ nhập tay, sửa tay) rồi tính lại. */
    public CalcDraftDTO restoreLine(Long draftId, Long lineId) {
        PayrollCalcDraft draft = openForUpdate(draftId);
        PayrollCalcDraftLine line = lineRepository.findById(lineId).orElseThrow(PayrollCalcDraftService::notFound);
        if (!line.isExcluded() || !line.getDraft().getId().equals(draft.getId())) {
            throw notFound();
        }
        line.setExcluded(false);
        YearMonth month = YearMonth.parse(draft.getPeriod().getPeriod());
        Context ctx = context(month);
        Optional<CollectedRow> row = collector
            .collect(month)
            .stream()
            .filter(r -> r.employeeId().equals(line.getEmployee().getId()))
            .findFirst();
        if (row.isPresent()) {
            line.setStale(false);
            fill(line, row.orElseThrow(), line.getEmployee(), ctx);
        } else {
            markStale(line, ctx);
        }
        draft.setWarningsAcknowledged(false);
        return toDto(draft);
    }

    /** Hủy bản nháp (trạng thái {@code DA_HUY}): "Tính lương" sau đó tạo bản mới. */
    public CalcDraftDTO cancel(Long draftId) {
        PayrollCalcDraft draft = openForUpdate(draftId);
        draft.setStatus(PayrollCalcStatus.DA_HUY);
        draftRepository.flush();
        return toDto(draft);
    }

    /** Kế toán đã xem và xác nhận các cảnh báo hiện có. */
    public CalcDraftDTO acknowledge(Long draftId) {
        PayrollCalcDraft draft = openForUpdate(draftId);
        draft.setWarningsAcknowledged(true);
        return toDto(draft);
    }

    // ---- tính và lưu dòng ----

    /** Ghi đầu vào / cảnh báo / lỗi từ hồ sơ vào dòng rồi tính lại. */
    private void fill(PayrollCalcDraftLine line, CollectedRow row, Employee e, Context ctx) {
        line.setEmployee(e);
        line.setCode(row.code());
        line.setFullName(row.fullName());
        line.setPosition(row.position());
        line.setInput(json(withEdits(row.input(), Map.of(), Map.of())));
        line.setErrors(json(row.errors()));
        line.setCollectWarnings(json(row.warnings()));
        compute(line, ctx);
    }

    /** Tính lại một dòng từ đầu vào + nhập tay + sửa tay. Dòng còn lỗi không tính (không có số). */
    private void compute(PayrollCalcDraftLine line, Context ctx) {
        List<String> collected = readStrings(line.getCollectWarnings());
        if (!readStrings(line.getErrors()).isEmpty()) {
            line.setAmounts("{}");
            line.setWarnings(json(collected));
            return;
        }
        CalcInput base = objectMapper.readValue(line.getInput(), CalcInput.class);
        CalcInput in = withEdits(base, readAmounts(line.getManual()), readAmounts(line.getOverrides()));
        CalcResult result = PayrollCalculator.calculate(in, ctx.settings(), ctx.rules().rules(), ctx.rules().estimated());
        List<String> warnings = new ArrayList<>(collected);
        warnings.addAll(result.warnings());
        warnings.addAll(validate(line, result, ctx));
        Employee e = line.getEmployee();
        if (
            (in.kind() == PersonKind.THUC_TAP || in.kind() == PersonKind.DICH_VU) &&
            (e.getTaxCode() == null || e.getTaxCode().isBlank()) &&
            result.amounts().getOrDefault(PayrollField.TONG_LUONG_GOP, BigDecimal.ZERO).compareTo(ctx.settings().withholdingThreshold()) >=
                0
        ) {
            warnings.add(NO_TAX_CODE);
        }
        line.setAmounts(json(result.amounts()));
        line.setWarnings(json(warnings));
    }

    /**
     * Đối chiếu kết quả như một dòng nhập file ({@link PayrollValidator}, chế độ bản tính: tỷ lệ BH theo thông số, mức đóng theo hồ
     * sơ, trợ cấp thôi việc không chịu thuế, quy tắc 14 ngày); mọi phát hiện là cảnh báo. Cảnh báo cấp file (vd chưa khai báo mức
     * luật) không gắn vào từng dòng mà hiện một lần ở cấp bản tính ({@link #notices}).
     */
    private List<String> validate(PayrollCalcDraftLine line, CalcResult result, Context ctx) {
        Employee e = line.getEmployee();
        LocalDate contractStart = ctx
            .contractStarts()
            .computeIfAbsent(e.getId(), id -> Optional.ofNullable(templateService.firstLabourContract(Set.of(id)).get(id)))
            .orElse(null);
        PayrollLine pl = new PayrollLine(1, line.getCode(), line.getPosition(), line.getFullName(), contractStart, result.amounts());
        ParsedPayroll parsed = new ParsedPayroll(Optional.empty(), Map.of(), List.of(pl), List.of());
        return ctx
            .validator()
            .validate(parsed, ctx.month(), directory(line.getFullName(), e))
            .stream()
            .filter(i -> i.excelRow() > 0)
            .map(Issue::message)
            .toList();
    }

    private static EmployeeDirectory directory(String fullName, Employee e) {
        return new EmployeeDirectory() {
            @Override
            public Optional<String> findNameByCode(String code) {
                return Optional.ofNullable(fullName);
            }

            @Override
            public Optional<String> insuranceExemption(String code) {
                return e == null ? Optional.empty() : Optional.ofNullable(e.getInsuranceExemption()).map(InsuranceExemption::label);
            }
        };
    }

    /** Cảnh báo chung của bản tính: cảnh báo cấp file của bộ đối chiếu cho kỳ (vd chưa khai báo mức luật định cho kỳ). */
    private List<String> notices(YearMonth month) {
        ParsedPayroll empty = new ParsedPayroll(Optional.empty(), Map.of(), List.of(), List.of());
        return new PayrollValidator(ruleBookService.current())
            .validate(empty, month, directory(null, null))
            .stream()
            .map(Issue::message)
            .toList();
    }

    private void preloadContractStarts(Context ctx, List<CollectedRow> rows) {
        Set<Long> ids = new HashSet<>(rows.stream().map(CollectedRow::employeeId).toList());
        Map<Long, LocalDate> found = templateService.firstLabourContract(ids);
        ids.forEach(id -> ctx.contractStarts().put(id, Optional.ofNullable(found.get(id))));
    }

    private Map<Long, Employee> employeesOf(List<CollectedRow> rows) {
        Map<Long, Employee> m = new HashMap<>();
        employeeRepository.findAllById(rows.stream().map(CollectedRow::employeeId).toList()).forEach(e -> m.put(e.getId(), e));
        return m;
    }

    private static PayrollField field(String name) {
        try {
            return PayrollField.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalidField("Không có ô \"" + name + "\" trong bảng lương.");
        }
    }

    /** Tiền: số đồng nguyên, không âm (trừ thừa/thiếu và quyết toán thuế); ngày công 0–31; số người phụ thuộc nguyên, không âm. */
    private static void checkValue(PayrollField f, BigDecimal v) {
        if (v == null) {
            return;
        }
        if (COUNT_FIELDS.contains(f)) {
            boolean days = f != PayrollField.SO_NGUOI_PHU_THUOC;
            if (v.signum() < 0 || (days && v.compareTo(MAX_DAYS) > 0)) {
                throw invalidField("Ô \"" + f.label() + "\" phải từ 0" + (days ? " đến 31." : "."));
            }
            if (!days && v.stripTrailingZeros().scale() > 0) {
                throw invalidField("Ô \"" + f.label() + "\" phải là số nguyên.");
            }
            return;
        }
        if (v.signum() < 0 && !MAY_BE_NEGATIVE.contains(f)) {
            throw invalidField("Ô \"" + f.label() + "\" không được âm.");
        }
        if (v.stripTrailingZeros().scale() > 0) {
            throw invalidField("Ô \"" + f.label() + "\" phải là số đồng nguyên.");
        }
    }

    private static CalcInput withEdits(CalcInput b, Map<PayrollField, BigDecimal> manual, Map<PayrollField, BigDecimal> overrides) {
        return new CalcInput(
            b.kind(),
            b.insured(),
            b.salary(),
            b.insuranceSalaryOverride(),
            b.days(),
            b.standardDays(),
            b.allowances(),
            b.overtime(),
            b.unusedLeaveDays(),
            b.dependants(),
            b.pitCommitment08(),
            manual,
            overrides
        );
    }

    private Context context(YearMonth month) {
        RuleBook book = ruleBookService.current();
        PayrollRules.RulesLookup rules = book
            .forPeriodOrLatest(month)
            .orElseThrow(() ->
                new EmployeeRuleException("Chưa khai báo mức luật định cho kỳ " + month.format(MM_YYYY) + ".", "payrollrulesmissing")
            );
        CalcSettings settings = settingsService.forPeriod(month);
        PayrollValidator.Options options = new PayrollValidator.Options(
            settings.bhxhEmployee(),
            settings.bhytEmployee(),
            settings.bhtnEmployee(),
            false,
            true,
            settings.insuranceSkipOnLongUnpaid() ? CalcSettings.LONG_UNPAID_DAYS : null
        );
        return new Context(month, settings, rules, new PayrollValidator(book, options), new HashMap<>());
    }

    // ---- khóa và kiểm tra trạng thái ----

    private PayrollPeriod lock(String period) {
        periodRepository.insertIfMissing(period);
        return periodRepository.findForUpdate(period).orElseThrow();
    }

    /** Khóa kỳ rồi bản nháp; bản đã xác nhận / hủy → {@code calcdraftclosed}; kỳ đã duyệt → {@code periodapproved}. */
    private PayrollCalcDraft openForUpdate(Long id) {
        // Chỉ lấy khóa kỳ (giá trị đơn) trước khi khóa: bản nháp và kỳ được nạp sau khóa nên đọc trạng thái mới nhất.
        String key = draftRepository.findPeriodKey(id).orElseThrow(PayrollCalcDraftService::notFound);
        PayrollPeriod period = lock(key);
        PayrollCalcDraft draft = draftRepository.findForUpdate(id).orElseThrow(PayrollCalcDraftService::notFound);
        if (draft.getStatus() != PayrollCalcStatus.DANG_LAM) {
            throw new EmployeeRuleException("Bản tính này đã xác nhận hoặc đã hủy.", "calcdraftclosed");
        }
        if (period.getStatus() == PayrollPeriodStatus.DA_DUYET) {
            throw approved(YearMonth.parse(period.getPeriod()));
        }
        return draft;
    }

    private PayrollCalcDraftLine lineOf(PayrollCalcDraft draft, Long lineId) {
        PayrollCalcDraftLine line = lineRepository.findById(lineId).orElseThrow(PayrollCalcDraftService::notFound);
        if (line.isExcluded() || !line.getDraft().getId().equals(draft.getId())) {
            throw notFound();
        }
        return line;
    }

    private static void put(Map<PayrollField, BigDecimal> map, PayrollField f, BigDecimal v) {
        if (v == null) {
            map.remove(f);
        } else {
            map.put(f, v);
        }
    }

    private static EmployeeRuleException approved(YearMonth month) {
        return new EmployeeRuleException(
            "Kỳ " + month.format(MM_YYYY) + " đã được duyệt; cần Quản trị mở lại trước khi tính lương",
            "periodapproved"
        );
    }

    private static EmployeeRuleException invalidField(String message) {
        return new EmployeeRuleException(message, "calcfieldinvalid");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản tính");
    }

    // ---- JSON và DTO ----

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private Map<PayrollField, BigDecimal> readAmounts(String text) {
        Map<PayrollField, BigDecimal> m = new EnumMap<>(PayrollField.class);
        if (text != null && !text.isBlank()) {
            m.putAll(objectMapper.readValue(text, AMOUNTS));
        }
        return m;
    }

    private List<String> readStrings(String text) {
        return text == null || text.isBlank() ? List.of() : objectMapper.readValue(text, STRINGS);
    }

    private CalcLineDTO toDto(PayrollCalcDraftLine l) {
        return new CalcLineDTO(
            l.getId(),
            l.getEmployee().getId(),
            l.getCode(),
            l.getFullName(),
            l.getPosition(),
            readAmounts(l.getAmounts()),
            readAmounts(l.getManual()),
            readAmounts(l.getOverrides()),
            readStrings(l.getErrors()),
            readStrings(l.getWarnings()),
            l.isStale()
        );
    }

    private CalcDraftDTO toDto(PayrollCalcDraft d) {
        List<PayrollCalcDraftLine> all = lineRepository.findByDraftIdOrderByCodeAscIdAsc(d.getId());
        List<CalcLineDTO> lines = all
            .stream()
            .filter(l -> !l.isExcluded())
            .map(this::toDto)
            .toList();
        List<CalcExcludedDTO> excluded = all
            .stream()
            .filter(PayrollCalcDraftLine::isExcluded)
            .map(l -> new CalcExcludedDTO(l.getId(), l.getEmployee().getId(), l.getCode(), l.getFullName()))
            .toList();
        List<String> notices = notices(YearMonth.parse(d.getPeriod().getPeriod()));
        Map<PayrollField, BigDecimal> totals = new EnumMap<>(PayrollField.class);
        for (PayrollField f : PayrollField.values()) {
            if (!COUNT_FIELDS.contains(f)) {
                totals.put(f, BigDecimal.ZERO);
            }
        }
        for (CalcLineDTO l : lines) {
            l.amounts().forEach((f, v) -> {
                if (!COUNT_FIELDS.contains(f)) {
                    totals.merge(f, v, BigDecimal::add);
                }
            });
        }
        boolean hasError = lines.stream().anyMatch(l -> !l.errors().isEmpty());
        boolean hasWarning = !notices.isEmpty() || lines.stream().anyMatch(l -> !l.warnings().isEmpty());
        boolean canConfirm =
            d.getStatus() == PayrollCalcStatus.DANG_LAM &&
            d.getPeriod().getStatus() != PayrollPeriodStatus.DA_DUYET &&
            !lines.isEmpty() &&
            !hasError &&
            (!hasWarning || d.isWarningsAcknowledged());
        List<CalcColumnDTO> columns = new ArrayList<>();
        for (PayrollField f : PayrollField.values()) {
            columns.add(new CalcColumnDTO(f.name(), f.header(), f.subHeader(), MANUAL_FIELDS.contains(f)));
        }
        return new CalcDraftDTO(
            d.getId(),
            d.getPeriod().getPeriod(),
            d.getStatus(),
            lines,
            totals,
            canConfirm,
            d.isWarningsAcknowledged(),
            columns,
            excluded,
            notices
        );
    }
}
