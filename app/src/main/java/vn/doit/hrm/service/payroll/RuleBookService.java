package vn.doit.hrm.service.payroll;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hr.payroll.PayrollRules;
import vn.doit.hr.payroll.RuleBook;
import vn.doit.hrm.domain.PayrollRuleBracket;
import vn.doit.hrm.domain.PayrollRuleMilestone;
import vn.doit.hrm.domain.PayrollRuleSetting;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayrollRuleMilestoneRepository;
import vn.doit.hrm.repository.PayrollRuleSettingRepository;
import vn.doit.hrm.repository.PayrollVersionRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.dto.RuleMilestoneDTO;

/**
 * Mức luật định theo ngày hiệu lực, lưu trong cơ sở dữ liệu. {@link #current()} giữ bản trong bộ nhớ, xóa sau khi thêm mốc
 * hoặc đổi "đã biết luật đến" (sau khi giao dịch lưu xong) để lần dùng kế tiếp đọc lại.
 */
@Service
@Transactional
public class RuleBookService {

    private static final String KEY = "rulesinvalid";

    private final PayrollRuleMilestoneRepository milestoneRepository;
    private final PayrollRuleSettingRepository settingRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollVersionRepository versionRepository;
    private final Clock clock;

    private volatile RuleBook cached;

    public RuleBookService(
        PayrollRuleMilestoneRepository milestoneRepository,
        PayrollRuleSettingRepository settingRepository,
        PayrollPeriodRepository periodRepository,
        PayrollVersionRepository versionRepository,
        Clock clock
    ) {
        this.milestoneRepository = milestoneRepository;
        this.settingRepository = settingRepository;
        this.periodRepository = periodRepository;
        this.versionRepository = versionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RuleBook current() {
        RuleBook book = cached;
        if (book == null) {
            List<RuleBook.Milestone> milestones = milestoneRepository
                .findAllByOrderByCreatedAtAscIdAsc()
                .stream()
                .map(m -> new RuleBook.Milestone(YearMonth.parse(m.getEffectiveFrom()), toRules(m)))
                .toList();
            book = new RuleBook(milestones, YearMonth.parse(setting().getKnownThrough()));
            cached = book;
        }
        return book;
    }

    /** Căn cứ văn bản của mốc có hiệu lực từ tháng {@code effectiveFrom} (mốc thêm sau cùng nếu có nhiều). */
    @Transactional(readOnly = true)
    public Optional<String> legalBasis(YearMonth effectiveFrom) {
        String from = effectiveFrom.toString();
        return milestoneRepository
            .findAllByOrderByCreatedAtAscIdAsc()
            .stream()
            .filter(m -> from.equals(m.getEffectiveFrom()))
            .reduce((a, b) -> b)
            .map(m -> m.getLegalBasis())
            .filter(b -> !b.isBlank());
    }

    /** Mốc mới nhất trước (theo tháng hiệu lực, rồi theo thứ tự thêm). */
    @Transactional(readOnly = true)
    public List<RuleMilestoneDTO> list() {
        String approved = periodRepository.latestApproved();
        Set<String> usedMonths = new HashSet<>(versionRepository.usedMilestoneMonths());
        return milestoneRepository
            .findAllByOrderByCreatedAtAscIdAsc()
            .stream()
            .sorted(
                Comparator.comparing(PayrollRuleMilestone::getEffectiveFrom)
                    .thenComparing(PayrollRuleMilestone::getCreatedAt)
                    .thenComparing(PayrollRuleMilestone::getId)
                    .reversed()
            )
            .map(m -> toDto(m, isUsed(m, approved, usedMonths)))
            .toList();
    }

    @Transactional(readOnly = true)
    public YearMonth knownThrough() {
        return YearMonth.parse(setting().getKnownThrough());
    }

    public RuleMilestoneDTO add(RuleMilestoneDTO dto) {
        YearMonth from = parseMonth(dto.effectiveFrom());
        String approved = periodRepository.latestApproved();
        checkAfterApproved(from, approved);
        checkValues(dto);
        PayrollRuleMilestone m = new PayrollRuleMilestone();
        m.setCreatedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        m.setCreatedAt(Instant.now(clock));
        fill(m, from, dto);
        PayrollRuleMilestone saved = milestoneRepository.saveAndFlush(m);
        refreshAfterCommit();
        return toDto(saved, false);
    }

    /**
     * Sửa mốc chưa dùng: thay giá trị và toàn bộ bậc thuế trong một giao dịch. Mốc đã dùng (hiệu lực không sau kỳ lương đã duyệt
     * gần nhất) → 400 {@code rulemilestoneused}; tháng hiệu lực mới vẫn phải sau kỳ đã duyệt như khi thêm.
     */
    public RuleMilestoneDTO update(Long id, RuleMilestoneDTO dto) {
        PayrollRuleMilestone m = find(id);
        String approved = periodRepository.latestApproved();
        checkNotUsed(m, approved);
        YearMonth from = parseMonth(dto.effectiveFrom());
        checkAfterApproved(from, approved);
        checkValues(dto);
        m.getBrackets().clear();
        milestoneRepository.saveAndFlush(m);
        fill(m, from, dto);
        PayrollRuleMilestone saved = milestoneRepository.saveAndFlush(m);
        refreshAfterCommit();
        return toDto(saved, false);
    }

    /** Xóa mốc chưa dùng cùng các bậc thuế; không xóa mốc duy nhất còn lại. */
    public void delete(Long id) {
        PayrollRuleMilestone m = find(id);
        checkNotUsed(m, periodRepository.latestApproved());
        if (milestoneRepository.count() <= 1) {
            throw new EmployeeRuleException("Không xóa được mốc luật duy nhất còn lại", "rulemilestonelast");
        }
        milestoneRepository.delete(m);
        milestoneRepository.flush();
        refreshAfterCommit();
    }

    private PayrollRuleMilestone find(Long id) {
        return milestoneRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mốc luật"));
    }

    /**
     * Mốc đã dùng khi hiệu lực không sau kỳ lương đã duyệt gần nhất, hoặc khi có phiên bản bảng lương (kỳ nào, trạng thái nào,
     * kể cả kỳ đã mở lại) lưu bản chụp luật trỏ tới tháng hiệu lực của mốc.
     */
    private static boolean isUsed(PayrollRuleMilestone m, String approved, Set<String> usedMonths) {
        boolean byApproved = approved != null && YearMonth.parse(m.getEffectiveFrom()).compareTo(YearMonth.parse(approved)) <= 0;
        return byApproved || usedMonths.contains(m.getEffectiveFrom());
    }

    private void checkNotUsed(PayrollRuleMilestone m, String approved) {
        if (isUsed(m, approved, new HashSet<>(versionRepository.usedMilestoneMonths()))) {
            throw new EmployeeRuleException(
                "Mốc có hiệu lực từ " + PayrollPeriodService.display(m.getEffectiveFrom()) + " đã được dùng cho bảng lương, không sửa hoặc xóa",
                "rulemilestoneused"
            );
        }
    }

    /** Luật đổi không tác động ngược: mốc phải có hiệu lực sau kỳ lương đã duyệt gần nhất. */
    private static void checkAfterApproved(YearMonth from, String approved) {
        if (approved != null && !from.isAfter(YearMonth.parse(approved))) {
            throw new EmployeeRuleException(
                "Mốc mới phải có hiệu lực sau kỳ lương đã duyệt gần nhất (" + PayrollPeriodService.display(approved) + ")",
                "rulesbeforeapproved"
            );
        }
    }

    private static void checkValues(RuleMilestoneDTO dto) {
        positive(dto.personalDeduction(), "Giảm trừ bản thân");
        positive(dto.dependantDeduction(), "Giảm trừ người phụ thuộc");
        positive(dto.socialInsuranceCap(), "Trần đóng BHXH, BHYT");
        positive(dto.unemploymentInsuranceCap(), "Trần đóng BHTN");
        checkBrackets(dto.brackets());
    }

    private static void fill(PayrollRuleMilestone m, YearMonth from, RuleMilestoneDTO dto) {
        m.setEffectiveFrom(from.toString());
        m.setPersonalDeduction(dto.personalDeduction());
        m.setDependantDeduction(dto.dependantDeduction());
        m.setSocialInsuranceCap(dto.socialInsuranceCap());
        m.setUnemploymentInsuranceCap(dto.unemploymentInsuranceCap());
        m.setLegalBasis(dto.legalBasis() == null || dto.legalBasis().isBlank() ? null : dto.legalBasis().trim());
        dto.brackets().forEach(b -> m.addBracket(new PayrollRuleBracket(b.upTo(), b.rate())));
    }

    public void setKnownThrough(YearMonth knownThrough) {
        setting().setKnownThrough(knownThrough.toString());
        refreshAfterCommit();
    }

    /** Bỏ bản trong bộ nhớ ngay (dùng trong test và sau khi dữ liệu đổi). */
    public void refresh() {
        cached = null;
    }

    /** Đọc tháng yyyy-MM; sai dạng → 400 {@code rulesinvalid}. */
    public static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month == null ? "" : month.trim());
        } catch (DateTimeParseException e) {
            throw new EmployeeRuleException("Tháng hiệu lực phải có dạng yyyy-MM", KEY);
        }
    }

    private void refreshAfterCommit() {
        refresh();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        refresh();
                    }
                }
            );
        }
    }

    private PayrollRuleSetting setting() {
        return settingRepository.findById(1L).orElseThrow(() -> new IllegalStateException("Thiếu dòng payroll_rule_setting id = 1"));
    }

    private static void positive(BigDecimal v, String name) {
        if (v == null || v.signum() <= 0) {
            throw new EmployeeRuleException(name + " phải lớn hơn 0", KEY);
        }
    }

    private static void checkBrackets(List<RuleMilestoneDTO.BracketDTO> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            throw new EmployeeRuleException("Cần ít nhất một bậc thuế", KEY);
        }
        BigDecimal lower = BigDecimal.ZERO;
        for (int i = 0; i < brackets.size(); i++) {
            RuleMilestoneDTO.BracketDTO b = brackets.get(i);
            boolean last = i == brackets.size() - 1;
            if (b.rate() == null || b.rate().signum() <= 0 || b.rate().compareTo(BigDecimal.ONE) > 0) {
                throw new EmployeeRuleException("Thuế suất bậc " + (i + 1) + " phải lớn hơn 0% và không quá 100%", KEY);
            }
            if (last) {
                if (b.upTo() != null) {
                    throw new EmployeeRuleException("Bậc thuế cuối cùng phải không giới hạn (để trống mức trên)", KEY);
                }
            } else {
                if (b.upTo() == null || b.upTo().compareTo(lower) <= 0) {
                    throw new EmployeeRuleException("Mức trên của các bậc thuế phải tăng dần", KEY);
                }
                lower = b.upTo();
            }
        }
    }

    private static PayrollRules toRules(PayrollRuleMilestone m) {
        return new PayrollRules(
            m.getPersonalDeduction(),
            m.getDependantDeduction(),
            m.getSocialInsuranceCap(),
            m.getUnemploymentInsuranceCap(),
            m.getBrackets().stream().map(b -> new PayrollRules.Bracket(b.getUpTo(), b.getRate())).toList()
        );
    }

    private static RuleMilestoneDTO toDto(PayrollRuleMilestone m, boolean used) {
        return new RuleMilestoneDTO(
            m.getId(),
            m.getEffectiveFrom(),
            m.getPersonalDeduction(),
            m.getDependantDeduction(),
            m.getSocialInsuranceCap(),
            m.getUnemploymentInsuranceCap(),
            m.getBrackets().stream().map(b -> new RuleMilestoneDTO.BracketDTO(b.getUpTo(), b.getRate())).toList(),
            m.getLegalBasis(),
            m.getCreatedBy(),
            m.getCreatedAt(),
            used
        );
    }
}
