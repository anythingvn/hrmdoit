package vn.doit.hrm.service.backup;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.service.EmployeeRuleException;

/** Khóa service account Google đã đọc từ file JSON. {@code toString()} không lộ khóa. */
public record ServiceAccountKey(String clientEmail, String projectId, RSAPrivateKey privateKey, String rawJson) {
    private static final int MAX_BYTES = 16384;

    public static ServiceAccountKey parse(String json) {
        if (json == null) {
            throw invalid();
        }
        String text = json.startsWith("﻿") ? json.substring(1) : json;
        text = text.trim();
        if (text.isEmpty() || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid();
        }
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(text);
        } catch (RuntimeException e) {
            throw invalid();
        }
        if (root == null || !root.isObject()) {
            throw invalid();
        }
        if (root.has("web") || root.has("installed")) {
            throw new EmployeeRuleException(
                "Đây là OAuth client ID, không phải khóa service account. Vào IAM & Admin → Service Accounts → Keys để tạo khóa JSON.",
                "backupkeyoauth"
            );
        }
        if (!"service_account".equals(root.path("type").asString(null))) {
            throw invalid();
        }
        String email = root.path("client_email").asString(null);
        String pem = root.path("private_key").asString(null);
        if (email == null || email.isBlank() || pem == null || pem.isBlank()) {
            throw invalid();
        }
        String projectId = root.path("project_id").asString(null);
        try {
            StringBuilder b64 = new StringBuilder();
            for (String line : pem.split("\\R")) {
                if (!line.startsWith("-----")) {
                    b64.append(line.trim());
                }
            }
            byte[] der = Base64.getMimeDecoder().decode(b64.toString());
            RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            return new ServiceAccountKey(email.trim(), projectId, key, text);
        } catch (Exception e) {
            throw invalid();
        }
    }

    private static EmployeeRuleException invalid() {
        return new EmployeeRuleException("File không phải khóa service account hợp lệ.", "backupkeyinvalid");
    }

    @Override
    public String toString() {
        return "ServiceAccountKey[clientEmail=" + clientEmail + ", projectId=" + projectId + "]";
    }
}
