package vn.doit.hrm.service.payslip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.Test;

class PdfPasswordTest {

    @Test
    void passwordFromIdNumber() {
        assertThat(PdfPassword.fromIdNumber("001 095 012345")).contains("012345");
        assertThat(PdfPassword.fromIdNumber("C1234567")).contains("234567");
        assertThat(PdfPassword.fromIdNumber("12345")).isEmpty();
        assertThat(PdfPassword.fromIdNumber(null)).isEmpty();
    }

    @Test
    void encryptedNeedsPassword() throws Exception {
        byte[] plain;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            plain = out.toByteArray();
        }
        byte[] locked = PdfPassword.encrypt(plain, "123456");
        assertThatThrownBy(() -> Loader.loadPDF(locked).close()).isInstanceOf(InvalidPasswordException.class);
        try (PDDocument opened = Loader.loadPDF(locked, "123456")) {
            assertThat(opened.getNumberOfPages()).isEqualTo(1);
        }
    }
}
