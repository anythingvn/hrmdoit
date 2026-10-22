package vn.doit.hrm.service.leave;

import org.springframework.security.access.AccessDeniedException;

/** Không được làm việc này với dữ liệu này (vd HR tự điều chỉnh sổ phép của mình) → 403 kèm mã lỗi {@code error.<key>}. */
public class LeaveForbiddenException extends AccessDeniedException {

    private final String errorKey;

    public LeaveForbiddenException(String message, String errorKey) {
        super(message);
        this.errorKey = errorKey;
    }

    public String getErrorKey() {
        return errorKey;
    }
}
