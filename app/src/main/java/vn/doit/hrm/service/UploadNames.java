package vn.doit.hrm.service;

/** Tên file người dùng tải lên: bỏ đường dẫn, cắt khoảng trắng, giới hạn 255 ký tự (giữ phần cuối có đuôi file). */
public final class UploadNames {

    private UploadNames() {}

    /** @throws EmployeeRuleException {@code filenamemissing} nếu tên trống */
    public static String displayName(String originalName) {
        String name = originalName == null ? "" : originalName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).trim();
        if (name.isEmpty()) {
            throw new EmployeeRuleException("Thiếu tên file", "filenamemissing");
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
