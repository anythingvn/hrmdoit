package vn.doit.hr.assignment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Một giai đoạn làm việc tại một nơi.
 *
 * @param id khóa DB; 0 = bản ghi mới chưa lưu
 * @param unit đơn vị/khách hàng/nơi đến (vd "Ngân hàng ABC – Chi nhánh Hà Nội"); có thể trống với VAN_PHONG, TU_XA
 * @param to ngày kết thúc (tính cả ngày đó); null = đến nay, chưa xác định
 * @param decisionNo số quyết định cử/điều động (nếu có)
 */
public record Assignment(
        long id,
        String employeeCode,
        WorkLocationType type,
        String unit,
        String address,
        LocalDate from,
        LocalDate to,
        String decisionNo,
        String note) {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public boolean openEnded() {
        return to == null;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(from) && (to == null || !date.isAfter(to));
    }

    /** Giao với khoảng [start, end] (end null = vô hạn). */
    public boolean overlaps(LocalDate start, LocalDate end) {
        boolean startsBeforeOtherEnds = end == null || !from.isAfter(end);
        boolean endsAfterOtherStarts = to == null || !to.isBefore(start);
        return startsBeforeOtherEnds && endsAfterOtherStarts;
    }

    public Assignment withTo(LocalDate newTo) {
        return new Assignment(id, employeeCode, type, unit, address, from, newTo, decisionNo, note);
    }

    public Assignment withFrom(LocalDate newFrom) {
        return new Assignment(0, employeeCode, type, unit, address, newFrom, to, decisionNo, note);
    }

    /** Nhãn để hiển thị và gom nhóm báo cáo, vd "Làm việc tại khách hàng/dự án – Ngân hàng ABC". */
    public String placeLabel() {
        return unit == null || unit.isBlank() ? type.label() : type.label() + " – " + unit;
    }

    public String periodText() {
        return from.format(D) + " – " + (to == null ? "nay" : to.format(D));
    }

    @Override
    public String toString() {
        return placeLabel() + " (" + periodText() + ")";
    }
}
