package vn.doit.hrm.service.dto;

import java.util.List;

/** Kết quả lưu trữ: những người còn lấy hồ sơ này làm quản lý và những phòng còn lấy làm trưởng phòng (HR cần đổi). */
public record ArchiveResultDTO(List<Ref> managedEmployees, List<Ref> headedDepartments) {
    public record Ref(Long id, String code, String name) {}
}
