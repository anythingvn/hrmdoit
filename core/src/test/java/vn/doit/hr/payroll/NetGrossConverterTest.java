package vn.doit.hr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NetGrossConverterTest {

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static Optional<BigDecimal> suggest(long net, int dependants) {
        return NetGrossConverter.suggestGross(bd(net), dependants, YearMonth.of(2026, 10));
    }

    private static void assertGross(Optional<BigDecimal> actual, long expected) {
        assertTrue(actual.isPresent());
        assertEquals(0, actual.get().compareTo(bd(expected)), "gross = " + actual.get());
    }

    @Test
    void netFromGross_belowCap() {
        var r = PayrollRules.forPeriod(YearMonth.of(2026, 10)).orElseThrow();
        assertEquals(0, NetGrossConverter.netFromGross(bd(30_000_000), 0, r).compareTo(bd(26_215_000)));
    }

    @Test
    void suggestGross_inverse() {
        assertGross(suggest(26_215_000, 0), 30_000_000);
    }

    @Test
    void suggestGross_withDependant() {
        assertGross(suggest(17_900_000, 1), 20_000_000);
    }

    @Test
    void suggestGross_aboveBhxhCap() {
        assertGross(suggest(50_274_400, 0), 60_000_000);
    }

    @Test
    void suggestGross_unknownYear() {
        assertTrue(NetGrossConverter.suggestGross(bd(10_000_000), 0, YearMonth.of(2030, 1)).isEmpty());
    }
}
