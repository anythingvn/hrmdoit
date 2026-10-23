package vn.doit.hrm.service.dto;

/** Thông tin công khai cho màn đăng nhập và menu (không có người đại diện). */
public record BrandingDTO(String companyName, String shortName, String tagline, long logoVersion) {}
