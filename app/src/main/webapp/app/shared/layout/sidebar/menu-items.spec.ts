import { menuFor } from './menu-items';

describe('menuFor', () => {
  it('KE_TOAN chỉ thấy Nhân viên', () => {
    expect(menuFor(['ROLE_KE_TOAN']).map(m => m.key)).toEqual(['nhan-vien']);
  });

  it('HR thấy Nhân viên và Danh mục', () => {
    const items = menuFor(['ROLE_HR']);
    expect(items.map(m => m.key)).toEqual(['nhan-vien', 'danh-muc']);
    expect(items[1].path).toBe('/danh-muc/phong-ban');
  });

  it('ADMIN thấy đủ 3 mục', () => {
    const items = menuFor(['ROLE_ADMIN']);
    expect(items.map(m => m.key)).toEqual(['nhan-vien', 'danh-muc', 'quan-tri']);
    expect(items.map(m => m.label)).toEqual(['Nhân viên', 'Danh mục', 'Quản trị']);
    expect(items[2].path).toBe('/admin/user-management');
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
    expect(menuFor(['ROLE_HR', 'ROLE_NHAN_VIEN']).map(m => m.key)).toEqual(['nhan-vien', 'danh-muc', 'ho-so-cua-toi']);
  });
});

describe('menuFor với hồ sơ nhân viên đã gắn', () => {
  it('vai trò khác có hồ sơ gắn tài khoản cũng thấy Hồ sơ của tôi', () => {
    expect(menuFor(['ROLE_HR'], true).map(m => m.key)).toEqual(['nhan-vien', 'danh-muc', 'ho-so-cua-toi']);
    expect(menuFor(['ROLE_ADMIN'], true).map(m => m.key)).toContain('ho-so-cua-toi');
  });

  it('chưa gắn hồ sơ và không phải NHAN_VIEN thì không có Hồ sơ của tôi', () => {
    expect(menuFor(['ROLE_KE_TOAN'], false).map(m => m.key)).toEqual(['nhan-vien']);
  });
});
