package vn.doit.hrm.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.service.numbering.NumberingSettings;

/**
 * Quy tắc mã nhân viên. Nhân viên: tiền tố + 2 số cuối năm vào làm + số thứ tự cộng dồn (mặc định DOIT, vd DOIT2401, DOIT2517);
 * thực tập: tiền tố + năm + dãy riêng (mặc định TT, vd TT2601); HĐ dịch vụ: tiền tố + số tự sinh (mặc định HDDVn).
 * Tiền tố lấy từ Cài đặt ({@link NumberingSettings}); đổi tiền tố không sửa mã đã cấp.
 */
public final class EmployeeCodes {

    private EmployeeCodes() {}

    /** Cắt khoảng trắng hai đầu và viết hoa; null/rỗng thành null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /** Tiền tố dãy mã cộng dồn của loại hồ sơ; HĐ dịch vụ không có (mã dịch vụ sinh riêng). */
    public static String prefix(PersonType type, NumberingSettings settings) {
        return settings.sequencePrefix(type);
    }

    /**
     * Mã kế tiếp: tiền tố + 2 số cuối của {@code year} + (số thứ tự lớn nhất trong {@code existing} + 1), tối thiểu 2 chữ số.
     * Số thứ tự là phần sau 2 số năm của các mã đúng dạng; mã khác dạng bị bỏ qua. Số đã dùng không cấp lại.
     */
    public static String next(String prefix, int year, List<String> existing) {
        long max = 0;
        for (String code : existing) {
            Matcher m = sequence(prefix, code);
            if (m.matches()) {
                max = Math.max(max, Long.parseLong(m.group(1)));
            }
        }
        return prefix + "%02d%02d".formatted(Math.floorMod(year, 100), max + 1);
    }

    /** Mã đúng dạng dãy cộng dồn của {@code prefix} (tiền tố + 2 số năm + số thứ tự từ 2 chữ số), dạng {@link #next} đọc. */
    public static boolean isSequenced(String prefix, String code) {
        return sequence(prefix, code).matches();
    }

    private static Matcher sequence(String prefix, String code) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "\\d{2}(\\d{2,})$");
        return pattern.matcher(code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
    }
}
