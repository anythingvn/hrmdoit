import axios from 'axios';

/** Giới hạn file khóa service account (khớp máy chủ: 16 KB). */
export const MAX_KEY_BYTES = 16384;

export type BackupSource = 'WEB' | 'ENV' | 'NONE';

/** Cấu hình sao lưu Google Drive ({@code GET/PUT /api/admin/backup-settings}); không bao giờ có khóa bí mật. */
export interface IBackupSettings {
  source: BackupSource;
  serviceAccountEmail: string | null;
  projectId: string | null;
  sharedDriveId: string | null;
  sharedDriveName: string | null;
  ageRecipient: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface IServiceAccountInfo {
  serviceAccountEmail: string;
  projectId: string;
}

export interface ISharedDrive {
  id: string;
  name: string;
}

export interface IBackupSaveRequest {
  serviceAccountJson: string | null;
  sharedDrive: string;
  ageRecipient: string;
  keySaved: boolean;
}

const BASE = 'api/admin/backup-settings';

/** Tách ID Shared Drive từ link {@code .../folders/<ID>} hoặc ID trần; sai thì null (cùng quy tắc máy chủ). */
export const parseSharedDriveId = (input: string): string | null => {
  const text = input.trim();
  const fromLink = /\/folders\/([A-Za-z0-9_-]{10,100})/.exec(text);
  if (fromLink) return fromLink[1];
  return /^[A-Za-z0-9_-]{10,100}$/.test(text) ? text : null;
};

export const backupApi = {
  get: () => axios.get<IBackupSettings>(BASE).then(r => r.data),
  checkKey: (serviceAccountJson: string) =>
    axios.post<IServiceAccountInfo>(`${BASE}/kiem-tra-khoa`, { serviceAccountJson }).then(r => r.data),
  test: (serviceAccountJson: string | null, sharedDrive: string) =>
    axios.post<ISharedDrive>(`${BASE}/kiem-tra`, { serviceAccountJson, sharedDrive }).then(r => r.data),
  save: (req: IBackupSaveRequest) => axios.put<IBackupSettings>(BASE, req).then(r => r.data),
  disable: () => axios.delete(BASE).then(() => undefined),
};
