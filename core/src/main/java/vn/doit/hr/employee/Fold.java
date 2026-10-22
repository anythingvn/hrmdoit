package vn.doit.hr.employee;

import java.text.Normalizer;
import java.util.Locale;

/** Chuẩn hóa chữ để so khớp: bỏ dấu (đ→d), chữ thường, gộp khoảng trắng, cắt hai đầu. */
final class Fold {

    private Fold() {
    }

    static String fold(Object value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.toString(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
    }
}
