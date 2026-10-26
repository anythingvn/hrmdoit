import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert } from 'react-bootstrap';

export interface BackupStatus {
  lastBackupAt: string | null;
  offsiteConfigured: boolean;
  offsiteOk?: boolean | null;
  /** Lý do không đẩy được ra ngoài VPS (vd thiếu khóa mã hóa BACKUP_AGE_RECIPIENT); nhiều nơi lỗi thì nối bằng "; ". */
  offsiteReason?: string | null;
}

export const STALE_AFTER_HOURS = 36;

/** Danh sách cảnh báo (tiếng Việt) cho trạng thái backup; rỗng nếu mọi thứ ổn. */
export const backupWarnings = (status: BackupStatus | null, now: Date = new Date()): string[] => {
  if (!status) {
    return [];
  }
  const warnings: string[] = [];
  const last = status.lastBackupAt ? new Date(status.lastBackupAt) : null;
  if (!last || isNaN(last.getTime())) {
    warnings.push('Chưa có bản sao lưu nào được ghi nhận.');
  } else if (now.getTime() - last.getTime() > STALE_AFTER_HOURS * 3600 * 1000) {
    warnings.push(`Bản sao lưu gần nhất đã quá ${STALE_AFTER_HOURS} giờ trước.`);
  }
  if (!status.offsiteConfigured) {
    warnings.push(
      'Chưa cấu hình sao chép bản sao lưu ra ngoài máy chủ (Google Drive: BACKUP_OFFSITE_RCLONE, hoặc máy khác: BACKUP_OFFSITE_RSYNC).',
    );
  } else if (status.offsiteOk === false) {
    warnings.push(
      status.offsiteReason
        ? `Lần sao chép bản sao lưu ra ngoài máy chủ gần nhất không thành công: ${status.offsiteReason}`
        : 'Lần sao chép bản sao lưu ra ngoài máy chủ gần nhất bị lỗi.',
    );
  }
  return warnings;
};

export const BackupBanner = () => {
  const [status, setStatus] = useState<BackupStatus | null>(null);

  useEffect(() => {
    axios
      .get<BackupStatus>('api/admin/backup-status')
      .then(res => setStatus(res.data))
      .catch(() => setStatus(null));
  }, []);

  const warnings = backupWarnings(status);
  if (warnings.length === 0) {
    return null;
  }
  return (
    <Alert variant="warning" className="backup-banner" data-cy="backup-banner">
      <strong>Cảnh báo sao lưu:</strong>
      <ul className="mb-0">
        {warnings.map(w => (
          <li key={w}>{w}</li>
        ))}
      </ul>
    </Alert>
  );
};

export default BackupBanner;
