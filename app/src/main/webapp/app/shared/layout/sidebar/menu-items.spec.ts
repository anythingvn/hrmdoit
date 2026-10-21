import { menuFor } from './menu-items';

describe('menuFor', () => {
  it('KE_TOAN thấy Nhân viên và Bảng lương', () => {
    const items = menuFor(['ROLE_KE_TOAN']);
    expect(items.map(m => m.key)).toEqual(['tong-quan', 'nhan-vien', 'bang-luong', 'bao-cao']);
    expect(items[2].label).toBe('Bảng lương');
    expect(items[2].path).toBe('/bang-luong');
  });

  it('HR thấy Nhân viên, Hợp đồng & mẫu và Danh mục', () => {
    const items = menuFor(['ROLE_HR']);
    expect(items.map(m => m.key)).toEqual(['tong-quan', 'nhan-vien', 'hop-dong', 'bao-cao', 'danh-muc']);
    expect(items[2].label).toBe('Hợp đồng & mẫu');
    expect(items[2].path).toBe('/hop-dong');
    expect(items[4].path).toBe('/danh-muc/phong-ban');
  });

  it('ADMIN thấy đủ 7 mục', () => {
    const items = menuFor(['ROLE_ADMIN']);
    expect(items.map(m => m.key)).toEqual(['tong-quan', 'nhan-vien', 'hop-dong', 'bang-luong', 'bao-cao', 'danh-muc', 'quan-tri']);
    expect(items.map(m => m.label)).toEqual(['Tổng quan', 'Nhân viên', 'Hợp đồng & mẫu', 'Bảng lương', 'Báo cáo', 'Danh mục', 'Quản trị']);
    expect(items[6].path).toBe('/admin/user-management');
  });

  it('BGD không thấy Hợp đồng & mẫu', () => {
    expect(menuFor(['ROLE_BGD']).map(m => m.key)).toEqual(['tong-quan', 'nhan-vien', 'bao-cao']);
  });
});

describe('menuFor với ROLE_NHAN_VIEN', () => {
  it('chỉ NHAN_VIEN thì chỉ có Hồ sơ của tôi', () => {
    const items = menuFor(['ROLE_NHAN_VIEN']);
    expect(items.map(m => m.key)).toEqual(['ho-so-cua-toi']);
    expect(items[0].label).toBe('Hồ sơ của tôi');
    expect(items[0].path).toBe('/ho-so-cua-toi');
  });

  it('nhiều vai trò thì gộp mục của từng vai trò', () => {
    expect(menuFor(['ROLE_HR', 'ROLE_NHAN_VIEN']).map(m => m.key)).toEqual([
      'tong-quan',
      'nhan-vien',
      'hop-dong',
      'bao-cao',
      'danh-muc',
      'ho-so-cua-toi',
    ]);
  });
});

describe('menuFor với hồ sơ nhân viên đã gắn', () => {
  it('vai trò khác có hồ sơ gắn tài khoản cũng thấy Hồ sơ của tôi', () => {
    expect(menuFor(['ROLE_HR'], true).map(m => m.key)).toEqual([
      'tong-quan',
      'nhan-vien',
      'hop-dong',
      'bao-cao',
      'danh-muc',
      'ho-so-cua-toi',
    ]);
    expect(menuFor(['ROLE_ADMIN'], true).map(m => m.key)).toContain('ho-so-cua-toi');
  });

  it('chưa gắn hồ sơ và không phải NHAN_VIEN thì không có Hồ sơ của tôi', () => {
    expect(menuFor(['ROLE_KE_TOAN'], false).map(m => m.key)).toEqual(['tong-quan', 'nhan-vien', 'bang-luong', 'bao-cao']);
  });
});

describe('menuFor: Tổng quan và Báo cáo', () => {
  it('HR bắt đầu bằng Tổng quan và có Báo cáo', () => {
    const items = menuFor(['ROLE_HR']);
    expect(items[0]).toMatchObject({ key: 'tong-quan', label: 'Tổng quan', path: '/tong-quan' });
    expect(items.find(m => m.key === 'bao-cao')).toMatchObject({ label: 'Báo cáo', path: '/bao-cao' });
  });

  it('chỉ NHAN_VIEN không có Tổng quan, Báo cáo', () => {
    const keys = menuFor(['ROLE_NHAN_VIEN']).map(m => m.key);
    expect(keys).not.toContain('tong-quan');
    expect(keys).not.toContain('bao-cao');
  });
});
