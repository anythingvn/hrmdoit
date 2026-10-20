package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.NotNull;

/** Gắn một tài khoản có sẵn vào hồ sơ nhân viên. */
public record EmployeeAccountLinkDTO(@NotNull Long userId) {}
