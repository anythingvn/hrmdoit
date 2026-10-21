package vn.doit.hrm.service.pdf;

/** Dịch vụ chuyển PDF không dùng được lúc này; file .docx vẫn giữ, HR bấm "Tạo lại PDF" sau. */
public class PdfUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "Chưa tạo được PDF — dịch vụ chuyển đổi không phản hồi";

    public PdfUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
