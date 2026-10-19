package vn.doit.hrm.service;

/** File của hồ sơ có trong cơ sở dữ liệu nhưng không còn trên đĩa (resource trả 404, không phải 500). */
public class StoredFileMissingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StoredFileMissingException(String storagePath) {
        super("Không tìm thấy file trên máy chủ: " + storagePath);
    }
}
