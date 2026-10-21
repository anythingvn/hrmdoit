package vn.doit.hrm.service.mail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mã hóa mật khẩu hộp thư lưu trong cơ sở dữ liệu (AES-256-GCM). Khóa lấy từ {@code APPLICATION_MAIL_SECRET_KEY}
 * (chuỗi ít nhất 16 ký tự, băm SHA-256 thành khóa AES); không có khóa thì không lưu được mật khẩu trên giao diện.
 */
@Component
public class MailSecretBox {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_SECRET_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public MailSecretBox(@Value("${application.mail.secret-key:}") String secret) {
        this.key = secret == null || secret.trim().length() < MIN_SECRET_LENGTH ? null : new SecretKeySpec(sha256(secret.trim()), "AES");
    }

    public boolean available() {
        return key != null;
    }

    /** base64(IV 12 byte + bản mã kèm tag). */
    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không mã hóa được mật khẩu email", e);
        }
    }

    /** Sai khóa (vd đổi APPLICATION_MAIL_SECRET_KEY) hoặc dữ liệu hỏng → {@link IllegalStateException}. */
    public String decrypt(String stored) {
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Không giải mã được mật khẩu email (khóa APPLICATION_MAIL_SECRET_KEY đã đổi?)", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("Chưa đặt APPLICATION_MAIL_SECRET_KEY");
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
