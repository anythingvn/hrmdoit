package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.PersonType;

/**
 * Yêu cầu nhận làm lại người đã nghỉ.
 *
 * @param joinDate ngày vào làm mới (bắt buộc), phải sau ngày làm việc cuối của lần trước
 * @param departmentId phòng ban mới (tùy chọn; bỏ trống = giữ nguyên)
 * @param position vị trí mới (tùy chọn; bỏ trống = giữ nguyên)
 * @param personType loại hồ sơ mới (tùy chọn; bỏ trống = giữ nguyên)
 */
public record RehireRequestDTO(LocalDate joinDate, Long departmentId, @Size(max = 100) String position, PersonType personType) {}
