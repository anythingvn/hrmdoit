package vn.doit.hr.leave;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class LeaveLedgerMath {

    private LeaveLedgerMath() {}

    public static Balance balance(Pool pool, List<Entry> entries) {
        BigDecimal truoc = BigDecimal.ZERO;
        BigDecimal nay = BigDecimal.ZERO;
        for (Entry e : entries) {
            if (e.pool() != pool) {
                continue;
            }
            if (e.bucket() == Bucket.NAM_TRUOC) {
                truoc = truoc.add(e.amount());
            } else {
                nay = nay.add(e.amount());
            }
        }
        return new Balance(truoc, nay);
    }

    /** Trừ NAM_TRUOC đến hết phần dương, còn lại trừ NAM_NAY; không cho xuống dưới -maxAdvance. */
    public static List<Entry> deduct(Pool pool, Balance b, BigDecimal days, BigDecimal maxAdvance) {
        if (b.total().subtract(days).compareTo(maxAdvance.negate()) < 0) {
            throw new InsufficientLeaveException("Không đủ phép: còn " + b.total().stripTrailingZeros().toPlainString() + ", xin " + days.stripTrailingZeros().toPlainString());
        }
        List<Entry> out = new ArrayList<>();
        BigDecimal fromPrev = b.namTruoc().signum() > 0 ? b.namTruoc().min(days) : BigDecimal.ZERO;
        BigDecimal rest = days.subtract(fromPrev);
        if (fromPrev.signum() > 0) {
            out.add(new Entry(pool, Bucket.NAM_TRUOC, fromPrev.negate()));
        }
        if (rest.signum() > 0) {
            out.add(new Entry(pool, Bucket.NAM_NAY, rest.negate()));
        }
        return out;
    }

    /** Chuyển năm (01/01): phần dương về NAM_TRUOC, phần âm ở NAM_NAY. Trả các dòng chênh lệch. */
    public static List<Entry> yearEndCarry(Pool pool, Balance b) {
        BigDecimal newTruoc = b.namTruoc().max(BigDecimal.ZERO).add(b.namNay().max(BigDecimal.ZERO));
        BigDecimal newNay = b.namNay().min(BigDecimal.ZERO).add(b.namTruoc().min(BigDecimal.ZERO));
        List<Entry> out = new ArrayList<>();
        BigDecimal dTruoc = newTruoc.subtract(b.namTruoc());
        BigDecimal dNay = newNay.subtract(b.namNay());
        if (dTruoc.signum() != 0) {
            out.add(new Entry(pool, Bucket.NAM_TRUOC, dTruoc));
        }
        if (dNay.signum() != 0) {
            out.add(new Entry(pool, Bucket.NAM_NAY, dNay));
        }
        return out;
    }

    /** Hết hạn (01/04): đưa phần dương của NAM_TRUOC về 0. */
    public static List<Entry> expire(Pool pool, Balance b) {
        if (b.namTruoc().signum() <= 0) {
            return List.of();
        }
        return List.of(new Entry(pool, Bucket.NAM_TRUOC, b.namTruoc().negate()));
    }
}
