package vn.doit.hrm.web.rest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Tiện ích cho phản hồi tải file. */
final class FileResponses {

    private FileResponses() {}

    /** Header {@code Content-Disposition} tải file, giữ nguyên tên tiếng Việt (RFC 5987). */
    static String attachment(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
