package vn.doit.hrm.service.payroll;

import java.time.YearMonth;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.repository.PayrollRuleMilestoneRepository;
import vn.doit.hrm.repository.PayrollRuleSettingRepository;

/** Trả mức luật về đúng dữ liệu nạp sẵn sau mỗi test (cơ sở dữ liệu test dùng chung giữa các lớp IT). */
@TestComponent
public class PayrollRuleTestSupport {

    /** Các mốc nạp sẵn có id 1..6 (changelog 20261022000100). */
    static final long SEEDED_MAX_ID = 6;

    private final PayrollRuleMilestoneRepository milestoneRepository;
    private final PayrollRuleSettingRepository settingRepository;
    private final RuleBookService ruleBookService;

    public PayrollRuleTestSupport(
        PayrollRuleMilestoneRepository milestoneRepository,
        PayrollRuleSettingRepository settingRepository,
        RuleBookService ruleBookService
    ) {
        this.milestoneRepository = milestoneRepository;
        this.settingRepository = settingRepository;
        this.ruleBookService = ruleBookService;
    }

    @Transactional
    public void resetToSeed() {
        milestoneRepository.findAll().stream().filter(m -> m.getId() > SEEDED_MAX_ID).forEach(milestoneRepository::delete);
        settingRepository.findById(1L).ifPresent(s -> s.setKnownThrough(YearMonth.of(2026, 12).toString()));
        ruleBookService.refresh();
    }
}
