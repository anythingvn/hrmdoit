package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Các dòng của phiếu lương, theo tab "Phiếu lương" của file bảng lương (ký hiệu a → q, cùng công thức).
 *
 * <p>Dòng không có ngày công chuẩn (thực tập, HĐ dịch vụ: file chỉ ghi "Lương gộp trong tháng") thì
 * {@code c = LUONG_GOP} và a, a1, a2, b, ngày công chuẩn để trống ({@code null}).
 *
 * @param prorated có ngày công chuẩn, c tính theo ngày công thực tế
 */
public record Payslip(
        boolean prorated,
        BigDecimal a, BigDecimal a1, BigDecimal a2, BigDecimal b, BigDecimal standardDays,
        BigDecimal c, BigDecimal d,
        BigDecimal e, BigDecimal e1, BigDecimal e2, BigDecimal e3, BigDecimal e4, BigDecimal e5, BigDecimal e6,
        BigDecimal f, BigDecimal g,
        BigDecimal h, BigDecimal h1, BigDecimal h2,
        BigDecimal i, BigDecimal j, BigDecimal k, BigDecimal l, BigDecimal m, BigDecimal n,
        BigDecimal o, BigDecimal o1, BigDecimal o2, BigDecimal p, BigDecimal q) {

    public static Payslip of(Map<PayrollField, BigDecimal> amounts) {
        BigDecimal standardDays = get(amounts, PayrollField.NGAY_CONG_CHUAN);
        boolean prorated = standardDays.signum() > 0;
        BigDecimal a1 = get(amounts, PayrollField.LUONG_CO_BAN);
        BigDecimal a2 = get(amounts, PayrollField.PHU_CAP_DONG_BH);
        BigDecimal b = get(amounts, PayrollField.NGAY_CONG_THUC_TE);
        // c = a1/(1)*b + a2 như Excel; không có ngày công chuẩn thì lấy thẳng lương gộp trong tháng.
        BigDecimal c = prorated
                ? a1.divide(standardDays, 10, RoundingMode.HALF_UP).multiply(b).add(a2)
                : get(amounts, PayrollField.LUONG_GOP);
        BigDecimal d = get(amounts, PayrollField.THUONG_DU_AN);
        BigDecimal e1 = get(amounts, PayrollField.PC_CHUYEN_CAN);
        BigDecimal e2 = get(amounts, PayrollField.PC_TRACH_NHIEM);
        BigDecimal e3 = get(amounts, PayrollField.PC_XANG_XE);
        BigDecimal e4 = get(amounts, PayrollField.THUONG_KPI);
        BigDecimal e5 = get(amounts, PayrollField.OT_CHIU_THUE);
        BigDecimal e6 = get(amounts, PayrollField.TRO_CAP_THOI_VIEC);
        BigDecimal e = e1.add(e2).add(e3).add(e4).add(e5).add(e6);
        BigDecimal f = get(amounts, PayrollField.PHEP_CHUA_SU_DUNG);
        BigDecimal g = c.add(d).add(e).add(f);
        BigDecimal h1 = get(amounts, PayrollField.THUA_THIEU);
        BigDecimal h2 = get(amounts, PayrollField.OT_KHONG_CHIU_THUE);
        BigDecimal h = h1.add(h2);
        BigDecimal i = get(amounts, PayrollField.BHXH_NLD);
        BigDecimal j = get(amounts, PayrollField.BHYT_NLD);
        BigDecimal k = get(amounts, PayrollField.BHTN_NLD);
        BigDecimal l = get(amounts, PayrollField.GIAM_TRU_BAN_THAN);
        BigDecimal m = get(amounts, PayrollField.GIAM_TRU_NPT);
        // Excel ghi "n=g-h-i-j-k-l-m" nhưng công thức thật không trừ h; âm thì 0.
        BigDecimal n = g.subtract(i).subtract(j).subtract(k).subtract(l).subtract(m).max(BigDecimal.ZERO);
        BigDecimal o1 = get(amounts, PayrollField.THUE_TNCN);
        BigDecimal o2 = get(amounts, PayrollField.QUYET_TOAN_THUE);
        BigDecimal o = o1.add(o2);
        BigDecimal p = get(amounts, PayrollField.GIAM_TRU_KHAC);
        BigDecimal q = g.add(h).subtract(i).subtract(j).subtract(k).subtract(o).subtract(p);
        return new Payslip(prorated,
                prorated ? a1.add(a2) : null, prorated ? a1 : null, prorated ? a2 : null, prorated ? b : null, prorated ? standardDays : null,
                c, d, e, e1, e2, e3, e4, e5, e6, f, g, h, h1, h2, i, j, k, l, m, n, o, o1, o2, p, q);
    }

    /** q − "Lương thực nhận" của dòng; khác 0 quá 1 đồng là phiếu lệch bảng lương. */
    public BigDecimal differenceFromNet(Map<PayrollField, BigDecimal> amounts) {
        return q.subtract(get(amounts, PayrollField.THUC_NHAN));
    }

    private static BigDecimal get(Map<PayrollField, BigDecimal> amounts, PayrollField field) {
        BigDecimal v = amounts.get(field);
        return v == null ? BigDecimal.ZERO : v;
    }
}
