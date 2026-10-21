package vn.doit.hrm.service.payslip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/** Tiêu đề và thân email phiếu lương; thân không có số tiền. */
final class PayslipText {

    private PayslipText() {}

    static String subject(YearMonth period) {
        return "[DOIT] Phiếu lương tháng " + month(period);
    }

    static String body(String fullName, YearMonth period) {
        return (
            "Chào " +
            fullName +
            ",\n\n" +
            "Công ty gửi bạn phiếu lương tháng " +
            month(period) +
            " trong tệp đính kèm.\n" +
            "Mật khẩu mở tệp là 6 số cuối số CCCD của bạn.\n\n" +
            "Có thắc mắc về phiếu lương, vui lòng liên hệ bộ phận Kế toán.\n\n" +
            "— Email tự động từ hệ thống HRM DOIT.\n"
        );
    }

    static String money(BigDecimal v) {
        return "%,d".formatted(v.setScale(0, RoundingMode.HALF_UP).longValueExact()).replace(',', '.');
    }

    private static String month(YearMonth period) {
        return "%02d/%d".formatted(period.getMonthValue(), period.getYear());
    }
}
