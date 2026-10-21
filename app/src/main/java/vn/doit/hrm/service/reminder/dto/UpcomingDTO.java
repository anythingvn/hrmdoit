package vn.doit.hrm.service.reminder.dto;

import java.time.LocalDate;
import vn.doit.hr.reminder.ReminderType;

/** Một việc sắp đến hạn (hoặc đã quá hạn) trên trang Tổng quan, kèm liên kết về hồ sơ / hợp đồng / lần điều chỉnh lương. */
public record UpcomingDTO(
    ReminderType type,
    String typeLabel,
    Long employeeId,
    String employeeCode,
    String employeeName,
    LocalDate eventDate,
    long daysLeft,
    boolean urgent,
    String message,
    Long contractId,
    Long salaryChangeId
) {}
