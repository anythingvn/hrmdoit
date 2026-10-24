package vn.doit.hrm.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.doit.hr.payroll.PayrollRules;
import vn.doit.hr.payroll.RuleBook;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.dto.RuleMilestoneDTO;

/** Mức luật định nạp sẵn trong cơ sở dữ liệu phải khớp các mốc của lõi; thêm mốc có hiệu lực ngay. */
@IntegrationTest
@org.springframework.context.annotation.Import(PayrollRuleTestSupport.class)
class RuleBookServiceIT {

    @Autowired
    private RuleBookService service;

    @Autowired
    private PayrollRuleTestSupport support;

    @AfterEach
    void reset() {
        support.resetToSeed();
    }

    @Test
    void seededBookEqualsDefaults() {
        RuleBook db = service.current();
        RuleBook core = RuleBook.defaults();
        assertThat(db.knownThrough()).isEqualTo(core.knownThrough());
        assertThat(db.milestones()).hasSize(core.milestones().size());
        for (YearMonth p = YearMonth.of(2020, 7); !p.isAfter(YearMonth.of(2026, 12)); p = p.plusMonths(1)) {
            assertSame(p, db.forPeriod(p).orElseThrow(), core.forPeriod(p).orElseThrow());
        }
    }

    @Test
    void addedMilestoneAppliesWithoutRestart() {
        service.add(milestone("2027-01", 16_000_000));
        service.setKnownThrough(YearMonth.of(2027, 12));
        assertThat(service.current().forPeriod(YearMonth.of(2027, 3)).orElseThrow().personalDeduction()).isEqualByComparingTo("16000000");
        assertThat(service.current().forPeriod(YearMonth.of(2026, 6)).orElseThrow().personalDeduction()).isEqualByComparingTo("15500000");
    }

    static RuleMilestoneDTO milestone(String from, long personal) {
        return new RuleMilestoneDTO(
            null,
            from,
            BigDecimal.valueOf(personal),
            BigDecimal.valueOf(6_200_000),
            BigDecimal.valueOf(50_600_000),
            BigDecimal.valueOf(106_200_000),
            List.of(
                new RuleMilestoneDTO.BracketDTO(BigDecimal.valueOf(10_000_000), new BigDecimal("0.05")),
                new RuleMilestoneDTO.BracketDTO(BigDecimal.valueOf(30_000_000), new BigDecimal("0.10")),
                new RuleMilestoneDTO.BracketDTO(null, new BigDecimal("0.35"))
            ),
            "Nghị quyết thử nghiệm",
            null,
            null,
            false
        );
    }

    private static void assertSame(YearMonth p, PayrollRules a, PayrollRules b) {
        assertThat(a.personalDeduction()).as("%s personal", p).isEqualByComparingTo(b.personalDeduction());
        assertThat(a.dependantDeduction()).as("%s dependant", p).isEqualByComparingTo(b.dependantDeduction());
        assertThat(a.socialInsuranceCap()).as("%s si cap", p).isEqualByComparingTo(b.socialInsuranceCap());
        assertThat(a.unemploymentInsuranceCap()).as("%s ui cap", p).isEqualByComparingTo(b.unemploymentInsuranceCap());
        assertThat(a.pitBrackets()).as("%s brackets", p).hasSameSizeAs(b.pitBrackets());
        for (int i = 0; i < a.pitBrackets().size(); i++) {
            PayrollRules.Bracket x = a.pitBrackets().get(i);
            PayrollRules.Bracket y = b.pitBrackets().get(i);
            if (y.upTo() == null) {
                assertThat(x.upTo()).isNull();
            } else {
                assertThat(x.upTo()).isEqualByComparingTo(y.upTo());
            }
            assertThat(x.rate()).isEqualByComparingTo(y.rate());
        }
    }
}
