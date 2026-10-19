package vn.doit.hrm.service.dto;

import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/**
 * Một đoạn trên dòng thời gian vị trí làm việc.
 *
 * @param to null = đến nay (giai đoạn đang mở)
 * @param type null khi là khoảng trống chưa có dữ liệu
 */
public record AssignmentSegmentDTO(LocalDate from, LocalDate to, WorkLocationType type, String unit, String label) {}
