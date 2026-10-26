package vn.doit.hrm.web.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.backup.BackupSettingsService;
import vn.doit.hrm.service.backup.BackupSettingsService.BackupSettingsView;
import vn.doit.hrm.service.backup.BackupSettingsService.KeyInfo;
import vn.doit.hrm.service.backup.GoogleDriveProbe.SharedDrive;

/** Cấu hình sao lưu Google Drive trên web. Chỉ Quản trị. */
@RestController
@RequestMapping("/api/admin/backup-settings")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
public class BackupSettingsResource {

    public record KeyCheckRequest(String serviceAccountJson) {}

    public record ConnectionRequest(String serviceAccountJson, String sharedDrive) {}

    public record SaveRequest(String serviceAccountJson, String sharedDrive, String ageRecipient, boolean keySaved) {}

    private final BackupSettingsService service;

    public BackupSettingsResource(BackupSettingsService service) {
        this.service = service;
    }

    @GetMapping("")
    public BackupSettingsView get() {
        return service.get();
    }

    @PostMapping("/kiem-tra-khoa")
    public KeyInfo checkKey(@RequestBody KeyCheckRequest req) {
        return service.checkKey(req.serviceAccountJson());
    }

    @PostMapping("/kiem-tra")
    public SharedDrive checkConnection(@RequestBody ConnectionRequest req) {
        return service.checkConnection(req.serviceAccountJson(), req.sharedDrive());
    }

    @PutMapping("")
    public BackupSettingsView save(@RequestBody SaveRequest req) {
        return service.save(req.serviceAccountJson(), req.sharedDrive(), req.ageRecipient(), req.keySaved());
    }

    @DeleteMapping("")
    public ResponseEntity<Void> delete() {
        service.delete();
        return ResponseEntity.noContent().build();
    }
}
