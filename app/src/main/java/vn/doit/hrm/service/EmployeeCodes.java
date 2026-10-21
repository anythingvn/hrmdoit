package vn.doit.hrm.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vn.doit.hrm.domain.enumeration.PersonType;

/**
 * Quy tắc mã nhân viên. Nhân viên: DOIT + 2 số cuối năm vào làm + số thứ tự cộng dồn (vd DOIT2401, DOIT2517);
 * thực tập: TT + năm + dãy riêng (vd TT2601); HĐ dịch vụ: HDDVn tự sinh.
 */
public final class EmployeeCodes {

    public static final String SERVICE_PREFIX = "HDDV";
    public static final String STAFF_PREFIX = "DOIT";
    public static final String INTERN_PREFIX = "TT";

    private EmployeeCodes() {}

    /** Cắt khoảng trắng hai đầu và viết hoa; null/rỗng thành null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /** Tiền tố dãy mã cộng dồn của loại hồ sơ; HĐ dịch vụ không có (mã HDDVn sinh riêng). */
    public static String prefix(PersonType type) {
        return switch (type) {
            case NHAN_VIEN -> STAFF_PREFIX;
            case THUC_TAP -> INTERN_PREFIX;
            case DICH_VU -> null;
        };
    }

    /**
     * Mã kế tiếp: tiền tố + 2 số cuối của {@code year} + (số thứ tự lớn nhất trong {@code existing} + 1), tối thiểu 2 chữ số.
     * Số thứ tự là phần sau 2 số năm của các mã đúng dạng; mã khác dạng bị bỏ qua. Số đã dùng không cấp lại.
     */
    public static String next(String prefix, int year, List<String> existing) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "\\d{2}(\\d{2,})$");
        long max = 0;
        for (String code : existing) {
            Matcher m = pattern.matcher(code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
            if (m.matches()) {
                max = Math.max(max, Long.parseLong(m.group(1)));
            }
        }
        return prefix + "%02d%02d".formatted(Math.floorMod(year, 100), max + 1);
    }
}
