import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import BackupTab from './backup-tab';

vi.mock('axios');
vi.mock('./age-key', async importOriginal => {
  const actual = await importOriginal<typeof import('./age-key')>();
  return {
    ...actual,
    createAgeKeyPair: vi.fn().mockResolvedValue({
      secretKey: 'AGE-SECRET-KEY-1TESTSECRET',
      publicKey: 'age1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq',
    }),
  };
});
const mocked = vi.mocked(axios, true);

const DRIVE_ID = '0AAfBVsNi11ezUk9PVA';
const PUBLIC_KEY = 'age1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq';
const NONE = {
  source: 'NONE',
  serviceAccountEmail: null,
  projectId: null,
  sharedDriveId: null,
  sharedDriveName: null,
  ageRecipient: null,
  updatedBy: null,
  updatedAt: null,
};
const WEB = {
  source: 'WEB',
  serviceAccountEmail: 'hrm@proj.iam.gserviceaccount.com',
  projectId: 'proj',
  sharedDriveId: DRIVE_ID,
  sharedDriveName: 'HRM backup',
  ageRecipient: 'age1oldoldoldoldoldoldoldoldoldoldoldoldoldoldoldoldoldoldoldoldoldoldo',
  updatedBy: 'admin',
  updatedAt: '2026-10-20T03:00:00Z',
};
const STATUS = { lastBackupAt: new Date().toISOString(), offsiteConfigured: true, offsiteOk: true, offsiteReason: null };

const setup = (settings: object) => {
  mocked.get.mockImplementation((url: string) => Promise.resolve({ data: url === 'api/admin/backup-settings' ? settings : STATUS } as any));
};

const jsonFile = (text: string) => new File([text], 'key.json', { type: 'application/json' });
const chooseKey = (text = '{"type":"service_account"}') => {
  const file = jsonFile(text);
  Object.defineProperty(file, 'text', { value: () => Promise.resolve(text) });
  fireEvent.change(screen.getByLabelText('File khóa JSON'), { target: { files: [file] } });
};

describe('BackupTab', () => {
  let clicked: HTMLAnchorElement[];
  let blobs: Blob[];

  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
    mocked.delete.mockReset();
    clicked = [];
    blobs = [];
    URL.createObjectURL = vi.fn((b: Blob) => {
      blobs.push(b);
      return 'blob:x';
    });
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push(this);
    });
  });

  afterEach(() => vi.restoreAllMocks());

  it('NONE: hiện 3 bước, nút Lưu disabled', async () => {
    setup(NONE);
    render(<BackupTab />);
    expect(await screen.findByText('Khóa Google (service account)')).toBeTruthy();
    expect(screen.getByText('Shared Drive', { selector: 'h2, h3' })).toBeTruthy();
    expect(screen.getByText('Khóa mã hóa')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Lưu' }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText('Chưa cấu hình')).toBeTruthy();
  });

  it('chọn file JSON: gọi kiem-tra-khoa, hiện email và nút Sao chép', async () => {
    setup(NONE);
    mocked.post.mockResolvedValue({ data: { serviceAccountEmail: 'sa@p.iam.gserviceaccount.com', projectId: 'p' } } as any);
    render(<BackupTab />);
    await screen.findByLabelText('File khóa JSON');
    chooseKey('{"a":1}');
    expect(await screen.findByText('sa@p.iam.gserviceaccount.com')).toBeTruthy();
    expect(mocked.post).toHaveBeenCalledWith('api/admin/backup-settings/kiem-tra-khoa', { serviceAccountJson: '{"a":1}' });
    expect(screen.getByRole('button', { name: 'Sao chép' })).toBeTruthy();
  });

  it('file OAuth client: hiện câu hướng dẫn tiếng Việt', async () => {
    setup(NONE);
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.backupkeyoauth', detail: 'x' } } });
    render(<BackupTab />);
    await screen.findByLabelText('File khóa JSON');
    chooseKey();
    expect(await screen.findByText(/Đây là OAuth client ID/)).toBeTruthy();
  });

  it('link Shared Drive sai: báo ngay, không gọi API', async () => {
    setup(NONE);
    render(<BackupTab />);
    fireEvent.change(await screen.findByLabelText('Link Shared Drive'), { target: { value: 'https://example.com' } });
    expect(await screen.findByText('Link Shared Drive không hợp lệ')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('Tạo khóa mã hóa: tải file khóa, hiện khóa công khai, Lưu chờ ô xác nhận', async () => {
    setup(NONE);
    render(<BackupTab />);
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo khóa mã hóa' }));
    expect(await screen.findByText(PUBLIC_KEY)).toBeTruthy();
    const today = new Date().toISOString().slice(0, 10);
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(await blobs[0].text()).toContain('AGE-SECRET-KEY-');
    expect(clicked[0].download).toBe(`hrm-backup-key-${today}.txt`);
    expect(screen.getByRole('button', { name: 'Tải lại file khóa' })).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Lưu' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('đủ 3 bước + đánh dấu: PUT đúng body rồi hiện tóm tắt', async () => {
    setup(NONE);
    mocked.post.mockResolvedValue({ data: { serviceAccountEmail: 'sa@p.iam.gserviceaccount.com', projectId: 'p' } } as any);
    mocked.put.mockResolvedValue({ data: { ...WEB, serviceAccountEmail: 'sa@p.iam.gserviceaccount.com' } } as any);
    render(<BackupTab />);
    await screen.findByLabelText('File khóa JSON');
    chooseKey('{"k":1}');
    await screen.findByText('sa@p.iam.gserviceaccount.com');
    fireEvent.change(screen.getByLabelText('Link Shared Drive'), {
      target: { value: `https://drive.google.com/drive/folders/${DRIVE_ID}?usp=sharing` },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo khóa mã hóa' }));
    await screen.findByText(PUBLIC_KEY);
    const save = screen.getByRole('button', { name: 'Lưu' }) as HTMLButtonElement;
    expect(save.disabled).toBe(true);
    fireEvent.click(screen.getByLabelText(/Tôi đã cất file khóa bí mật/));
    expect(save.disabled).toBe(false);
    fireEvent.click(save);
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/admin/backup-settings', {
        serviceAccountJson: '{"k":1}',
        sharedDrive: DRIVE_ID,
        ageRecipient: PUBLIC_KEY,
        keySaved: true,
      }),
    );
    expect(await screen.findByRole('button', { name: 'Kiểm tra kết nối' })).toBeTruthy();
    expect(screen.queryByText('AGE-SECRET-KEY-1TESTSECRET')).toBeNull();
  });

  it('WEB: tóm tắt, Kiểm tra kết nối; Tắt cần xác nhận mới gọi DELETE', async () => {
    setup(WEB);
    mocked.post.mockResolvedValue({ data: { id: DRIVE_ID, name: 'HRM backup' } } as any);
    mocked.delete.mockResolvedValue({ data: undefined } as any);
    render(<BackupTab />);
    expect(await screen.findByText('hrm@proj.iam.gserviceaccount.com')).toBeTruthy();
    expect(screen.getAllByText('HRM backup').length).toBeGreaterThan(0);
    expect(screen.getByText(/admin/)).toBeTruthy();
    expect(screen.getByText('Cấu hình trên web')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra kết nối' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/admin/backup-settings/kiem-tra', { serviceAccountJson: null, sharedDrive: DRIVE_ID }),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Tắt sao lưu Google Drive' }));
    expect(mocked.delete).not.toHaveBeenCalled();
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Tắt sao lưu' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/admin/backup-settings'));
  });

  it('Sửa: không bắt chọn lại file, PUT gửi serviceAccountJson null và khóa mã hóa cũ', async () => {
    setup(WEB);
    mocked.put.mockResolvedValue({ data: WEB } as any);
    render(<BackupTab />);
    fireEvent.click(await screen.findByRole('button', { name: 'Sửa' }));
    const save = screen.getByRole('button', { name: 'Lưu' }) as HTMLButtonElement;
    await waitFor(() => expect(save.disabled).toBe(false));
    fireEvent.click(save);
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/admin/backup-settings', {
        serviceAccountJson: null,
        sharedDrive: DRIVE_ID,
        ageRecipient: WEB.ageRecipient,
        keySaved: true,
      }),
    );
  });

  it('Sửa rồi tạo khóa mới: cảnh báo giữ file khóa cũ', async () => {
    setup(WEB);
    render(<BackupTab />);
    fireEvent.click(await screen.findByRole('button', { name: 'Sửa' }));
    fireEvent.click(screen.getByRole('button', { name: 'Tạo khóa mã hóa' }));
    expect(await screen.findByText(/hãy giữ cả file khóa cũ/)).toBeTruthy();
  });

  it('ENV: ghi rõ cấu hình bằng file .env, vẫn có 3 bước', async () => {
    setup({ ...NONE, source: 'ENV' });
    render(<BackupTab />);
    expect(await screen.findByText('Cấu hình bằng file .env trên máy chủ')).toBeTruthy();
    expect(screen.getByText('Khóa mã hóa')).toBeTruthy();
  });
});
