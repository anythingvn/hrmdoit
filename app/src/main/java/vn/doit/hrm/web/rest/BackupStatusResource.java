package vn.doit.hrm.web.rest;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.security.AuthoritiesConstants;

/** Trạng thái backup gần nhất, đọc từ status.json do script backup ghi. Chỉ Quản trị. */
@RestController
@RequestMapping("/api/admin")
public class BackupStatusResource {

    private static final Logger LOG = LoggerFactory.getLogger(BackupStatusResource.class);

    /** {@code offsiteReason}: lý do lần đẩy ra ngoài VPS gần nhất không thành công (vd thiếu khóa mã hóa), null nếu ổn. */
    public record BackupStatus(String lastBackupAt, boolean offsiteConfigured, Boolean offsiteOk, String offsiteReason) {}

    private final Path statusFile;
    private final ObjectMapper objectMapper;

    public BackupStatusResource(ApplicationProperties properties, ObjectMapper objectMapper) {
        this.statusFile = Path.of(properties.getBackup().getStatusFile());
        this.objectMapper = objectMapper;
    }

    @GetMapping("/backup-status")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public BackupStatus getBackupStatus() {
        if (!Files.isRegularFile(statusFile)) {
            return new BackupStatus(null, false, null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(Files.readAllBytes(statusFile));
            String last = node.hasNonNull("lastBackupAt") ? node.get("lastBackupAt").asString() : null;
            boolean offsiteConfigured = node.path("offsiteConfigured").asBoolean(false);
            Boolean offsiteOk = node.hasNonNull("offsiteOk") ? node.get("offsiteOk").asBoolean() : null;
            String reason = node.hasNonNull("offsiteReason") ? node.get("offsiteReason").asString() : null;
            return new BackupStatus(last, offsiteConfigured, offsiteOk, reason);
        } catch (Exception e) {
            LOG.warn("Không đọc được file trạng thái backup {}: {}", statusFile, e.getMessage());
            return new BackupStatus(null, false, null, null);
        }
    }
}
