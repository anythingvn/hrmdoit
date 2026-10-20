import { backupWarnings } from './backup-banner';

const now = new Date('2026-10-19T12:00:00+07:00');

describe('backupWarnings', () => {
  it('không cảnh báo khi backup mới và đã cấu hình sao chép ngoài', () => {
    expect(backupWarnings({ lastBackupAt: '2026-10-19T02:00:00+07:00', offsiteConfigured: true, offsiteOk: true }, now)).toEqual([]);
  });

  it('cảnh báo khi chưa cấu hình sao chép ngoài', () => {
    expect(backupWarnings({ lastBackupAt: '2026-10-19T02:00:00+07:00', offsiteConfigured: false }, now)).toHaveLength(1);
  });

  it('cảnh báo khi backup cũ hơn 36 giờ', () => {
    const w = backupWarnings({ lastBackupAt: '2026-10-17T20:00:00+07:00', offsiteConfigured: true, offsiteOk: true }, now);
    expect(w).toHaveLength(1);
    expect(w[0]).toContain('36');
  });

  it('không cảnh báo khi đúng 36 giờ trở xuống', () => {
    expect(backupWarnings({ lastBackupAt: '2026-10-18T00:00:00+07:00', offsiteConfigured: true, offsiteOk: true }, now)).toEqual([]);
  });

  it('cảnh báo khi chưa có backup', () => {
    expect(backupWarnings({ lastBackupAt: null, offsiteConfigured: false }, now)).toHaveLength(2);
  });

  it('cảnh báo khi sao chép ngoài lỗi', () => {
    expect(backupWarnings({ lastBackupAt: '2026-10-19T02:00:00+07:00', offsiteConfigured: true, offsiteOk: false }, now)).toHaveLength(1);
  });

  it('hiện lý do khi không đẩy ra ngoài (vd thiếu khóa mã hóa)', () => {
    const w = backupWarnings(
      {
        lastBackupAt: '2026-10-19T02:00:00+07:00',
        offsiteConfigured: true,
        offsiteOk: false,
        offsiteReason: 'Chưa đặt BACKUP_AGE_RECIPIENT',
      },
      now,
    );
    expect(w).toEqual(['Lần sao chép bản sao lưu ra ngoài máy chủ gần nhất không thành công: Chưa đặt BACKUP_AGE_RECIPIENT']);
  });
});
