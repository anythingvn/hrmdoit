package vn.doit.hrm.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import vn.doit.hrm.service.EmployeeRuleException;

class BackupInputsTest {

    private static final String DRIVE_ID = "0AAfBVsNi11ezUk9PVA";

    private static String fakeKeyJson(String lineSep) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String b64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(gen.generateKeyPair().getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
        return (
            "{" +
            lineSep +
            "\"type\":\"service_account\"," +
            lineSep +
            "\"project_id\":\"proj-1\"," +
            lineSep +
            "\"client_email\":\"bk@proj-1.iam.gserviceaccount.com\"," +
            lineSep +
            "\"private_key\":\"" +
            pem.replace("\n", "\\n") +
            "\"" +
            lineSep +
            "}"
        );
    }

    private static void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(EmployeeRuleException.class, e -> assertThat(e.getErrorKey()).isEqualTo(code));
    }

    @Test
    void parsesValidKey() throws Exception {
        ServiceAccountKey k = ServiceAccountKey.parse(fakeKeyJson(""));
        assertThat(k.clientEmail()).isEqualTo("bk@proj-1.iam.gserviceaccount.com");
        assertThat(k.projectId()).isEqualTo("proj-1");
        assertThat(k.privateKey()).isNotNull();
        assertThat(k.toString()).doesNotContain("BEGIN PRIVATE KEY");
        assertThat(k.toString()).doesNotContain("MII");
    }

    @Test
    void acceptsBomAndCrlf() throws Exception {
        ServiceAccountKey k = ServiceAccountKey.parse("﻿" + fakeKeyJson("\r\n"));
        assertThat(k.clientEmail()).isEqualTo("bk@proj-1.iam.gserviceaccount.com");
    }

    @Test
    void rejectsOauthClient() {
        assertCode(() -> ServiceAccountKey.parse("{\"web\":{\"client_id\":\"x\"}}"), "backupkeyoauth");
        assertCode(() -> ServiceAccountKey.parse("{\"installed\":{\"client_id\":\"x\"}}"), "backupkeyoauth");
    }

    @Test
    void rejectsInvalid() {
        assertCode(() -> ServiceAccountKey.parse("abc"), "backupkeyinvalid");
        assertCode(() -> ServiceAccountKey.parse(null), "backupkeyinvalid");
        assertCode(() -> ServiceAccountKey.parse("{\"type\":\"user\"}"), "backupkeyinvalid");
        assertCode(
            () -> ServiceAccountKey.parse("{\"type\":\"service_account\",\"client_email\":\"a@b\",\"project_id\":\"p\"}"),
            "backupkeyinvalid"
        );
        assertCode(
            () ->
                ServiceAccountKey.parse(
                    "{\"type\":\"service_account\",\"client_email\":\"a@b\",\"project_id\":\"p\",\"private_key\":\"-----BEGIN PRIVATE KEY-----\\nrac\\n-----END PRIVATE KEY-----\"}"
                ),
            "backupkeyinvalid"
        );
        assertCode(() -> ServiceAccountKey.parse("x".repeat(16385)), "backupkeyinvalid");
    }

    @Test
    void parsesDriveLinks() {
        assertThat(SharedDriveRef.parseId("https://drive.google.com/drive/folders/" + DRIVE_ID)).isEqualTo(DRIVE_ID);
        assertThat(SharedDriveRef.parseId("https://drive.google.com/drive/u/1/folders/" + DRIVE_ID + "?usp=sharing")).isEqualTo(DRIVE_ID);
        assertThat(SharedDriveRef.parseId("https://drive.google.com/drive/folders/" + DRIVE_ID + "#abc")).isEqualTo(DRIVE_ID);
        assertThat(SharedDriveRef.parseId("  " + DRIVE_ID + " ")).isEqualTo(DRIVE_ID);
        assertCode(() -> SharedDriveRef.parseId("https://example.com"), "backupdrivelink");
        assertCode(() -> SharedDriveRef.parseId(""), "backupdrivelink");
        assertCode(() -> SharedDriveRef.parseId(null), "backupdrivelink");
        assertCode(() -> SharedDriveRef.parseId("a b"), "backupdrivelink");
    }

    @Test
    void validatesAgeRecipient() {
        String ok = "age1e3tlg3rkq2fjze8gzynkhx4dlsl3wn2pk9ahkj8hawx5x253rv0qyktdll";
        assertThat(AgeRecipient.validate("  " + ok + "\n")).isEqualTo(ok);
        assertCode(() -> AgeRecipient.validate("age1abc"), "backupagerecipient");
        assertCode(
            () -> AgeRecipient.validate("AGE-SECRET-KEY-1QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ"),
            "backupagerecipient"
        );
        assertCode(() -> AgeRecipient.validate(ok.toUpperCase()), "backupagerecipient");
        assertCode(() -> AgeRecipient.validate(null), "backupagerecipient");
    }
}
