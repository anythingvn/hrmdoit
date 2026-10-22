import { menuFor } from './menu-items';

describe('menuFor', () => {
  it('KE_TOAN thấy Nhân viên, Bảng lương và Tổng hợp nghỉ & làm thêm', () => {
    const items = menuFor(['ROLE_KE_TOAN']);
    expect(items.map(m => m.key)).toEqual(['tong-quan', 'nhan-vien', 'bang-luong', 'tong-hop-nghi', 'bao-cao']);
    expect(items[2].label).toBe('Bảng lương');
    expect(items[2].path).toBe('/bang-luong');
    expect(items[3]).toMatchObject({ label: 'Tổng hợp nghỉ & làm thêm', path: '/tong-hop-nghi' });
  });

  it('HR thấy Nhân viên, Hợp đồng & mẫu và Danh mục', () => {
    const items = menuFor(['ROLE_HR']);
    expect(items.map(m => m.key)).toEqual([
      'tong-quan',
      'nhan-vien',
      'duyet-don',
      'lich-nghi',
      'quan-ly-nghi-phep',
      'hop-dong',
      'tong-hop-nghi',
      'bao-cao',
      'danh-muc',
    ]);
    expect(items[5].label).toBe('Hợp đồng & mẫu');
    expect(items[5].path).toBe('/hop-dong');
    expect(items[8].path).toBe('/danh-muc/phong-ban');
  });

  it('ADMIN thấy đủ 11 mục', () => {
    const items = menuFor(['ROLE_ADMIN']);
    expect(items.map(m => m.key)).toEqual([
      'tong-quan',
      'nhan-vien',
      'duyet-don',
      'lich-nghi',
      'quan-ly-nghi-phep',
      'hop-dong',
      'bang-luong',
      'tong-hop-nghi',
      'bao-cao',
      'danh-muc',
      'quan-tri',
    ]);
    expect(items.map(m => m.label)).toEqual([
      'Tổng quan',
      'Nhân viên',
      'Duyệt đơn',
      'Lịch nghỉ',
      'Quản lý nghỉ phép',
      'Hợp đồng & mẫu',
      'Bảng lương',
      'Tổng hợp nghỉ & làm thêm',
      'Báo cáo',
      'Danh mục',
      'Quản trị',
    ]);
    expect(items[10].path).toBe('/admin/user-management');
  });

  it('BGD không thấy Hợp đồng & mẫu', () => {
    expect(menuFor(['ROLE_BGD']).map(m => m.key)).toEqual(['tong-quan', 'nhan-vien', 'duyet-don', 'lich-nghi', 'bao-cao']);
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
      'duyet-don',
      'lich-nghi',
      'quan-ly-nghi-phep',
      'hop-dong',
      'tong-hop-nghi',
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
      'duyet-don',
      'lich-nghi',
      'quan-ly-nghi-phep',
      'hop-dong',
      'tong-hop-nghi',
      'bao-cao',
      'danh-muc',
      'ho-so-cua-toi',
    ]);
    expect(menuFor(['ROLE_ADMIN'], true).map(m => m.key)).toContain('ho-so-cua-toi');
  });

  it('chưa gắn hồ sơ và không phải NHAN_VIEN thì không có Hồ sơ của tôi', () => {
    expect(menuFor(['ROLE_KE_TOAN'], false).map(m => m.key)).toEqual(['tong-quan', 'nhan-vien', 'bang-luong', 'tong-hop-nghi', 'bao-cao']);
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

describe('menuFor: Nghỉ phép và Duyệt đơn', () => {
  it('NHAN_VIEN dùng phần nghỉ phép thì có Nghỉ phép', () => {
    const items = menuFor(['ROLE_NHAN_VIEN'], true, { leaveApplicable: true });
    expect(items.map(m => m.key)).toEqual(['nghi-phep', 'ho-so-cua-toi']);
    expect(items[0]).toMatchObject({ label: 'Nghỉ phép', path: '/nghi-phep' });
  });

  it('THUC_TAP / DICH_VU (leaveApplicable = false) không có Nghỉ phép', () => {
    expect(menuFor(['ROLE_NHAN_VIEN'], true, { leaveApplicable: false }).map(m => m.key)).not.toContain('nghi-phep');
    expect(menuFor(['ROLE_NHAN_VIEN'], true).map(m => m.key)).not.toContain('nghi-phep');
  });

  it('người có isApprover có Duyệt đơn', () => {
    const items = menuFor(['ROLE_NHAN_VIEN'], true, { leaveApplicable: true, isApprover: true });
    expect(items.map(m => m.key)).toEqual(['nghi-phep', 'duyet-don', 'ho-so-cua-toi']);
    expect(items[1]).toMatchObject({ label: 'Duyệt đơn', path: '/duyet-don' });
    expect(menuFor(['ROLE_NHAN_VIEN'], true, { leaveApplicable: true }).map(m => m.key)).not.toContain('duyet-don');
  });

  it('HR, Quản trị, BGĐ luôn có Duyệt đơn; Kế toán thì không', () => {
    expect(menuFor(['ROLE_HR']).map(m => m.key)).toContain('duyet-don');
    expect(menuFor(['ROLE_ADMIN']).map(m => m.key)).toContain('duyet-don');
    expect(menuFor(['ROLE_BGD']).map(m => m.key)).toContain('duyet-don');
    expect(menuFor(['ROLE_KE_TOAN']).map(m => m.key)).not.toContain('duyet-don');
  });
});

describe('menuFor: Lịch nghỉ, Quản lý nghỉ phép, Tổng hợp', () => {
  it('trưởng nhóm / trưởng phòng (seesLeaveCalendar) có Lịch nghỉ', () => {
    const items = menuFor(['ROLE_NHAN_VIEN'], true, { leaveApplicable: true, isApprover: true, seesLeaveCalendar: true });
    expect(items.map(m => m.key)).toEqual(['nghi-phep', 'duyet-don', 'lich-nghi', 'ho-so-cua-toi']);
    expect(items[2]).toMatchObject({ label: 'Lịch nghỉ', path: '/lich-nghi' });
    expect(menuFor(['ROLE_NHAN_VIEN'], true, { leaveApplicable: true, isApprover: true }).map(m => m.key)).not.toContain('lich-nghi');
  });

  it('Kế toán không có Lịch nghỉ; chỉ HR, Quản trị có Quản lý nghỉ phép', () => {
    expect(menuFor(['ROLE_KE_TOAN']).map(m => m.key)).not.toContain('lich-nghi');
    expect(menuFor(['ROLE_BGD']).map(m => m.key)).not.toContain('quan-ly-nghi-phep');
    expect(menuFor(['ROLE_HR']).find(m => m.key === 'quan-ly-nghi-phep')).toMatchObject({ path: '/quan-ly-nghi-phep' });
    expect(menuFor(['ROLE_BGD']).map(m => m.key)).not.toContain('tong-hop-nghi');
  });
});
