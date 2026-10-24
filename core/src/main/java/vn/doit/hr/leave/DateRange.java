package vn.doit.hr.leave;

import java.time.LocalDate;

/**
 * Một khoảng ngày, tính cả hai đầu.
 *
 * @param from ngày bắt đầu
 * @param to ngày kết thúc; null là chưa kết thúc (đang làm)
 */
public record DateRange(LocalDate from, LocalDate to) {}
