package vn.doit.hr.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.doit.hr.leave.LeaveTestSupport.d;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeaveLedgerMathTest {

    private static Balance bal(String truoc, String nay) {
        return new Balance(d(truoc), d(nay));
    }

    private static BigDecimal sum(List<Entry> es, Bucket b) {
        return es.stream().filter(e -> e.bucket() == b).map(Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void balanceSumsPoolAndBucket() {
        List<Entry> es = List.of(
            new Entry(Pool.PHEP_NAM, Bucket.NAM_TRUOC, d("3")),
            new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, d("2")),
            new Entry(Pool.NGHI_BU, Bucket.NAM_NAY, d("8")),
            new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, d("-0.5"))
        );
        Balance b = LeaveLedgerMath.balance(Pool.PHEP_NAM, es);
        assertEquals(0, d("3").compareTo(b.namTruoc()));
        assertEquals(0, d("1.5").compareTo(b.namNay()));
    }

    @Test
    void deductTakesPreviousYearFirst() {
        List<Entry> es = LeaveLedgerMath.deduct(Pool.PHEP_NAM, bal("3", "2"), d("4"), d("1"));
        assertEquals(0, d("-3").compareTo(sum(es, Bucket.NAM_TRUOC)));
        assertEquals(0, d("-1").compareTo(sum(es, Bucket.NAM_NAY)));
    }

    @Test
    void advanceOfOneDayAllowed() {
        List<Entry> es = LeaveLedgerMath.deduct(Pool.PHEP_NAM, bal("0", "0.5"), d("1.5"), d("1"));
        assertEquals(0, d("-1.5").compareTo(sum(es, Bucket.NAM_NAY)));
    }

    @Test
    void beyondAdvanceRejected() {
        assertThrows(InsufficientLeaveException.class, () -> LeaveLedgerMath.deduct(Pool.PHEP_NAM, bal("0", "0.5"), d("2"), d("1")));
    }

    @Test
    void requestableAddsOneAdvanceDay() {
        assertEquals(0, d("5.5").compareTo(bal("2.75", "2").requestable()));
        assertEquals(0, d("5.5").compareTo(new Balance(d("4.75"), d("0")).requestable()));
    }

    private static Balance apply(Balance b, List<Entry> es) {
        return new Balance(b.namTruoc().add(sum(es, Bucket.NAM_TRUOC)), b.namNay().add(sum(es, Bucket.NAM_NAY)));
    }

    @Test
    void yearEndCarryPositiveGoesToPreviousYearBucket() {
        Balance start = bal("2.5", "1");
        Balance after = apply(start, LeaveLedgerMath.yearEndCarry(Pool.PHEP_NAM, start));
        assertEquals(0, d("3.5").compareTo(after.namTruoc()));
        assertEquals(0, d("0").compareTo(after.namNay()));
    }

    @Test
    void yearEndCarryNegativeStaysInNamNay() {
        Balance start = bal("0", "-1");
        List<Entry> es = LeaveLedgerMath.yearEndCarry(Pool.PHEP_NAM, start);
        Balance after = apply(start, es);
        assertEquals(0, d("0").compareTo(after.namTruoc()));
        assertEquals(0, d("-1").compareTo(after.namNay()));
    }

    @Test
    void expireZeroesPositivePreviousYear() {
        List<Entry> es = LeaveLedgerMath.expire(Pool.PHEP_NAM, bal("1.5", "3"));
        assertEquals(1, es.size());
        assertEquals(Bucket.NAM_TRUOC, es.get(0).bucket());
        assertEquals(0, d("-1.5").compareTo(es.get(0).amount()));
        assertTrue(LeaveLedgerMath.expire(Pool.PHEP_NAM, bal("0", "3")).isEmpty());
        assertTrue(LeaveLedgerMath.expire(Pool.PHEP_NAM, bal("-1", "3")).isEmpty());
    }
}
