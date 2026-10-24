import { errorText } from 'app/entities/employee/detail/detail-api';

/**
 * Lỗi từ các trang lưu qua redux (phòng ban, hồ sơ nhân viên) đi qua notification-middleware,
 * vốn gọi translate(problem.message) trên file i18n/vi/*.json. Thiếu khóa thì toast hiện
 * "translation-not-found[...]". Các mã lỗi mới của nhánh nghỉ phép ném từ phòng ban, nhân viên,
 * hồ sơ tài liệu phải có câu tiếng Việt trong i18n.
 */
const viFiles = import.meta.glob<{ error?: Record<string, unknown> }>('../../i18n/vi/*.json', { eager: true, import: 'default' });

const viErrorKeys = (): Record<string, unknown> => {
  const merged: Record<string, unknown> = {};
  for (const json of Object.values(viFiles)) {
    if (json.error && typeof json.error === 'object') Object.assign(merged, json.error);
  }
  return merged;
};

const NEW_NON_LEAVE_KEYS: Record<string, string> = {
  departmentheadinactive: 'Trưởng phòng phải là nhân viên đang làm việc',
  annualleavedaysinvalid: 'Mức phép năm chỉ là 12, 14 hoặc 16 ngày',
  priorinsuranceyearsinvalid: 'Số năm đóng BHXH trước khi vào công ty không được âm',
  documentinuse: 'Hồ sơ này là giấy nghỉ ốm của một đơn nghỉ, không xóa được',
  employeearchived: 'Hồ sơ đã lưu trữ, khôi phục trước khi sửa',
  archivenotterminated: 'Chỉ lưu trữ được nhân viên đã nghỉ việc. Hãy cho nghỉ việc trước',
  alreadyarchived: 'Hồ sơ đã nằm trong vùng lưu trữ',
  notarchived: 'Hồ sơ chưa nằm trong vùng lưu trữ',
  archivenotetoolong: 'Ghi chú lưu trữ tối đa 500 ký tự',
  rehiredatebeforeleave: 'Ngày vào làm mới phải sau ngày làm việc cuối của lần trước',
  rehirenotterminated: 'Chỉ nhận làm lại được nhân viên đã nghỉ việc',
  rehiredaterequired: 'Cần ngày vào làm mới',
  rehiredepartmentinvalid: 'Phòng ban không tồn tại hoặc đã ngừng hoạt động',
  rehirepositiontoolong: 'Vị trí tối đa 100 ký tự',
  deleterequiressystemadmin: 'Chỉ tài khoản admin được xóa hẳn nhân viên',
  deletenotarchived: 'Chỉ xóa hẳn được hồ sơ đang ở vùng lưu trữ',
  deleteblocked: 'Chưa xóa hẳn được: người này còn trong kỳ lương chưa duyệt, bản nháp import đang làm hoặc lượt gửi phiếu lương đang chờ',
  deleteconfirmmismatch: 'Mã nhân viên xác nhận không khớp',
  deletereasonrequired: 'Hãy nhập lý do xóa hẳn',
  deletereasontoolong: 'Lý do tối đa 500 ký tự',
  holidaymonthlocked: 'Tháng này đã chốt công hoặc đã duyệt lương – không sửa ngày lễ',
  holidayimporterrors: 'File ngày lễ còn lỗi, sửa file rồi chọn lại',
  holidayfileinvalid: 'Không đọc được file; hãy chọn file Excel .xlsx có ngày lễ',
  deleteadminaccount: 'Không xóa được hồ sơ gắn tài khoản admin',
};

describe('mã lỗi mới của phòng ban, nhân viên, hồ sơ tài liệu', () => {
  it('có câu tiếng Việt trong i18n/vi (toast của redux)', () => {
    expect(Object.keys(viFiles).length).toBeGreaterThan(5);
    const keys = viErrorKeys();
    for (const [key, text] of Object.entries(NEW_NON_LEAVE_KEYS)) {
      expect(keys[key], `error.${key}`).toBe(text);
    }
  });

  it('có câu tiếng Việt trong errorText (các hộp thoại gọi axios trực tiếp)', () => {
    for (const [key, text] of Object.entries(NEW_NON_LEAVE_KEYS)) {
      expect(errorText({ response: { status: 400, data: { message: `error.${key}` } } })).toBe(text);
    }
  });
});
