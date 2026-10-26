package vn.doit.hrm.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.service.EmployeeRuleException;

class GoogleDriveProbeTest {

    private static final String EMAIL = "bk@proj-1.iam.gserviceaccount.com";
    private static final String DRIVE_OK =
        "{\"id\":\"0Aabc\",\"name\":\"HRM backup\",\"capabilities\":{\"canAddChildren\":true,\"canTrashChildren\":true}}";

    private HttpServer server;
    private KeyPair pair;
    private ServiceAccountKey key;
    private GoogleDriveProbe probe;
    private final CopyOnWriteArrayList<String> tokenBodies = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> driveAuth = new AtomicReference<>();
    private volatile int tokenStatus = 200;
    private volatile String tokenBody = "{\"access_token\":\"t\"}";
    private volatile int driveStatus = 200;
    private volatile String driveBody = DRIVE_OK;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        pair = gen.generateKeyPair();
        String b64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(pair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
        key = ServiceAccountKey.parse(
            "{\"type\":\"service_account\",\"project_id\":\"proj-1\",\"client_email\":\"" +
                EMAIL +
                "\",\"token_uri\":\"http://127.0.0.1:1/evil\",\"private_key\":\"" +
                pem.replace("\n", "\\n") +
                "\"}"
        );
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/token", ex -> {
            tokenBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(ex, tokenStatus, tokenBody);
        });
        server.createContext("/drive/v3/drives/", ex -> {
            driveAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            reply(ex, driveStatus, driveBody);
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ApplicationProperties props = new ApplicationProperties();
        props.getBackup().setGoogleTokenUrl(base + "/token");
        props.getBackup().setGoogleDriveUrl(base + "/drive/v3");
        probe = new GoogleDriveProbe(props);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void assertCode(String code) {
        assertThatThrownBy(() -> probe.check(key, "0Aabc")).isInstanceOfSatisfying(EmployeeRuleException.class, e ->
            assertThat(e.getErrorKey()).isEqualTo(code)
        );
    }

    @Test
    void returnsDriveWhenAllowed() throws Exception {
        GoogleDriveProbe.SharedDrive d = probe.check(key, "0Aabc");
        assertThat(d).isEqualTo(new GoogleDriveProbe.SharedDrive("0Aabc", "HRM backup"));
        assertThat(driveAuth.get()).isEqualTo("Bearer t");
        String body = tokenBodies.get(0);
        String assertion = null;
        String grant = null;
        for (String kv : body.split("&")) {
            String[] p = kv.split("=", 2);
            String v = URLDecoder.decode(p[1], StandardCharsets.UTF_8);
            if (p[0].equals("assertion")) assertion = v;
            if (p[0].equals("grant_type")) grant = v;
        }
        assertThat(grant).isEqualTo("urn:ietf:params:oauth:grant-type:jwt-bearer");
        String[] parts = assertion.split("\\.");
        assertThat(parts).hasSize(3);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify((RSAPublicKey) pair.getPublic());
        sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(sig.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        JsonNode claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.path("iss").asString()).isEqualTo(EMAIL);
        assertThat(claims.path("scope").asString()).isEqualTo("https://www.googleapis.com/auth/drive");
        assertThat(claims.path("aud").asString()).isEqualTo("http://127.0.0.1:" + server.getAddress().getPort() + "/token");
    }

    @Test
    void ignoresTokenUriInKey() {
        probe.check(key, "0Aabc");
        assertThat(tokenBodies).hasSize(1);
    }

    @Test
    void mapsInvalidGrant() {
        tokenStatus = 400;
        tokenBody = "{\"error\":\"invalid_grant\"}";
        assertCode("backupkeyrevoked");
    }

    @Test
    void mapsApiDisabled() {
        driveStatus = 403;
        driveBody = "{\"error\":{\"errors\":[{\"reason\":\"accessNotConfigured\"}]}}";
        assertCode("backupdriveapioff");
        assertThatThrownBy(() -> probe.check(key, "0Aabc")).hasMessageContaining("proj-1");
        driveBody = "{\"error\":{\"details\":[{\"reason\":\"SERVICE_DISABLED\"}]}}";
        assertCode("backupdriveapioff");
    }

    @Test
    void mapsNotFound() {
        driveStatus = 404;
        driveBody = "{}";
        assertCode("backupdrivenotfound");
        assertThatThrownBy(() -> probe.check(key, "0Aabc")).hasMessageContaining(EMAIL);
    }

    @Test
    void mapsRole() {
        driveBody = "{\"id\":\"0Aabc\",\"name\":\"x\",\"capabilities\":{\"canAddChildren\":true,\"canTrashChildren\":false}}";
        assertCode("backupdriverole");
    }

    @Test
    void mapsUnreachable() {
        driveStatus = 500;
        driveBody = "{}";
        assertCode("backupgoogleunreachable");
        server.stop(0);
        assertCode("backupgoogleunreachable");
    }
}
