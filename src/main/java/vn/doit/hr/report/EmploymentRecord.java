package vn.doit.hr.report;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Quá trình làm việc của một nhân viên – đủ để tính biến động và nghỉ việc.
 * Lấy từ OrangeHRM (ngày vào làm, ngày nghỉ việc, lý do) và phòng ban hiện tại.
 *
 * @param terminationDate ngày làm việc cuối cùng; null = đang làm việc
 * @param exitType loại nghỉ việc; null nếu đang làm việc
 * @param exitReason lý do chi tiết (tự do), vd "Lương thấp hơn thị trường"
 */
public record EmploymentRecord(
        String code,
        String fullName,
        String department,
        String position,
        LocalDate hireDate,
        LocalDate terminationDate,
        ExitType exitType,
        String exitReason) {

    public enum ExitType {
        TU_NGHI("Tự nghỉ việc", true),
        HET_HAN_HD("Hết hạn HĐ, không tái ký", false),
        KHONG_DAT_THU_VIEC("Không đạt thử việc", false),
        CONG_TY_CHAM_DUT("Công ty chấm dứt HĐ", false),
        KHAC("Khác", false);

        private final String label;
        private final boolean voluntary;

        ExitType(String label, boolean voluntary) {
            this.label = label;
            this.voluntary = voluntary;
        }

        public String label() {
            return label;
        }

        public boolean voluntary() {
            return voluntary;
        }
    }

    public EmploymentRecord {
        if (hireDate == null) {
            throw new IllegalArgumentException("Thiếu ngày vào làm: " + code);
        }
        if (terminationDate != null && terminationDate.isBefore(hireDate)) {
            throw new IllegalArgumentException("Ngày nghỉ việc trước ngày vào làm: " + code);
        }
    }

    /** Còn trong danh sách sau khi kết thúc ngày {@code d} (người nghỉ đúng ngày d không còn tính). */
    public boolean onPayrollAfter(LocalDate d) {
        return !hireDate.isAfter(d) && (terminationDate == null || terminationDate.isAfter(d));
    }

    public boolean hiredBetween(LocalDate from, LocalDate to) {
        return !hireDate.isBefore(from) && !hireDate.isAfter(to);
    }

    public boolean leftBetween(LocalDate from, LocalDate to) {
        return terminationDate != null && !terminationDate.isBefore(from) && !terminationDate.isAfter(to);
    }

    /** Thâm niên khi nghỉ, tính bằng tháng tròn (nghỉ cuối ngày 31/03 sau khi vào 01/01 = 3 tháng). */
    public long tenureMonthsAtExit() {
        if (terminationDate == null) {
            throw new IllegalStateException(code + " chưa nghỉ việc");
        }
        return ChronoUnit.MONTHS.between(hireDate, terminationDate.plusDays(1));
    }
}
