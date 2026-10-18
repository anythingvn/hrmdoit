package vn.doit.hr.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Chuyển .docx sang .pdf bằng LibreOffice chạy ngầm (cần cài LibreOffice + font Times New Roman trên server).
 * Mỗi lần gọi dùng một profile tạm riêng để chạy song song không bị khóa.
 */
public class PdfConverter {

    private final String sofficeBinary;

    public PdfConverter(String sofficeBinary) {
        this.sofficeBinary = sofficeBinary;
    }

    public Path toPdf(Path docx) throws IOException, InterruptedException {
        Path outDir = docx.toAbsolutePath().getParent();
        Path profile = Files.createTempDirectory("lo-profile");
        Process p = new ProcessBuilder(sofficeBinary,
                "-env:UserInstallation=" + profile.toUri(),
                "--headless", "--convert-to", "pdf", "--outdir", outDir.toString(), docx.toString())
                .redirectErrorStream(true)
                .start();
        String log = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(120, TimeUnit.SECONDS) || p.exitValue() != 0) {
            p.destroyForcibly();
            throw new IOException("LibreOffice convert lỗi: " + log);
        }
        String name = docx.getFileName().toString().replaceFirst("\\.docx$", ".pdf");
        Path pdf = outDir.resolve(name);
        if (!Files.exists(pdf)) {
            throw new IOException("Không thấy file PDF đầu ra: " + log);
        }
        return pdf;
    }
}
