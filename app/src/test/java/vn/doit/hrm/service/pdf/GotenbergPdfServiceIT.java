package vn.doit.hrm.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import vn.doit.hr.contract.ContractData;
import vn.doit.hr.contract.ContractGenerator;

/** Chuyển HĐLĐ mẫu sang PDF bằng chính image {@code ops/pdf} (Gotenberg + Times New Roman). */
@Tag("pdf")
class GotenbergPdfServiceIT {

    private static GenericContainer<?> gotenberg;

    @BeforeAll
    static void start() {
        gotenberg = new GenericContainer<>(new ImageFromDockerfile("doit-hrm-pdf-test", false).withFileFromPath(".", Path.of("../ops/pdf")))
            .withExposedPorts(3000)
            .waitingFor(Wait.forHttp("/health").forPort(3000).withStartupTimeout(Duration.ofMinutes(2)));
        gotenberg.start();
    }

    @AfterAll
    static void stop() {
        if (gotenberg != null) {
            gotenberg.stop();
        }
    }

    @Test
    void convertsVietnameseDocx() throws Exception {
        PdfService service = new GotenbergPdfService(
            "http://" + gotenberg.getHost() + ":" + gotenberg.getMappedPort(3000),
            Duration.ofSeconds(60)
        );

        byte[] pdf = service.toPdf(renderSampleContract(), "hop-dong.docx");

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM").contains("NGUYỄN THỊ HỒNG NHUNG");
            assertThat(fontNames(doc)).anyMatch(n -> n.contains("TimesNewRoman"));
        }
    }

    @Test
    void unavailableThrows() throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        PdfService service = new GotenbergPdfService("http://127.0.0.1:" + closedPort, Duration.ofSeconds(5));

        assertThatThrownBy(() -> service.toPdf(renderSampleContract(), "hop-dong.docx"))
            .isInstanceOf(PdfUnavailableException.class)
            .hasMessage("Chưa tạo được PDF — dịch vụ chuyển đổi không phản hồi");
    }

    private static byte[] renderSampleContract() throws Exception {
        ContractData data = new ContractData(
            "DOIT/2026/HĐLĐ/1",
            "Nguyễn Thị Hồng Nhung",
            "Nữ",
            LocalDate.of(1996, 4, 2),
            "Phường Cầu Giấy, Hà Nội",
            "0900000000",
            "nhung@example.com",
            "Đại học",
            "001196000000",
            LocalDate.of(2021, 5, 10),
            "Cục Cảnh sát QLHC về TTXH",
            "0123456789",
            "Vietcombank",
            24,
            LocalDate.of(2026, 10, 25),
            "Phòng Phát triển phần mềm",
            "Lập trình viên",
            26_215_000,
            List.of(new ContractData.Allowance("Phụ cấp ăn trưa", 730_000)),
            "Xét nâng lương mỗi năm một lần",
            "Trần Ngọc Nga"
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream template = GotenbergPdfServiceIT.class.getResourceAsStream("/templates/DOIT_HDLD_template.docx")) {
            new ContractGenerator().render(template, data, out);
        }
        return out.toByteArray();
    }

    private static Set<String> fontNames(PDDocument doc) throws Exception {
        Set<String> names = new HashSet<>();
        for (PDPage page : doc.getPages()) {
            for (var name : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(name);
                names.add(font.getName());
            }
        }
        return names;
    }
}
