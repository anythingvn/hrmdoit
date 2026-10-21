package vn.doit.hrm.service.pdf;

import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.doit.hrm.config.ApplicationProperties;

/** Gọi Gotenberg ({@code POST /forms/libreoffice/convert}) để chuyển .docx sang .pdf. */
@Service
public class GotenbergPdfService implements PdfService {

    private static final Logger LOG = LoggerFactory.getLogger(GotenbergPdfService.class);

    private final RestClient client;

    @Autowired
    public GotenbergPdfService(ApplicationProperties properties) {
        this(properties.getPdf().getUrl(), Duration.ofSeconds(properties.getPdf().getTimeoutSeconds()));
    }

    public GotenbergPdfService(String baseUrl, Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public byte[] toPdf(byte[] docx, String fileName) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add(
            "files",
            new ByteArrayResource(docx) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            }
        );
        try {
            byte[] pdf = client
                .post()
                .uri("/forms/libreoffice/convert")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(byte[].class);
            if (pdf == null || pdf.length == 0) {
                throw new PdfUnavailableException(new IllegalStateException("Dịch vụ PDF trả về rỗng"));
            }
            return pdf;
        } catch (RestClientException e) {
            LOG.warn("Không chuyển được PDF: {}", e.getMessage());
            throw new PdfUnavailableException(e);
        }
    }
}
