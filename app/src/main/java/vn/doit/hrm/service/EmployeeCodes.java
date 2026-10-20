package vn.doit.hrm.service;

import java.util.Locale;

/** Quy tắc mã nhân viên. */
public final class EmployeeCodes {

    public static final String SERVICE_PREFIX = "HDDV";

    private EmployeeCodes() {}

    /** Cắt khoảng trắng hai đầu và viết hoa; null/rỗng thành null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
