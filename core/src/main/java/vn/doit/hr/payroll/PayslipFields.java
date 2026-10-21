package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thẻ của mẫu Word "Phiếu lương": thông tin người, các dòng a → q, ghi chú theo mốc luật của kỳ. */
public final class PayslipFields {

    /** Phần đầu phiếu. */
    public record Header(String companyName, YearMonth period, String fullName, String employeeCode) {
    }

    /**
     * Mức luật in ở phần Ghi chú, lấy từ bản chụp luật của phiên bản bảng lương (không tác động ngược).
     *
     * @param legalBasis căn cứ văn bản; trống thì không in dòng "Căn cứ"
     */
    public record Notes(BigDecimal socialInsuranceCap, BigDecimal unemploymentInsuranceCap, BigDecimal personalDeduction,
                        BigDecimal dependantDeduction, String legalBasis) {
    }

    private PayslipFields() {
    }

    /** {@code notes == null} (phiên bản không có bản chụp luật): bốn dòng ghi chú để trống. */
    public static Map<String, String> toMap(Header header, Payslip s, Notes notes) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("tenCongTy", text(header.companyName()));
        m.put("kyLuong", String.format("%02d/%d", header.period().getMonthValue(), header.period().getYear()));
        m.put("hoTen", text(header.fullName()));
        m.put("maNhanVien", text(header.employeeCode()));
        m.put("ngayCongChuan", number(s.standardDays()));
        m.put("a", money(s.a()));
        m.put("a1", money(s.a1()));
        m.put("a2", money(s.a2()));
        m.put("b", number(s.b()));
        m.put("c", money(s.c()));
        m.put("d", money(s.d()));
        m.put("e", money(s.e()));
        m.put("e1", money(s.e1()));
        m.put("e2", money(s.e2()));
        m.put("e3", money(s.e3()));
        m.put("e4", money(s.e4()));
        m.put("e5", money(s.e5()));
        m.put("e6", money(s.e6()));
        m.put("f", money(s.f()));
        m.put("g", money(s.g()));
        m.put("h", money(s.h()));
        m.put("h1", money(s.h1()));
        m.put("h2", money(s.h2()));
        m.put("i", money(s.i()));
        m.put("j", money(s.j()));
        m.put("k", money(s.k()));
        m.put("l", money(s.l()));
        m.put("m", money(s.m()));
        m.put("n", money(s.n()));
        m.put("o", money(s.o()));
        m.put("o1", money(s.o1()));
        m.put("o2", money(s.o2()));
        m.put("p", money(s.p()));
        m.put("q", money(s.q()));
        if (notes == null) {
            m.put("ghiChu1", "");
            m.put("ghiChu2", "");
            m.put("ghiChu3", "");
            m.put("ghiChu4", "");
        } else {
            m.put("ghiChu1", "1. Tỉ lệ người lao động đóng: BHXH 8%, BHYT 1,5%, BHTN 1%.");
            m.put("ghiChu2", "2. Mức lương đóng BHXH, BHYT tối đa " + money(notes.socialInsuranceCap())
                    + " đồng/tháng; mức lương đóng BHTN tối đa " + money(notes.unemploymentInsuranceCap()) + " đồng/tháng.");
            m.put("ghiChu3", "3. Giảm trừ gia cảnh: bản thân " + money(notes.personalDeduction())
                    + " đồng/tháng, người phụ thuộc " + money(notes.dependantDeduction()) + " đồng/người/tháng.");
            m.put("ghiChu4", notes.legalBasis() == null || notes.legalBasis().isBlank() ? "" : "4. Căn cứ: " + notes.legalBasis().trim() + ".");
        }
        return m;
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }

    /** Làm tròn đến đồng, nhóm nghìn bằng dấu chấm; {@code null} → trống. */
    static String money(BigDecimal v) {
        if (v == null) {
            return "";
        }
        return String.format("%,d", v.setScale(0, RoundingMode.HALF_UP).longValueExact()).replace(',', '.');
    }

    private static String number(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
