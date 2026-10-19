package vn.doit.hrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import vn.doit.hr.payroll.PayrollRules;

class CoreWiringTest {

    @Test
    void appSeesCoreModule() {
        assertThat(PayrollRules.forPeriod(YearMonth.of(2026, 10))).isPresent();
    }
}
