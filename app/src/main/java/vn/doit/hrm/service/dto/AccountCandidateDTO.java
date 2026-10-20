package vn.doit.hrm.service.dto;

/** Tài khoản chưa gắn hồ sơ nào, để chọn khi gắn tài khoản có sẵn. */
public record AccountCandidateDTO(Long id, String login, String name, boolean activated) {}
