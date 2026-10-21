package vn.doit.hrm.service.payroll.dto;

import vn.doit.hrm.domain.enumeration.PersonType;

/** Hồ sơ rút gọn tạo từ màn Kiểm tra: họ tên, loại người, mã (bỏ qua với người dịch vụ: tự sinh HDDVn). */
public record NewEmployeeDTO(String fullName, PersonType personType, String code) {}
