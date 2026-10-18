package vn.doit.hr.assignment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dữ liệu quyết định cử nhân sự đi công tác / làm việc tại khách hàng.
 * Tạo quyết định → ghi luôn {@link #assignment()} vào lịch sử qua {@link AssignmentHistory#add}.
 */
public record AssignmentDecision(
        String decisionNo,
        LocalDate signDate,
        String signerName,
        String signerTitle,
        String employeeName,
        String employeeCode,
        String position,
        String department,
        String contractNo,
        Assignment assignment,
        String task,
        String benefits) {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public AssignmentDecision {
        if (assignment.type() != WorkLocationType.CONG_TAC && assignment.type() != WorkLocationType.KHACH_HANG) {
            throw new IllegalArgumentException("Quyết định cử chỉ dùng cho đi công tác hoặc làm việc tại khách hàng");
        }
    }

    /** Map {thẻ → giá trị} cho DOIT_QD_CuNhanSu_template.docx. */
    public Map<String, String> toFields() {
        Map<String, String> m = new LinkedHashMap<>();
        boolean trip = assignment.type() == WorkLocationType.CONG_TAC;
        m.put("soQuyetDinh", decisionNo);
        m.put("ngay", String.format("%02d", signDate.getDayOfMonth()));
        m.put("thang", String.format("%02d", signDate.getMonthValue()));
        m.put("nam", String.valueOf(signDate.getYear()));
        m.put("trichYeu", trip ? "cử nhân sự đi công tác" : "cử nhân sự làm việc tại khách hàng");
        m.put("hoTen", employeeName);
        m.put("maNhanVien", employeeCode);
        m.put("viTri", position);
        m.put("boPhan", department);
        m.put("soHopDong", contractNo);
        m.put("hinhThuc", trip ? "đi công tác tại" : "làm việc tại");
        m.put("donVi", assignment.unit());
        m.put("diaChi", assignment.address());
        m.put("tuNgay", assignment.from().format(D));
        m.put("denNgay", assignment.to() == null ? "khi có quyết định khác" : assignment.to().format(D));
        m.put("noiDung", task);
        m.put("cheDo", benefits);
        m.put("chucVuKy", signerTitle.toUpperCase());
        m.put("nguoiKy", signerName);
        m.replaceAll((k, v) -> v == null ? "" : v);
        return m;
    }
}
