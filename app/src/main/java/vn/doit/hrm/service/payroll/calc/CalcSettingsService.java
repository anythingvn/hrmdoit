package vn.doit.hrm.service.payroll.calc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.payroll.calc.CalcSettings;
import vn.doit.hrm.domain.PayrollCalcSetting;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.repository.PayrollCalcSettingRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.dto.CalcSettingDTO;

/**
 * Thông số tính lương theo tháng hiệu lực: kỳ dùng bộ có tháng hiệu lực lớn nhất không sau kỳ đó; chưa có bộ nào thì dùng mặc
 * định ({@link CalcSettings#defaults()}). Bộ đã có kỳ đã duyệt trong khoảng hiệu lực (từ tháng hiệu lực đến trước bộ kế tiếp) bị
 * khóa; thêm hoặc dời bộ cũng không được phủ lên kỳ đã duyệt (luật đổi không tác động ngược).
 */
@Service
@Transactional
public class CalcSettingsService {

    private static final String INVALID = "calcsettinginvalid";
    private static final String INVALID_TEXT = "Thông số tính lương không hợp lệ.";
    private static final String LOCKED = "calcsettinglocked";
    private static final String LOCKED_TEXT = "Bộ thông số này đã dùng cho kỳ đã duyệt, hãy thêm bộ mới.";

    private final PayrollCalcSettingRepository repository;
    private final PayrollPeriodRepository periodRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CalcSettingsService(
        PayrollCalcSettingRepository repository,
        PayrollPeriodRepository periodRepository,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.periodRepository = periodRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Bộ thông số của kỳ {@code p}. */
    @Transactional(readOnly = true)
    public CalcSettings forPeriod(YearMonth p) {
        PayrollCalcSetting best = null;
        for (PayrollCalcSetting s : repository.findAllByOrderByEffectiveFromAsc()) {
            if (YearMonth.parse(s.getEffectiveFrom()).isAfter(p)) {
                break;
            }
            best = s;
        }
        return best == null ? CalcSettings.defaults() : toSettings(read(best));
    }

    /** Bộ mới nhất trước. */
    @Transactional(readOnly = true)
    public List<CalcSettingDTO> list() {
        List<PayrollCalcSetting> all = repository.findAllByOrderByEffectiveFromAsc();
        List<YearMonth> approved = approvedPeriods();
        List<CalcSettingDTO> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            YearMonth next = i + 1 < all.size() ? YearMonth.parse(all.get(i + 1).getEffectiveFrom()) : null;
            out.add(toDto(all.get(i), hasApproved(YearMonth.parse(all.get(i).getEffectiveFrom()), next, approved)));
        }
        return out;
    }

    public CalcSettingDTO add(CalcSettingDTO dto) {
        YearMonth from = validate(dto);
        List<PayrollCalcSetting> all = repository.findAllByOrderByEffectiveFromAsc();
        checkMonthFree(from, all, null);
        checkRangeNotApproved(from, all, null);
        PayrollCalcSetting s = new PayrollCalcSetting();
        s.setCreatedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        s.setCreatedAt(Instant.now(clock));
        fill(s, from, dto);
        return toDto(repository.saveAndFlush(s), false);
    }

    public CalcSettingDTO update(Long id, CalcSettingDTO dto) {
        PayrollCalcSetting s = find(id);
        List<PayrollCalcSetting> all = repository.findAllByOrderByEffectiveFromAsc();
        checkNotLocked(s, all);
        YearMonth from = validate(dto);
        checkMonthFree(from, all, id);
        checkRangeNotApproved(from, all, id);
        fill(s, from, dto);
        return toDto(repository.saveAndFlush(s), false);
    }

    public void delete(Long id) {
        PayrollCalcSetting s = find(id);
        checkNotLocked(s, repository.findAllByOrderByEffectiveFromAsc());
        repository.delete(s);
        repository.flush();
    }

    private PayrollCalcSetting find(Long id) {
        return repository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ thông số tính lương"));
    }

    private List<YearMonth> approvedPeriods() {
        return periodRepository.findPeriodsByStatus(PayrollPeriodStatus.DA_DUYET).stream().map(YearMonth::parse).toList();
    }

    /** Có kỳ đã duyệt trong [from, next) (next null: không giới hạn trên). */
    private static boolean hasApproved(YearMonth from, YearMonth next, List<YearMonth> approved) {
        return approved.stream().anyMatch(p -> !p.isBefore(from) && (next == null || p.isBefore(next)));
    }

    private void checkNotLocked(PayrollCalcSetting s, List<PayrollCalcSetting> all) {
        YearMonth from = YearMonth.parse(s.getEffectiveFrom());
        if (hasApproved(from, nextAfter(from, all, s.getId()), approvedPeriods())) {
            throw new EmployeeRuleException(LOCKED_TEXT, LOCKED);
        }
    }

    /** Khoảng hiệu lực của bộ đặt tại {@code from} không được phủ lên kỳ đã duyệt. */
    private void checkRangeNotApproved(YearMonth from, List<PayrollCalcSetting> all, Long ignoreId) {
        if (hasApproved(from, nextAfter(from, all, ignoreId), approvedPeriods())) {
            throw new EmployeeRuleException(LOCKED_TEXT, LOCKED);
        }
    }

    /** Tháng hiệu lực của bộ kế tiếp sau {@code from} (bỏ qua bộ {@code ignoreId}), null nếu không có. */
    private static YearMonth nextAfter(YearMonth from, List<PayrollCalcSetting> all, Long ignoreId) {
        YearMonth next = null;
        for (PayrollCalcSetting o : all) {
            if (o.getId().equals(ignoreId)) {
                continue;
            }
            YearMonth m = YearMonth.parse(o.getEffectiveFrom());
            if (m.isAfter(from) && (next == null || m.isBefore(next))) {
                next = m;
            }
        }
        return next;
    }

    private static void checkMonthFree(YearMonth from, List<PayrollCalcSetting> all, Long ignoreId) {
        boolean taken = all.stream().anyMatch(o -> !o.getId().equals(ignoreId) && o.getEffectiveFrom().equals(from.toString()));
        if (taken) {
            throw invalid();
        }
    }

    private static EmployeeRuleException invalid() {
        return new EmployeeRuleException(INVALID_TEXT, INVALID);
    }

    /** Kiểm tra giá trị; trả tháng hiệu lực. */
    private static YearMonth validate(CalcSettingDTO d) {
        if (d == null || d.effectiveFrom() == null) {
            throw invalid();
        }
        YearMonth from;
        try {
            from = YearMonth.parse(d.effectiveFrom().trim());
        } catch (DateTimeParseException e) {
            throw invalid();
        }
        for (BigDecimal rate : new BigDecimal[] {
            d.bhxhEmployee(),
            d.bhytEmployee(),
            d.bhtnEmployee(),
            d.bhxhCompany(),
            d.bhytCompany(),
            d.bhtnCompany(),
            d.unionFeeRate(),
            d.withholdingRate(),
        }) {
            if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw invalid();
            }
        }
        BigDecimal hours = d.hoursPerDay();
        if (hours == null || hours.signum() <= 0 || hours.compareTo(BigDecimal.valueOf(24)) > 0) {
            throw invalid();
        }
        if (d.withholdingThreshold() == null || d.withholdingThreshold().signum() < 0) {
            throw invalid();
        }
        if (d.actualDayKinds() == null || d.actualDayKinds().isEmpty()) {
            throw invalid();
        }
        return from;
    }

    private void fill(PayrollCalcSetting s, YearMonth from, CalcSettingDTO dto) {
        s.setEffectiveFrom(from.toString());
        s.setSettings(objectMapper.writeValueAsString(withMeta(dto, null, from.toString(), false)));
    }

    private CalcSettingDTO read(PayrollCalcSetting s) {
        return objectMapper.readValue(s.getSettings(), CalcSettingDTO.class);
    }

    private CalcSettingDTO toDto(PayrollCalcSetting s, boolean locked) {
        return withMeta(read(s), s.getId(), s.getEffectiveFrom(), locked);
    }

    private static CalcSettingDTO withMeta(CalcSettingDTO d, Long id, String effectiveFrom, boolean locked) {
        return new CalcSettingDTO(
            id,
            effectiveFrom,
            d.bhxhEmployee(),
            d.bhytEmployee(),
            d.bhtnEmployee(),
            d.bhxhCompany(),
            d.bhytCompany(),
            d.bhtnCompany(),
            d.unionFeeEnabled(),
            d.unionFeeRate(),
            d.hoursPerDay(),
            d.overtimeBaseIncludesInsuredAllowance(),
            d.overtimeAllNonTaxable(),
            d.actualDayKinds(),
            d.allowanceKeywords() == null ? Map.of() : d.allowanceKeywords(),
            d.withholdingThreshold(),
            d.withholdingRate(),
            d.payUnusedLeaveOnTermination(),
            locked
        );
    }

    private static CalcSettings toSettings(CalcSettingDTO d) {
        return new CalcSettings(
            d.bhxhEmployee(),
            d.bhytEmployee(),
            d.bhtnEmployee(),
            d.bhxhCompany(),
            d.bhytCompany(),
            d.bhtnCompany(),
            d.unionFeeEnabled(),
            d.unionFeeRate(),
            d.hoursPerDay(),
            d.overtimeBaseIncludesInsuredAllowance(),
            d.overtimeAllNonTaxable(),
            Set.copyOf(d.actualDayKinds()),
            d.allowanceKeywords(),
            d.withholdingThreshold(),
            d.withholdingRate(),
            d.payUnusedLeaveOnTermination()
        );
    }
}
