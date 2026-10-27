package vn.doit.hrm.service.payroll.calc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.payroll.EmployeeDirectory;
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
import vn.doit.hrm.service.payroll.RuleBookService;
import vn.doit.hrm.service.payroll.calc.PayrollInputCollector.CollectedRow;
import vn.doit.hrm.service.payroll.dto.CalcColumnDTO;
import vn.doit.hrm.service.payroll.dto.CalcDraftDTO;
import vn.doit.hrm.service.payroll.dto.CalcEditVM;
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

    public PayrollCalcDraftService(
        PayrollPeriodRepository periodRepository,
        PayrollCalcDraftRepository draftRepository,
        PayrollCalcDraftLineRepository lineRepository,
        EmployeeRepository employeeRepository,
        PayrollInputCollector collector,
        CalcSettingsService settingsService,
        RuleBookService ruleBookService,
        ObjectMapper objectMapper,
        Clock clock
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
    }

    /** Mức luật, thông số và bộ đối chiếu dùng để tính các dòng của một kỳ. */
    private record Context(YearMonth month, CalcSettings settings, PayrollRules.RulesLookup rules, PayrollValidator validator) {}

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
        YearMonth month = YearMonth.parse(draft.getPeriod().getPeriod());
        List<CollectedRow> rows = collector.collect(month);
        Context ctx = context(month);
        Map<Long, Employee> people = employeesOf(rows);
        Map<Long, PayrollCalcDraftLine> existing = new HashMap<>();
        lineRepository.findByDraftIdOrderByCodeAscIdAsc(draftId).forEach(l -> existing.put(l.getEmployee().getId(), l));
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
                line.setStale(true);
                line.setErrors(json(List.of(STALE_ERROR)));
                compute(line, ctx);
            }
        });
        draft.setWarningsAcknowledged(false);
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

    /** Đối chiếu kết quả như một dòng nhập file ({@link PayrollValidator}); mọi phát hiện là cảnh báo. */
    private List<String> validate(PayrollCalcDraftLine line, CalcResult result, Context ctx) {
        PayrollLine pl = new PayrollLine(1, line.getCode(), line.getPosition(), line.getFullName(), null, result.amounts());
        ParsedPayroll parsed = new ParsedPayroll(Optional.empty(), Map.of(), List.of(pl), List.of());
        Employee e = line.getEmployee();
        EmployeeDirectory directory = new EmployeeDirectory() {
            @Override
            public Optional<String> findNameByCode(String code) {
                return Optional.ofNullable(line.getFullName());
            }

            @Override
            public Optional<String> insuranceExemption(String code) {
                return Optional.ofNullable(e.getInsuranceExemption()).map(InsuranceExemption::label);
            }
        };
        return ctx
            .validator()
            .validate(parsed, ctx.month(), directory)
            .stream()
            .map(i -> i.message())
            .toList();
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

    /** Tiền: số đồng nguyên, không âm (trừ thừa/thiếu và quyết toán thuế); ngày công 0–31; số người phụ thuộc không âm. */
    private static void checkValue(PayrollField f, BigDecimal v) {
        if (v == null) {
            return;
        }
        if (COUNT_FIELDS.contains(f)) {
            boolean days = f != PayrollField.SO_NGUOI_PHU_THUOC;
            if (v.signum() < 0 || (days && v.compareTo(MAX_DAYS) > 0)) {
                throw invalidField("Ô \"" + f.label() + "\" phải từ 0" + (days ? " đến 31." : "."));
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
        return new Context(month, settingsService.forPeriod(month), rules, new PayrollValidator(book));
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
        List<CalcLineDTO> lines = lineRepository
            .findByDraftIdOrderByCodeAscIdAsc(d.getId())
            .stream()
            .filter(l -> !l.isExcluded())
            .map(this::toDto)
            .toList();
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
        boolean hasWarning = lines.stream().anyMatch(l -> !l.warnings().isEmpty());
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
            columns
        );
    }
}
