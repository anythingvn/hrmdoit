package vn.doit.hrm.service.dto;

import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;

/** Hợp đồng của chính nhân viên đang đăng nhập (không có lương): để tải bản đã ký. */
public record OwnContractDTO(
    Long id,
    ContractType type,
    String contractNo,
    LocalDate startDate,
    LocalDate endDate,
    ContractStatus status,
    boolean hasSigned
) {}
