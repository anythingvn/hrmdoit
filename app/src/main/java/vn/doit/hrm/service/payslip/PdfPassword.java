package vn.doit.hrm.service.payslip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/** Mật khẩu mở phiếu lương PDF: 6 chữ số cuối của số CCCD; mã hóa AES-256. */
public final class PdfPassword {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PdfPassword() {}

    /** Chỉ lấy chữ số; ít hơn 6 chữ số thì không có mật khẩu (không gửi được). */
    public static Optional<String> fromIdNumber(String idNumber) {
        if (idNumber == null) {
            return Optional.empty();
        }
        String digits = idNumber.replaceAll("\\D", "");
        return digits.length() < 6 ? Optional.empty() : Optional.of(digits.substring(digits.length() - 6));
    }

    /** Mã hóa với mật khẩu mở tệp; mật khẩu chủ sở hữu ngẫu nhiên; vẫn cho in và sao chép. */
    public static byte[] encrypt(byte[] pdf, String password) {
        byte[] owner = new byte[16];
        RANDOM.nextBytes(owner);
        try (PDDocument doc = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy(HexFormat.of().formatHex(owner), password, new AccessPermission());
            policy.setEncryptionKeyLength(256);
            doc.protect(policy);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
