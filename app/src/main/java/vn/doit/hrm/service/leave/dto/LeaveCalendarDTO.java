package vn.doit.hrm.service.leave.dto;

import java.time.LocalDate;
import java.util.List;

/** Lịch nghỉ một tháng. Không có lý do nghỉ, giấy ốm hay ghi chú. */
public record LeaveCalendarDTO(List<Day> days, List<Item> items) {
    /** Một ngày trong tháng: cuối tuần = ngày không làm việc theo lịch công ty; holidayName chỉ có khi là ngày lễ. */
    public record Day(LocalDate date, boolean weekend, String holidayName) {}

    /** type: PHEP_NAM, VIEC_RIENG, OM, KHONG_LUONG, NGHI_BU, CONG_TAC hoặc NGHI_VIEC. firstPart/lastPart: CA_NGAY, SANG, CHIEU (null với CONG_TAC, NGHI_VIEC). */
    public record Item(
        Long employeeId,
        String employeeName,
        String department,
        String type,
        LocalDate from,
        LocalDate to,
        String firstPart,
        String lastPart,
        boolean pending
    ) {}
}
