package vn.doit.hr.payroll;

import java.text.Normalizer;
import java.util.Locale;

final class Text {

    private Text() {
    }

    /**
     * Chuẩn hóa tiêu đề để so khớp: Unicode NFC, chữ thường, gộp khoảng trắng/xuống dòng,
     * bỏ phần chú thích trong ngoặc ở cuối ("BHXH\n(8%)" → "bhxh").
     */
    static String normalize(Object value) {
        if (value == null) {
            return "";
        }
        String s = Normalizer.normalize(value.toString(), Normalizer.Form.NFC)
                .replace("\\n", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        String prev;
        do {
            prev = s;
            s = s.replaceAll("\\s*\\([^()]*\\)\\s*$", "").trim();
        } while (!s.equals(prev));
        return s;
    }

    static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }
}
