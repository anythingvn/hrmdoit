package vn.doit.hrm.service.payroll;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.doit.hr.payroll.PayrollRules;
import vn.doit.hr.payroll.RuleBook;
import vn.doit.hrm.domain.PayrollRuleBracket;
import vn.doit.hrm.domain.PayrollRuleMilestone;
import vn.doit.hrm.domain.PayrollRuleSetting;
import vn.doit.hrm.repository.PayrollRuleMilestoneRepository;
import vn.doit.hrm.repository.PayrollRuleSettingRepository;
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
    private final Clock clock;

    private volatile RuleBook cached;

    public RuleBookService(PayrollRuleMilestoneRepository milestoneRepository, PayrollRuleSettingRepository settingRepository, Clock clock) {
        this.milestoneRepository = milestoneRepository;
        this.settingRepository = settingRepository;
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

    /** Mốc mới nhất trước (theo tháng hiệu lực, rồi theo thứ tự thêm). */
    @Transactional(readOnly = true)
    public List<RuleMilestoneDTO> list() {
        return milestoneRepository
            .findAllByOrderByCreatedAtAscIdAsc()
            .stream()
            .sorted(
                Comparator.comparing(PayrollRuleMilestone::getEffectiveFrom)
                    .thenComparing(PayrollRuleMilestone::getCreatedAt)
                    .thenComparing(PayrollRuleMilestone::getId)
                    .reversed()
            )
            .map(RuleBookService::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public YearMonth knownThrough() {
        return YearMonth.parse(setting().getKnownThrough());
    }

    public RuleMilestoneDTO add(RuleMilestoneDTO dto) {
        YearMonth from = parseMonth(dto.effectiveFrom());
        positive(dto.personalDeduction(), "Giảm trừ bản thân");
        positive(dto.dependantDeduction(), "Giảm trừ người phụ thuộc");
        positive(dto.socialInsuranceCap(), "Trần đóng BHXH, BHYT");
        positive(dto.unemploymentInsuranceCap(), "Trần đóng BHTN");
        checkBrackets(dto.brackets());
        PayrollRuleMilestone m = new PayrollRuleMilestone();
        m.setEffectiveFrom(from.toString());
        m.setPersonalDeduction(dto.personalDeduction());
        m.setDependantDeduction(dto.dependantDeduction());
        m.setSocialInsuranceCap(dto.socialInsuranceCap());
        m.setUnemploymentInsuranceCap(dto.unemploymentInsuranceCap());
        m.setLegalBasis(dto.legalBasis() == null || dto.legalBasis().isBlank() ? null : dto.legalBasis().trim());
        m.setCreatedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        m.setCreatedAt(Instant.now(clock));
        dto.brackets().forEach(b -> m.addBracket(new PayrollRuleBracket(b.upTo(), b.rate())));
        PayrollRuleMilestone saved = milestoneRepository.saveAndFlush(m);
        refreshAfterCommit();
        return toDto(saved);
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

    private static RuleMilestoneDTO toDto(PayrollRuleMilestone m) {
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
            m.getCreatedAt()
        );
    }
}
