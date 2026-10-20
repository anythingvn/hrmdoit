package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Mật khẩu mới do Quản trị đặt cho một tài khoản (khi không có máy chủ thư để gửi link đặt lại).
 * {@link #toString()} không bao giờ in mật khẩu (kể cả khi LoggingAspect ghi tham số ở môi trường dev).
 */
public record AdminPasswordDTO(@NotNull @Size(min = AdminPasswordDTO.MIN_LENGTH, max = AdminPasswordDTO.MAX_LENGTH) String newPassword) {
    public static final int MIN_LENGTH = 8;

    public static final int MAX_LENGTH = 100;

    /** Mật khẩu ban đầu tùy chọn: rỗng (= mật khẩu mặc định) hoặc 8–100 ký tự. */
    public static final String OPTIONAL_PATTERN = "(?s)|.{" + MIN_LENGTH + "," + MAX_LENGTH + "}";

    @Override
    public String toString() {
        return "AdminPasswordDTO{newPassword=***}";
    }
}
