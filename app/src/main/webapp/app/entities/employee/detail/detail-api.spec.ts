import { errorText } from './detail-api';

const err = (status: number, message?: string, detail?: string) => ({ response: { status, data: { message, detail } } });

describe('errorText', () => {
  it('có câu tiếng Việt cho các mã lỗi mới', () => {
    expect(errorText(err(400, 'error.departmentheadinactive'))).toBe('Trưởng phòng phải là nhân viên đang làm việc');
    expect(errorText(err(400, 'error.leaveperiodlocked'))).toContain('Kỳ lương');
    expect(errorText(err(400, 'error.leaveemployeeterminated'))).toContain('đã nghỉ việc');
    expect(errorText(err(400, 'error.leaveaccountingmonth'))).toBe('Tháng không hợp lệ');
  });

  it('403 tự điều chỉnh sổ của mình hiện câu riêng, 403 khác hiện câu chung', () => {
    expect(errorText(err(403, 'error.leaveselfadjust'))).toContain('chính mình');
    expect(errorText(err(403, 'error.http.403'))).toBe('Bạn không có quyền thực hiện thao tác này');
    expect(errorText(err(403))).toBe('Bạn không có quyền thực hiện thao tác này');
  });
});
