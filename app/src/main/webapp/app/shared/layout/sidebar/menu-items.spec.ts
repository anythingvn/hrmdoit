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
