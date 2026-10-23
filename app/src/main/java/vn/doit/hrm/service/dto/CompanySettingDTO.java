package vn.doit.hrm.service.dto;

/**
 * Cài đặt công ty cho Quản trị và HR (có người đại diện). Khi gửi lên ({@code PUT}), {@code logoVersion} bị bỏ qua: logo đổi
 * qua API riêng.
 */
public record CompanySettingDTO(
    String companyName,
    String shortName,
    String tagline,
    String representativeName,
    String representativeTitle,
    Long logoVersion
) {}
