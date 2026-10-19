package vn.doit.hrm.service;

import java.text.Normalizer;
import java.util.Locale;

/** Tiện ích xử lý chữ tiếng Việt cho tìm kiếm không dấu. */
public final class VietnameseText {

    private VietnameseText() {}

    /** Bỏ dấu (đ/Đ thành d), chữ thường, gộp khoảng trắng; null thành chuỗi rỗng. */
    public static String fold(String s) {
        if (s == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
