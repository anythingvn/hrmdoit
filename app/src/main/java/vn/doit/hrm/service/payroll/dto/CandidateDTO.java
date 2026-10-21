package vn.doit.hrm.service.payroll.dto;

import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;

/** Hồ sơ gợi ý để gắn với một dòng lương; {@code exactName} = họ tên trùng khớp (không dấu, không phân biệt hoa thường). */
public record CandidateDTO(
    Long id,
    String code,
    String fullName,
    String position,
    PersonType personType,
    EmploymentStatus status,
    boolean exactName
) {}
