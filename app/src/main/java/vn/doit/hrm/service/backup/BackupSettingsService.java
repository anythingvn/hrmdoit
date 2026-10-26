package vn.doit.hrm.service.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.doit.hrm.aop.logging.NoArgumentLogging;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.backup.GoogleDriveProbe.SharedDrive;

/**
 * Cấu hình sao lưu Google Drive trên web: ghi service-account.json, rclone.conf, offsite.conf vào thư mục backup-config
 * (volume dùng chung với container backup). Không bao giờ log hay trả về nội dung khóa.
 */
@NoArgumentLogging
@Service
public class BackupSettingsService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupSettingsService.class);

    static final String KEY_FILE = "service-account.json";
    static final String RCLONE_FILE = "rclone.conf";
    static final String OFFSITE_FILE = "offsite.conf";
    private static final String KEY_PATH_IN_BACKUP = "/data/backup-config/" + KEY_FILE;

    /** source: "WEB" | "ENV" | "NONE". */
    public record BackupSettingsView(
        String source,
        String serviceAccountEmail,
        String projectId,
        String sharedDriveId,
        String sharedDriveName,
        String ageRecipient,
        String updatedBy,
        String updatedAt
    ) {}

    public record KeyInfo(String serviceAccountEmail, String projectId) {}

    /** Một instance app: tuần tự hóa ghi/xóa file để hai quản trị viên không trộn file của nhau. */
    private final ReentrantLock writeLock = new ReentrantLock();

    private final Path dir;
    private final GoogleDriveProbe probe;
    private final String envRclone;
    private final String envRsync;

    public BackupSettingsService(
        ApplicationProperties props,
        GoogleDriveProbe probe,
        @Value("${BACKUP_OFFSITE_RCLONE:}") String envRclone,
        @Value("${BACKUP_OFFSITE_RSYNC:}") String envRsync
    ) {
        this.dir = Path.of(props.getBackup().getConfigDir());
        this.probe = probe;
        this.envRclone = envRclone;
        this.envRsync = envRsync;
    }

    public BackupSettingsView get() {
        Map<String, String> conf = readOffsite();
        if (!conf.isEmpty()) {
            String email = null;
            String projectId = null;
            try {
                ServiceAccountKey k = ServiceAccountKey.parse(Files.readString(dir.resolve(KEY_FILE), StandardCharsets.UTF_8));
                email = k.clientEmail();
                projectId = k.projectId();
            } catch (IOException | RuntimeException e) {
                LOG.warn("Không đọc được khóa service account đã lưu");
            }
            return new BackupSettingsView(
                "WEB",
                email,
                projectId,
                conf.get("SHARED_DRIVE_ID"),
                conf.get("SHARED_DRIVE_NAME"),
                conf.get("BACKUP_AGE_RECIPIENT"),
                conf.get("UPDATED_BY"),
                conf.get("UPDATED_AT")
            );
        }
        boolean env = !envRclone.isBlank() || !envRsync.isBlank();
        return new BackupSettingsView(env ? "ENV" : "NONE", null, null, null, null, null, null, null);
    }

    public KeyInfo checkKey(String serviceAccountJson) {
        ServiceAccountKey k = ServiceAccountKey.parse(serviceAccountJson);
        return new KeyInfo(k.clientEmail(), k.projectId());
    }

    public SharedDrive checkConnection(String serviceAccountJson, String sharedDrive) {
        ServiceAccountKey key = resolveKey(serviceAccountJson);
        return probe.check(key, SharedDriveRef.parseId(sharedDrive));
    }

    public BackupSettingsView save(String serviceAccountJson, String sharedDrive, String ageRecipient, boolean keySaved) {
        String driveId = SharedDriveRef.parseId(sharedDrive);
        String age = AgeRecipient.validate(ageRecipient);
        ServiceAccountKey key = resolveKey(serviceAccountJson);
        String storedAge = readOffsite().get("BACKUP_AGE_RECIPIENT");
        if (!keySaved && !age.equals(storedAge)) {
            throw new EmployeeRuleException("Hãy xác nhận đã cất file khóa bí mật.", "backupkeynotsaved");
        }
        requireWritableDir();
        SharedDrive drive = probe.check(key, driveId);
        // Chỉ tin id Google trả về khi trùng id đã kiểm tra; id này được ghi vào rclone.conf
        String confirmedId = driveId.equals(drive.id()) ? drive.id() : driveId;
        String login = SecurityUtils.getCurrentUserLogin().orElse("unknown");

        String rclone =
            "[gdrive]\ntype = drive\nscope = drive\nservice_account_file = " + KEY_PATH_IN_BACKUP + "\nteam_drive = " + confirmedId + "\n";
        Map<String, String> conf = new LinkedHashMap<>();
        conf.put("BACKUP_OFFSITE_RCLONE", "gdrive:HRM-backup");
        conf.put("BACKUP_AGE_RECIPIENT", age);
        conf.put("SERVICE_ACCOUNT_EMAIL", key.clientEmail());
        conf.put("SHARED_DRIVE_ID", confirmedId);
        conf.put("SHARED_DRIVE_NAME", drive.name());
        conf.put("UPDATED_BY", login);
        conf.put("UPDATED_AT", Instant.now().toString());
        StringBuilder offsite = new StringBuilder();
        conf.forEach((k, v) -> offsite.append(k).append('=').append(clean(v)).append('\n'));

        writeLock.lock();
        try {
            writeAtomic(KEY_FILE, key.rawJson(), true);
            writeAtomic(RCLONE_FILE, rclone, false);
            writeAtomic(OFFSITE_FILE, offsite.toString(), false);
        } catch (IOException e) {
            throw dirError();
        } finally {
            writeLock.unlock();
        }
        LOG.info("Cấu hình sao lưu Google Drive: {} bởi {}", "lưu", login);
        return get();
    }

    public void delete() {
        writeLock.lock();
        try {
            Files.deleteIfExists(dir.resolve(OFFSITE_FILE));
            Files.deleteIfExists(dir.resolve(RCLONE_FILE));
            Files.deleteIfExists(dir.resolve(KEY_FILE));
        } catch (IOException e) {
            throw dirError();
        } finally {
            writeLock.unlock();
        }
        LOG.info("Cấu hình sao lưu Google Drive: {} bởi {}", "tắt", SecurityUtils.getCurrentUserLogin().orElse("unknown"));
    }

    /** Dùng khóa gửi lên; nếu không có thì dùng khóa đã lưu. */
    private ServiceAccountKey resolveKey(String serviceAccountJson) {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            return ServiceAccountKey.parse(serviceAccountJson);
        }
        Path stored = dir.resolve(KEY_FILE);
        if (!Files.isRegularFile(stored)) {
            throw new EmployeeRuleException("Hãy tải lên file khóa service account.", "backupnokey");
        }
        try {
            return ServiceAccountKey.parse(Files.readString(stored, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw dirError();
        }
    }

    private Map<String, String> readOffsite() {
        Map<String, String> m = new LinkedHashMap<>();
        Path f = dir.resolve(OFFSITE_FILE);
        if (!Files.isRegularFile(f)) {
            return m;
        }
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                int i = line.indexOf('=');
                if (i > 0) {
                    m.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
                }
            }
        } catch (IOException e) {
            LOG.warn("Không đọc được {}: {}", f, e.getMessage());
        }
        return m;
    }

    private void requireWritableDir() {
        if (!Files.isDirectory(dir) || !Files.isWritable(dir)) {
            throw dirError();
        }
    }

    private static EmployeeRuleException dirError() {
        return new EmployeeRuleException(
            "Máy chủ chưa có thư mục cấu hình sao lưu. Cập nhật docker-compose.yml và khởi động lại.",
            "backupconfigdir"
        );
    }

    private static String clean(String v) {
        return v == null ? "" : v.replace("\r", " ").replace("\n", " ").trim();
    }

    /** Ghi ra file .tmp cùng thư mục rồi đổi tên nguyên tử; khóa bí mật chỉ chủ sở hữu đọc/ghi. */
    private void writeAtomic(String name, String content, boolean secret) throws IOException {
        Path target = dir.resolve(name);
        Path tmp = null;
        try {
            // Tên tạm duy nhất; khóa bí mật tạo ngay với quyền 0600 (nếu hệ thống tệp hỗ trợ POSIX)
            if (secret && Files.getFileStore(dir).supportsFileAttributeView("posix")) {
                FileAttribute<Set<PosixFilePermission>> perms = PosixFilePermissions.asFileAttribute(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                );
                tmp = Files.createTempFile(dir, name + ".", ".tmp", perms);
            } else {
                tmp = Files.createTempFile(dir, name + ".", ".tmp");
            }
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (tmp != null) {
                Files.deleteIfExists(tmp);
            }
        }
    }
}
