package vn.doit.hr.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Balance(BigDecimal namTruoc, BigDecimal namNay) {
    private static final BigDecimal ONE = BigDecimal.ONE;

    public BigDecimal total() {
        return namTruoc.add(namNay);
    }

    /** Số được xin: số dư làm tròn xuống bội số 0,5, cộng 1 ngày được ứng. */
    public BigDecimal requestable() {
        BigDecimal t = total();
        BigDecimal floorHalf = t.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.FLOOR).divide(BigDecimal.valueOf(2));
        return floorHalf.add(ONE);
    }
}
