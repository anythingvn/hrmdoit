package vn.doit.hrm.service.leave.dto;

import java.math.BigDecimal;
import java.util.List;

/** Tổng hợp nghỉ và làm thêm một tháng cho kế toán ({@code month} = "yyyy-MM"), mỗi người NHAN_VIEN một dòng. */
public record LeaveAccountingDTO(String month, List<Row> rows) {
    /**
     * Một người trong tháng. Số ngày nghỉ là ngày làm việc của đơn đã duyệt (kể cả đang chờ hủy) rơi vào tháng; giờ làm thêm là đơn hoàn
     * tất, trả tiền, có ngày làm trong tháng, tách theo hệ số.
     *
     * @param nightCount số đơn làm thêm trả tiền có giờ đêm (22:00–06:00)
     * @param settlementDays phép năm chốt khi nghỉ việc có ngày làm việc cuối trong tháng: dương = trả tiền phép chưa nghỉ, âm = trừ lương
     *     phép đã ứng; null nếu không có (hoặc đã hoàn tác)
     * @param settlementCompensatoryHours giờ nghỉ bù còn lại được chốt khi nghỉ việc (như trên)
     * @param settlementReversal số chốt của dòng này là phần đảo ngược do hoàn tác nghỉ việc sau khi kỳ lương tháng chốt đã duyệt
     * @param archived hồ sơ đã lưu trữ (vẫn hiện ở tháng có dữ liệu, kèm nhãn "Lưu trữ")
     */
    public record Row(
        Long employeeId,
        String code,
        String fullName,
        String departmentName,
        String status,
        BigDecimal annualDays,
        BigDecimal personalDays,
        BigDecimal sickDays,
        BigDecimal unpaidDays,
        BigDecimal compensatoryDays,
        BigDecimal ot150Hours,
        BigDecimal ot200Hours,
        BigDecimal ot300Hours,
        int nightCount,
        BigDecimal settlementDays,
        BigDecimal settlementCompensatoryHours,
        boolean settlementReversal,
        boolean archived
    ) {}
}
