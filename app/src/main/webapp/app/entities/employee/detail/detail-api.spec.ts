import { errorText } from './detail-api';

const err = (status: number, message?: string, detail?: string) => ({ response: { status, data: { message, detail } } });

describe('errorText', () => {
  it('có câu tiếng Việt cho các mã lỗi mới', () => {
    expect(errorText(err(400, 'error.departmentheadinactive'))).toBe('Trưởng phòng phải là nhân viên đang làm việc');
    expect(errorText(err(400, 'error.leaveperiodlocked'))).toContain('Kỳ lương');
    expect(errorText(err(400, 'error.leaveemployeeterminated'))).toContain('đã nghỉ việc');
    expect(errorText(err(400, 'error.leaveaccountingmonth'))).toBe('Tháng không hợp lệ');
    expect(errorText(err(400, 'error.attendanceself'))).toBe('Không ai gõ giờ cho chính mình');
    expect(errorText(err(400, 'error.attendancefuture'))).toBe('Không gõ giờ cho thời điểm chưa tới');
    expect(errorText(err(400, 'error.attendancemonthclosed'))).toContain('đã chốt');
    expect(errorText(err(400, 'error.attendanceautofilled'))).toContain('tự điền');
    expect(errorText(err(400, 'error.attendancetimeorder'))).toBe('Giờ ra phải sau giờ vào');
    expect(errorText(err(400, 'error.attendanceabsentwithtimes'))).toContain('vắng');
    expect(errorText(err(400, 'error.attendanceduplicateentry'))).toContain('một dòng');
  });

  it('403 tự điều chỉnh sổ của mình hiện câu riêng, 403 khác hiện câu chung', () => {
    expect(errorText(err(403, 'error.leaveselfadjust'))).toContain('chính mình');
    expect(errorText(err(403, 'error.http.403'))).toBe('Bạn không có quyền thực hiện thao tác này');
    expect(errorText(err(403))).toBe('Bạn không có quyền thực hiện thao tác này');
  });
});

describe('errorText — lưu trữ và xóa hẳn', () => {
  it('xóa hẳn bị chặn: hiện danh sách lý do từ server', () => {
    expect(errorText(err(400, 'error.deleteblocked', 'Chưa xóa hẳn được: kỳ lương 09/2026 chưa duyệt'))).toBe(
      'Chưa xóa hẳn được: kỳ lương 09/2026 chưa duyệt',
    );
    expect(errorText(err(400, 'error.deleteblocked'))).toContain('Chưa xóa hẳn được');
  });

  it('bộ giờ làm không hợp lệ: hiện câu cụ thể của server', () => {
    expect(errorText(err(400, 'error.attendancescheduleinvalid', 'Giờ nghỉ trưa phải nằm trong giờ làm'))).toBe(
      'Giờ nghỉ trưa phải nằm trong giờ làm',
    );
    expect(errorText(err(400, 'error.attendancescheduleinvalid'))).toContain('Bộ giờ làm không hợp lệ');
  });

  it('403 không phải admin: câu riêng', () => {
    expect(errorText(err(403, 'error.deleterequiressystemadmin'))).toBe('Chỉ tài khoản admin được xóa hẳn nhân viên');
  });
});
