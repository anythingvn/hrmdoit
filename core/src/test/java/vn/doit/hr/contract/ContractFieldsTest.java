package vn.doit.hr.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractFieldsTest {

    @Test
    void numberToWords() {
        assertEquals("Năm triệu bảy trăm nghìn đồng", VietnameseNumberWords.toWords(5_700_000));
        assertEquals("Mười lăm triệu năm trăm nghìn đồng", VietnameseNumberWords.toWords(15_500_000));
        assertEquals("Một triệu không trăm linh năm nghìn đồng", VietnameseNumberWords.toWords(1_005_000));
        assertEquals("Hai mươi mốt triệu đồng", VietnameseNumberWords.toWords(21_000_000));
        assertEquals("Một tỷ không trăm mười triệu đồng", VietnameseNumberWords.toWords(1_010_000_000));
        assertEquals("Bốn mươi lăm nghìn đồng", VietnameseNumberWords.toWords(45_000));
        assertEquals("Một trăm linh một đồng", VietnameseNumberWords.toWords(101));
    }

    @Test
    void fixedTermEndsTheDayBefore() {
        Map<String, String> m = ContractFields.toMap(sample(12));
        assertEquals("01 năm", m.get("thoiHan"));
        assertEquals("01/01/2026", m.get("tuNgay"));
        assertEquals("31/12/2026", m.get("denNgay"));
        assertEquals("5.700.000", m.get("mucLuong"));
        assertEquals("Phụ cấp xăng xe 1.000.000 đồng/tháng; Phụ cấp chuyên cần 2.000.000 đồng/tháng", m.get("phuCap"));
        assertEquals("NGUYỄN VĂN A", m.get("hoTen"));
        assertEquals("", m.get("email"));
    }

    @Test
    void indefiniteTerm() {
        Map<String, String> m = ContractFields.toMap(sample(null));
        assertEquals("Không xác định thời hạn", m.get("loaiHopDong"));
    }

    static ContractData sample(Integer months) {
        return new ContractData("DOIT/2026/HĐLĐ/0001", "Nguyễn Văn A", "Nam", LocalDate.of(1995, 3, 15),
                "Phường Cầu Giấy, Hà Nội", "0900000000", null, "Đại học", "001095000000",
                LocalDate.of(2021, 5, 10), "Cục Cảnh sát QLHC về TTXH", "0123456789", "Vietcombank",
                months, LocalDate.of(2026, 1, 1), "Phòng Phát triển phần mềm", "Lập trình viên", 5_700_000,
                List.of(new ContractData.Allowance("Phụ cấp xăng xe", 1_000_000),
                        new ContractData.Allowance("Phụ cấp chuyên cần", 2_000_000)),
                "Xét nâng lương mỗi năm một lần theo kết quả đánh giá", "Trần Ngọc Nga");
    }
}
