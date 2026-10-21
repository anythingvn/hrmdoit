package vn.doit.hrm.service.pdf;

/** Chuyển văn bản .docx sang .pdf. */
public interface PdfService {
    /**
     * @param docx nội dung file .docx
     * @param fileName tên file (đuôi .docx) gửi kèm cho dịch vụ chuyển đổi
     * @return nội dung file .pdf
     * @throws PdfUnavailableException khi dịch vụ chuyển đổi không kết nối được, quá thời gian hoặc báo lỗi
     */
    byte[] toPdf(byte[] docx, String fileName);
}
