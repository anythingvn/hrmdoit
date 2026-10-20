package vn.doit.hr.contract;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chuyển {@link ContractData} thành map {tên trường → chuỗi} khớp với các thẻ {{...}} trong DOIT_HDLD_template.docx.
 * Tách riêng khỏi phần render để test được mà không cần thư viện Word.
 */
public final class ContractFields {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ContractFields() {
    }

    public static Map<String, String> toMap(ContractData d) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("soHopDong", d.contractNo());
        m.put("hoTen", d.fullName().toUpperCase());
        m.put("gioiTinh", d.gender());
        m.put("ngaySinh", date(d.birthDate()));
        m.put("noiCuTru", d.residence());
        m.put("dienThoai", d.phone());
        m.put("email", d.email());
        m.put("trinhDo", d.education());
        m.put("soCanCuoc", d.idNumber());
        m.put("ngayCap", date(d.idIssueDate()));
        m.put("noiCap", d.idIssuePlace());
        m.put("soTaiKhoan", d.bankAccount());
        m.put("nganHang", d.bankName());

        if (d.termMonths() == null) {
            m.put("loaiHopDong", "Không xác định thời hạn");
            m.put("thoiHan", "Không xác định thời hạn,");
            m.put("tuNgay", date(d.startDate()));
            m.put("denNgay", "khi chấm dứt theo quy định");
        } else {
            m.put("loaiHopDong", "Xác định thời hạn");
            m.put("thoiHan", termText(d.termMonths()));
            m.put("tuNgay", date(d.startDate()));
            m.put("denNgay", date(d.endDate()));
        }
        m.put("boPhan", d.department());
        m.put("viTri", d.position());
        m.put("mucLuong", money(d.baseSalary()));
        m.put("mucLuongBangChu", VietnameseNumberWords.toWords(d.baseSalary()));
        m.put("phuCap", d.allowances().isEmpty() ? "Không"
                : d.allowances().stream()
                        .map(a -> a.name() + " " + money(a.amountPerMonth()) + " đồng/tháng")
                        .collect(Collectors.joining("; ")));
        m.put("cheDoNangLuong", d.raisePolicy());
        m.put("nguoiDaiDien", d.companyRepresentative().toUpperCase());
        m.replaceAll((k, v) -> v == null ? "" : v);
        return m;
    }

    static String termText(int months) {
        if (months % 12 == 0) {
            return String.format("%02d năm", months / 12);
        }
        return String.format("%02d tháng", months);
    }

    static String money(long v) {
        return String.format("%,d", v).replace(',', '.');
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }
}
