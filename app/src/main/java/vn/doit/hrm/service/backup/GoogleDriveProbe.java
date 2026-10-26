package vn.doit.hrm.service.backup;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.service.EmployeeRuleException;

/** Kiểm tra service account truy cập được Shared Drive với đủ quyền (JWT RS256 → access token → drives.get). */
@Component
public class GoogleDriveProbe {

    public record SharedDrive(String id, String name) {}

    private static final String SCOPE = "https://www.googleapis.com/auth/drive";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ApplicationProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public GoogleDriveProbe(ApplicationProperties props) {
        this.props = props;
    }

    public SharedDrive check(ServiceAccountKey key, String driveId) {
        String token = fetchToken(key);
        String fields = URLEncoder.encode("id,name,capabilities(canAddChildren,canTrashChildren)", StandardCharsets.UTF_8);
        String url =
            props.getBackup().getGoogleDriveUrl() + "/drives/" + URLEncoder.encode(driveId, StandardCharsets.UTF_8) + "?fields=" + fields;
        HttpResponse<String> res = send(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build()
        );
        int status = res.statusCode();
        String body = res.body() == null ? "" : res.body();
        if (status == 403 && (body.contains("accessNotConfigured") || body.contains("SERVICE_DISABLED"))) {
            throw new EmployeeRuleException(
                "Chưa bật Google Drive API cho project " + key.projectId() + ". Vào APIs & Services → Library để bật.",
                "backupdriveapioff"
            );
        }
        if (status == 404 || status == 403) {
            throw new EmployeeRuleException(
                "Không thấy Shared Drive. Kiểm tra link, và đã thêm " + key.clientEmail() + " làm thành viên chưa.",
                "backupdrivenotfound"
            );
        }
        if (status != 200) {
            throw unreachable();
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (RuntimeException e) {
            throw unreachable();
        }
        JsonNode caps = root.path("capabilities");
        if (!caps.path("canAddChildren").asBoolean(false) || !caps.path("canTrashChildren").asBoolean(false)) {
            throw new EmployeeRuleException("Service account cần vai trò Người quản lý nội dung để thêm và dọn bản cũ.", "backupdriverole");
        }
        return new SharedDrive(root.path("id").asString(driveId), root.path("name").asString(""));
    }

    private String fetchToken(ServiceAccountKey key) {
        // Luôn dùng URL cấu hình, không tin token_uri trong file khóa.
        String tokenUrl = props.getBackup().getGoogleTokenUrl();
        String form =
            "grant_type=" +
            URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8) +
            "&assertion=" +
            URLEncoder.encode(jwt(key, tokenUrl), StandardCharsets.UTF_8);
        HttpResponse<String> res = send(
            HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()
        );
        String body = res.body() == null ? "" : res.body();
        if (res.statusCode() == 200) {
            try {
                String t = mapper.readTree(body).path("access_token").asString(null);
                if (t != null && !t.isBlank()) {
                    return t;
                }
            } catch (RuntimeException ignored) {
                // rơi xuống lỗi chung bên dưới
            }
            throw unreachable();
        }
        if (res.statusCode() == 400 || res.statusCode() == 401) {
            throw new EmployeeRuleException("Khóa này đã bị xóa hoặc vô hiệu hóa trên Google Cloud. Hãy tạo khóa mới.", "backupkeyrevoked");
        }
        throw unreachable();
    }

    private String jwt(ServiceAccountKey key, String aud) {
        long now = Instant.now().getEpochSecond();
        String header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        var claims = mapper.createObjectNode();
        claims.put("iss", key.clientEmail());
        claims.put("scope", SCOPE);
        claims.put("aud", aud);
        claims.put("iat", now);
        claims.put("exp", now + 600);
        String payload = b64(mapper.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(key.privateKey());
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64(sig.sign());
        } catch (GeneralSecurityException e) {
            throw new EmployeeRuleException("Khóa service account không hợp lệ.", "backupkeyinvalid");
        }
    }

    private HttpResponse<String> send(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unreachable();
        } catch (IOException | RuntimeException e) {
            throw unreachable();
        }
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static EmployeeRuleException unreachable() {
        return new EmployeeRuleException("Máy chủ không kết nối được tới Google. Kiểm tra mạng rồi thử lại.", "backupgoogleunreachable");
    }
}
