import { createContext, use } from 'react';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import { ContractTypeCode } from 'app/shared/model/employee.model';

/** Kiểu dữ liệu và tiện ích dùng chung cho trang chi tiết nhân viên. */

export type SalaryBasis = 'NET' | 'GROSS';
export type ContractStatus = 'NHAP' | 'DA_KY' | 'DA_KET_THUC' | 'DA_THANH_LY';
export type WorkLocationType = 'VAN_PHONG' | 'KHACH_HANG' | 'TU_XA' | 'CONG_TAC';
export type TerminationType = 'TU_NGHI' | 'HET_HAN_HD' | 'KHONG_DAT_THU_VIEC' | 'CONG_TY_CHAM_DUT' | 'KHAC';

export interface ICurrentSalary {
  amount: number;
  basis: SalaryBasis;
  grossAmount?: number | null;
  effectiveDate?: string | null;
  source?: string | null;
}

export interface IContract {
  id?: number;
  contractNo?: string | null;
  type?: ContractTypeCode;
  termMonths?: number | null;
  startDate?: string;
  endDate?: string | null;
  position?: string | null;
  salary?: number | null;
  salaryBasis?: SalaryBasis | null;
  grossAmount?: number | null;
  raisePolicy?: string | null;
  status?: ContractStatus;
  /** Kết quả thử việc (chỉ HĐ thử việc). */
  probationResult?: ProbationResult | null;
  probationResultDate?: string | null;
  employee?: { id: number };
}

export type ProbationResult = 'DAT' | 'KHONG_DAT';

export const PROBATION_RESULT_LABEL: Record<ProbationResult, string> = { DAT: 'Đạt', KHONG_DAT: 'Không đạt' };

export interface ISalaryChange {
  id?: number;
  effectiveDate?: string;
  amount?: number;
  basis?: SalaryBasis;
  grossAmount?: number | null;
  reviewCycleMonths?: number | null;
  note?: string | null;
  /** Hẹn xét lại lương (thay cho mốc tính theo chu kỳ). */
  nextReviewOverride?: string | null;
  employee?: { id: number };
}

export interface IAllowance {
  id?: number;
  name?: string;
  amountPerMonth?: number;
  fromDate?: string;
  toDate?: string | null;
  employee?: { id: number };
}

export interface ISegment {
  /** id giai đoạn (WorkAssignment); null/không có khi là khoảng trống. */
  id?: number | null;
  from: string;
  to?: string | null;
  type?: WorkLocationType;
  unit?: string | null;
  label?: string | null;
}

/** Giai đoạn làm việc bắt đầu sau ngày làm việc cuối (chặn việc cho nghỉ). */
export interface ITerminationBlocker {
  id: number;
  type?: WorkLocationType;
  unit?: string | null;
  fromDate: string;
  toDate?: string | null;
  started: boolean;
  /** Giai đoạn "quay về" hệ thống tự tạo, chưa bắt đầu. */
  autoReturn: boolean;
}

export interface IDocument {
  id: number;
  fileName: string;
  contentType?: string;
  sizeBytes?: number;
  uploadedBy?: string | null;
  uploadedAt?: string;
}

export type GeneratedFileKind = 'DOCX' | 'PDF' | 'SIGNED';

/** Một file văn bản của hợp đồng / quyết định. */
export interface IGeneratedDocument {
  id: number;
  kind: GeneratedFileKind;
  fileName: string;
  templateKind?: string | null;
  templateVersion?: number | null;
  sourceDocumentId?: number | null;
  createdBy?: string | null;
  createdAt: string;
}

/** Kết quả sinh văn bản: luôn có .docx; PDF có thể chưa tạo được (pdfError). */
export interface IGenerationResult {
  contractNo?: string | null;
  docx: IGeneratedDocument;
  pdf?: IGeneratedDocument | null;
  pdfError?: string | null;
}

export const FILE_KIND_LABEL: Record<GeneratedFileKind, string> = {
  DOCX: 'Word',
  PDF: 'PDF',
  SIGNED: 'Bản đã ký',
};

export interface IHistoryEntry {
  at: string;
  login?: string | null;
  entity: string;
  action: string;
  changes: { field: string; before?: string | null; after?: string | null }[];
}

export const CONTRACT_STATUS_LABEL: Record<ContractStatus, string> = {
  NHAP: 'Nháp',
  DA_KY: 'Đã ký',
  DA_KET_THUC: 'Đã kết thúc',
  DA_THANH_LY: 'Đã thanh lý',
};

export const LOCATION_TYPE_LABEL: Record<WorkLocationType, string> = {
  VAN_PHONG: 'Văn phòng',
  KHACH_HANG: 'Tại khách hàng',
  TU_XA: 'Làm từ xa',
  CONG_TAC: 'Công tác / biệt phái',
};

export const TERMINATION_TYPE_LABEL: Record<TerminationType, string> = {
  TU_NGHI: 'Tự nghỉ',
  HET_HAN_HD: 'Hết hạn hợp đồng',
  KHONG_DAT_THU_VIEC: 'Không đạt thử việc',
  CONG_TY_CHAM_DUT: 'Công ty chấm dứt',
  KHAC: 'Khác',
};

const ERROR_TEXT: Record<string, string> = {
  'error.numberingdocinvalid': 'Tiền tố và mã loại văn bản chỉ gồm chữ in hoa, số, gạch ngang, tối đa 10 ký tự, không có dấu "/".',
  'error.numberingdocduplicate': 'Mã các loại văn bản phải khác nhau.',
  'error.numberingcodeinvalid': 'Tiền tố mã nhân viên chỉ gồm chữ A–Z và số, bắt đầu bằng chữ, tối đa 10 ký tự.',
  'error.numberingcodeduplicate': 'Ba tiền tố mã nhân viên phải khác nhau.',
  'error.numberingcodeoverlap': 'Tiền tố mã nhân viên không được là phần đầu của tiền tố khác (vd ABC và ABCNV).',
  'error.defaultpasswordinvalid': 'Mật khẩu mặc định phải có từ 8 đến 100 ký tự.',
  'error.backupkeyoauth':
    'Đây là OAuth client ID, không phải khóa service account. Vào IAM & Admin → Service Accounts → Keys để tạo khóa JSON.',
  'error.backupkeyinvalid': 'File không phải khóa service account hợp lệ.',
  'error.backupkeyrevoked': 'Khóa này đã bị xóa hoặc vô hiệu hóa trên Google Cloud. Hãy tạo khóa mới.',
  'error.backupdriveapioff': 'Chưa bật Google Drive API cho project của service account. Vào APIs & Services → Library để bật.',
  'error.backupdrivenotfound': 'Không thấy Shared Drive. Kiểm tra link, và đã thêm email service account làm thành viên chưa.',
  'error.backupdriverole': 'Service account cần vai trò Người quản lý nội dung để thêm và dọn bản cũ.',
  'error.backupdrivelink': 'Link Shared Drive không hợp lệ.',
  'error.backupgoogleunreachable': 'Máy chủ không kết nối được tới Google. Kiểm tra mạng rồi thử lại.',
  'error.backupagerecipient': 'Khóa mã hóa không hợp lệ (phải bắt đầu bằng age1).',
  'error.backupkeynotsaved': 'Hãy xác nhận đã cất file khóa bí mật.',
  'error.backupnokey': 'Hãy tải lên file khóa service account.',
  'error.backupconfigdir': 'Máy chủ chưa có thư mục cấu hình sao lưu. Cập nhật docker-compose.yml và khởi động lại.',
  'error.badterm': 'Thời hạn hợp đồng không nằm trong danh sách cho phép',
  'error.indefinitehasend': 'Hợp đồng không xác định thời hạn không có ngày kết thúc',
  'error.endbeforestart': 'Ngày kết thúc trước ngày bắt đầu',
  'error.alreadyterminated': 'Nhân viên đã nghỉ việc',
  'error.attendancescheduleclosedmonth': 'Bảng công tháng có ngày hiệu lực này đã chốt, không đổi được bộ giờ làm',
  'error.attendancescheduleinvalid':
    'Bộ giờ làm không hợp lệ: giờ ra phải sau giờ vào, nghỉ trưa nằm trong giờ làm, mốc nửa ngày lớn hơn số phút muộn cho phép',
  'error.attendancescheduledeletepast': 'Chỉ xóa được bộ giờ chưa tới ngày hiệu lực; muốn đổi giờ thì thêm bộ giờ mới',
  'error.attendancescheduleinvalidsite': 'Chỉ nơi làm việc loại khách hàng có bộ giờ riêng và trưởng nhóm onsite',
  'error.attendanceself': 'Không ai gõ giờ cho chính mình',
  'error.attendancefuture': 'Không gõ giờ cho thời điểm chưa tới',
  'error.attendancemonthclosed': 'Bảng công tháng này đã chốt, không sửa được giờ',
  'error.attendanceautofilled': 'Ngày này đã tự điền (nghỉ, lễ, công tác…), không gõ giờ',
  'error.attendancetimeorder': 'Giờ ra phải sau giờ vào',
  'error.attendancetimeinvalid': 'Chọn ngày chấm công',
  'error.attendanceabsentwithtimes': 'Đã đánh dấu vắng thì không nhập giờ vào, giờ ra',
  'error.attendanceduplicateentry': 'Mỗi người chỉ được một dòng trong một lần lưu',
  'error.attendanceunresolved': 'Bảng công còn ô thiếu giờ / chưa nhập / chờ duyệt; xem danh sách rồi sửa hoặc vẫn chốt',
  'error.attendancealreadyclosed': 'Bảng công tháng này đã chốt',
  'error.attendancemonthnotover': 'Tháng chưa hết, chưa chốt được bảng công',
  'error.attendancereasonrequired': 'Cần nhập lý do mở lại bảng công',
  'error.attendancenotclosed': 'Bảng công tháng này chưa chốt',
  'error.attendancepayrollapproved':
    'Kỳ lương tháng này đã duyệt, bảng công tháng này khóa (không sửa giờ, không mở lại chốt); điều chỉnh ở kỳ sau',
  'error.attendancenotapplicable': 'Chỉ nhân viên đang làm việc mới tự chấm công',
  'error.attendanceconsentrequired': 'Cần đồng ý thông báo thu thập vị trí trước khi chấm công',
  'error.attendancepunchinvalid': 'Lần chấm không hợp lệ: chọn Vào hoặc Ra; vị trí cần đủ vĩ độ, kinh độ hợp lệ và sai số không âm',
  'error.attendanceselfreview': 'Không ai tự duyệt lần chấm của chính mình',
  'error.attendancerejectnoterequired': 'Cần nhập lý do từ chối',
  'error.worksitehaspunches': 'Địa điểm đã có lần chấm công, không xóa được; hãy ngừng sử dụng',
  'error.attendancedecisionnotetoolong': 'Lý do tối đa 500 ký tự',
  'error.attendancepunchnotpending': 'Lần chấm này không còn chờ duyệt',
  'error.attendancenoticesettinginvalid': 'Nội dung thông báo tối đa 10.000 ký tự; số ngày giữ tọa độ từ 1 đến 3650',
  'error.explanationdayrequired': 'Chọn ngày giải trình',
  'error.explanationfuture': 'Chỉ giải trình cho ngày đã qua, hoặc hôm nay với giờ đã qua',
  'error.explanationtimerequired': 'Nhập giờ vào hoặc giờ ra đề xuất',
  'error.explanationtimeorder': 'Giờ vào phải trước giờ ra',
  'error.explanationreasonrequired': 'Cần nhập lý do giải trình',
  'error.explanationreasontoolong': 'Lý do tối đa 500 ký tự',
  'error.explanationpending': 'Ngày này đã có giải trình đang chờ duyệt; rút giải trình đó trước khi gửi lại',
  'error.explanationselfreview': 'Không ai tự duyệt giải trình của chính mình',
  'error.explanationnotpending': 'Giải trình này không còn chờ duyệt',
  'error.employeearchived': 'Hồ sơ đã lưu trữ, khôi phục trước khi sửa',
  'error.archivenotterminated': 'Chỉ lưu trữ được nhân viên đã nghỉ việc. Hãy cho nghỉ việc trước',
  'error.archivependingovertime':
    'Nhân viên còn đơn làm thêm giờ đang chờ duyệt hoặc chờ xác nhận giờ. Xử lý xong các đơn này trước khi lưu trữ',
  'error.archivependingleave': 'Nhân viên còn đơn nghỉ đang chờ duyệt hoặc chờ duyệt hủy. Xử lý xong các đơn này trước khi lưu trữ',
  'error.accountemployeearchived': 'Tài khoản gắn với hồ sơ đã lưu trữ. Khôi phục hồ sơ trước khi mở lại tài khoản',
  'error.alreadyarchived': 'Hồ sơ đã nằm trong vùng lưu trữ',
  'error.notarchived': 'Hồ sơ chưa nằm trong vùng lưu trữ',
  'error.archivenotetoolong': 'Ghi chú lưu trữ tối đa 500 ký tự',
  'error.rehiredatebeforeleave': 'Ngày vào làm mới phải sau ngày làm việc cuối của lần trước',
  'error.rehirenotterminated': 'Chỉ nhận làm lại được nhân viên đã nghỉ việc',
  'error.rehiredaterequired': 'Cần ngày vào làm mới',
  'error.rehiredepartmentinvalid': 'Phòng ban không tồn tại hoặc đã ngừng hoạt động',
  'error.rehirepositiontoolong': 'Vị trí tối đa 100 ký tự',
  'error.deleterequiressystemadmin': 'Chỉ tài khoản admin được xóa hẳn nhân viên',
  'error.deletenotarchived': 'Chỉ xóa hẳn được hồ sơ đang ở vùng lưu trữ',
  'error.deleteblocked':
    'Chưa xóa hẳn được: người này còn trong kỳ lương chưa duyệt, bản nháp import đang làm hoặc lượt gửi phiếu lương đang chờ',
  'error.deleteconfirmmismatch': 'Mã nhân viên xác nhận không khớp',
  'error.deletereasonrequired': 'Hãy nhập lý do xóa hẳn',
  'error.deletereasontoolong': 'Lý do tối đa 500 ký tự',
  'error.deleteadminaccount': 'Không xóa được hồ sơ gắn tài khoản admin',
  'error.terminatebeforeassignment':
    'Có giai đoạn làm việc (đã bắt đầu hoặc đã lên kế hoạch) bắt đầu sau ngày làm việc cuối. Cho nghỉ việc không xóa dữ liệu: kiểm tra lại ngày, hoặc xóa / sửa giai đoạn đó trong "Lịch sử công tác" trước khi cho nghỉ việc.',
  'error.terminationscheduled': 'Nhân viên đã có lịch nghỉ việc. Hủy lịch đó trước khi đặt lịch mới.',
  'error.noscheduledtermination': 'Nhân viên không có lịch nghỉ việc nào đang chờ',
  'error.nothingtoundo': 'Không có lần cho nghỉ việc nào để hoàn tác',
  'error.undoconflict': 'Không hoàn tác được vì dữ liệu đã được sửa sau khi cho nghỉ việc',
  'error.selftermination': 'Không thể tự cho mình nghỉ việc (tài khoản đang dùng sẽ bị khóa)',
  'error.lastadmin': 'Đây là tài khoản quản trị cuối cùng đang hoạt động. Tạo hoặc mở một tài khoản quản trị khác trước khi cho nghỉ việc.',
  'error.adminaccountrequiresadmin': 'Nhân viên này có tài khoản Quản trị — chỉ Quản trị được cho nghỉ việc.',
  'error.employeeterminated': 'Nhân viên đã nghỉ việc: không tạo tài khoản mới, chỉ gắn được tài khoản đang khóa.',
  'error.pendingtermination':
    'Nhân viên có lịch nghỉ việc; không thêm được giai đoạn làm việc hoặc hợp đồng đã ký bắt đầu sau ngày làm việc cuối. Hủy lịch trước.',
  'error.terminationdatebeforelastday': 'Ngày nghỉ việc không được trước ngày làm việc cuối',
  'error.assignmentnotfound': 'Không tìm thấy giai đoạn làm việc này của nhân viên',
  'error.terminatebeforecontract': 'Ngày làm việc cuối trước ngày bắt đầu của hợp đồng đã ký',
  'error.lastworkingdayrequired': 'Cần ngày làm việc cuối cùng',
  'error.terminationtyperequired': 'Cần chọn hình thức nghỉ việc',
  'error.filetypenotallowed': 'Chỉ nhận file PDF, JPEG hoặc PNG',
  'error.filetypemismatch': 'Loại file không khớp với đuôi file',
  'error.filecontentmismatch': 'Nội dung file không đúng định dạng',
  'error.filenamemissing': 'Thiếu tên file',
  'error.idnotfound': 'Không tìm thấy dữ liệu',
  'error.filemissing': 'Không tìm thấy file trên máy chủ',
  'error.badsort': 'Không sắp xếp được theo cột này',
  'error.useralreadylinked': 'Tài khoản này đã gắn với hồ sơ nhân viên khác',
  'error.employeehasaccount': 'Hồ sơ đã có tài khoản; hãy bỏ gắn trước',
  'error.userexists': 'Tên đăng nhập đã được dùng',
  'error.idnumberrequired': 'Nhập số CCCD trong hồ sơ trước khi tạo tài khoản',
  'error.idnumberinvalid': 'Số CCCD phải gồm 12 chữ số (hoặc 9 chữ số CMND cũ)',
  'error.loginexists': 'Số CCCD này đã là tên đăng nhập của một tài khoản khác',
  'error.systemaccount': 'Tài khoản quản trị hệ thống "admin" không gắn vào hồ sơ nhân viên',
  'error.loginfromidnumber': 'Tên đăng nhập là số CCCD — sửa số CCCD trên hồ sơ nhân viên để đổi',
  'error.idnumberambiguous': 'Có nhiều hồ sơ đang làm việc cùng số CCCD này; tạo tài khoản từ tab Tài khoản của đúng hồ sơ',
  'error.defaultpassword': 'Mật khẩu mới không được trùng mật khẩu mặc định',
  'error.samepassword': 'Mật khẩu mới phải khác mật khẩu đang dùng',
  'error.usernotfound': 'Không tìm thấy tài khoản',
  'error.badauthority': 'Vai trò không hợp lệ',
  'error.cannotdeletesigned': 'Chỉ xóa được hợp đồng nháp; hợp đồng đã ký là dữ liệu lịch sử',
  'error.cannotmoverecord': 'Không được chuyển bản ghi sang nhân viên khác',
  'error.invalidmonth': 'Kỳ lương không hợp lệ (định dạng MM/yyyy)',
  'error.negativeamount': 'Số tiền không được âm',
  'error.notemplate': 'Chưa có mẫu cho loại hợp đồng này — tải mẫu ở Hợp đồng & mẫu',
  'error.nodocx': 'Chưa có văn bản Word — bấm Tạo văn bản trước',
  'error.signednotpdf': 'Bản đã ký phải là file PDF',
  'error.notdraft': 'Chỉ hợp đồng nháp mới đánh dấu đã ký được',
  'error.probationresultnothdtv': 'Kết quả thử việc chỉ ghi trên hợp đồng thử việc',
  'error.mailnotconfigured': 'Chưa cấu hình máy chủ gửi email (SMTP) — vào Cài đặt → Email',
  'error.payslipnotapproved': 'Kỳ lương chưa được duyệt nên chưa gửi hoặc xem phiếu lương được',
  'error.pdfunavailable': 'Dịch vụ PDF đang không chạy, chưa tạo được phiếu lương — thử lại sau',
  'error.departmentheadinactive': 'Trưởng phòng phải là nhân viên đang làm việc',
  'error.annualleavedaysinvalid': 'Mức phép năm chỉ là 12, 14 hoặc 16 ngày',
  'error.priorinsuranceyearsinvalid': 'Số năm đóng BHXH trước khi vào công ty không được âm',
  'error.documentinuse': 'Hồ sơ này là giấy nghỉ ốm của một đơn nghỉ, không xóa được',
  // Nghỉ phép & đơn từ
  'error.insufficientbalance': 'Không đủ số dư cho đơn này',
  'error.leavealreadyposted': 'Đơn đã được trừ quỹ',
  'error.leavealreadyrefunded': 'Đơn đã được hoàn quỹ',
  'error.leaveaccountingmonth': 'Tháng không hợp lệ',
  'error.leaveamountinvalid': 'Số điều chỉnh phải khác 0 và có tối đa 2 chữ số thập phân',
  'error.leavebucketexpired': 'Phép năm trước đã hết hạn; điều chỉnh vào phép năm nay',
  'error.leavecalendarmonth': 'Tháng không hợp lệ',
  'error.leavecalendartype': 'Loại nghỉ không hợp lệ',
  'error.leavecancelpast': 'Ngày nghỉ đã bắt đầu hoặc đã qua; liên hệ HR để hủy đơn',
  'error.leaveemployeeterminated': 'Nhân viên đã nghỉ việc, sổ phép đã chốt — không điều chỉnh được nữa',
  'error.leavedatesinvalid': 'Khoảng ngày nghỉ không hợp lệ (đến ngày phải từ ngày bắt đầu trở đi)',
  'error.leaveexpiryinpast': 'Ngày hết hạn phép năm trước không được trước hôm nay',
  'error.leavenotapplicable': 'Bạn không thuộc diện dùng phần nghỉ phép (chỉ dành cho nhân viên chính thức)',
  'error.leavenoterequired': 'Phải ghi lý do',
  'error.leavenoworkingdays': 'Khoảng ngày đã chọn không có ngày làm việc nào',
  'error.leaveopeninginvalid': 'File số dư đầu kỳ còn lỗi; xem lại kết quả kiểm tra',
  'error.leaveopeninglayout': 'File không đúng mẫu số dư đầu kỳ',
  'error.leaveopeningnotxlsx': 'Chỉ nhận file Excel .xlsx lưu từ mẫu số dư đầu kỳ',
  'error.leavepartinvalid': 'Buổi nghỉ không hợp lệ: đơn nhiều ngày chỉ nghỉ chiều ở ngày đầu, sáng ở ngày cuối',
  'error.leaveperiodlocked': 'Kỳ lương của tháng có ngày nghỉ này đã được duyệt, không thay đổi được đơn nghỉ',
  'error.leavepasttoofar': 'Ngày đã qua quá lâu, không xin được nữa',
  'error.leaverequeststatus': 'Đơn không còn ở trạng thái cho phép thao tác này — tải lại trang',
  'error.leaveselfadjust': 'Không tự điều chỉnh hoặc nhập số dư sổ phép của chính mình — nhờ HR khác làm',
  'error.leavesettinginvalid': 'Cài đặt nghỉ phép không hợp lệ',
  'error.leavesickdocnotallowed': 'Chỉ đơn nghỉ ốm mới đính kèm giấy chứng nhận nghỉ hưởng BHXH',
  'error.leavetypeinvalid': 'Chọn loại đơn',
  'error.overlap': 'Trùng ngày với một đơn nghỉ khác đang chờ hoặc đã duyệt',
  'error.overtimecompensationrequired': 'Chọn hình thức: trả tiền hoặc nghỉ bù',
  'error.overtimeconfirmearly': 'Chỉ xác nhận giờ thực tế sau khi đã làm xong',
  'error.overtimehoursinvalid': 'Số giờ thực tế phải lớn hơn 0 và không quá 24 giờ',
  'error.overtimelimit': 'Vượt giới hạn làm thêm giờ (40 giờ/tháng, 200 giờ/năm)',
  'error.overtimeoverlap': 'Trùng giờ với một đơn làm thêm khác',
  'error.overtimeperiodlocked': 'Kỳ lương của tháng này đã được duyệt, không thay đổi được đơn làm thêm giờ',
  'error.overtimestatus': 'Đơn không còn ở trạng thái cho phép thao tác này — tải lại trang',
  'error.overtimeterminated': 'Nhân viên đã nghỉ việc, làm thêm giờ chỉ được trả tiền',
  'error.overtimetimeinvalid': 'Giờ bắt đầu và kết thúc không hợp lệ',
  'error.personalleaveexceeded': 'Vượt số ngày nghỉ việc riêng có lương của lý do đã chọn',
  'error.personalreasonrequired': 'Chọn lý do nghỉ việc riêng',
  'error.companynamerequired': 'Cần nhập tên công ty',
  'error.companysettinginvalid':
    'Thông tin công ty quá dài: tên công ty tối đa 255 ký tự, tên viết tắt 30, khẩu hiệu 100, người đại diện và chức danh 100',
  'error.logotoolarge': 'Logo tối đa 1 MB',
  'error.logoinvalidtype': 'Logo phải là ảnh PNG, JPG hoặc SVG',
  'error.worksitelocationinvalid': 'Cần nhập cả vĩ độ và kinh độ (vĩ độ -90..90, kinh độ -180..180) hoặc để trống cả hai',
  'error.worksiteradiusinvalid': 'Bán kính chấm công từ 20 đến 5000 mét',
  'error.holidaymonthlocked': 'Tháng này đã chốt công hoặc đã duyệt lương – không sửa ngày lễ',
  'error.rulemilestoneused': 'Mốc luật đã dùng cho bảng lương (kỳ đã duyệt hoặc phiên bản đã xác nhận) nên không sửa, không xóa',
  'error.rulemilestonelast': 'Không xóa được mốc luật duy nhất còn lại',
  'error.templatelimit': 'Đã có 5 mẫu, hãy xoá bớt mẫu không cần',
  'error.templateactive': 'Không xóa được mẫu đang dùng; hãy chọn dùng mẫu khác trước',
  'error.holidayimporterrors': 'File ngày lễ còn lỗi, sửa file rồi chọn lại',
  'error.holidayfileinvalid': 'Không đọc được file; hãy chọn file Excel .xlsx có ngày lễ',
  'error.holidayyearinvalid': 'Năm phải trong khoảng 1900–2200',
  'error.worksiteipinvalid': 'Dải IP không hợp lệ: mỗi dòng một địa chỉ IPv4/IPv6 hoặc CIDR (ví dụ 113.190.1.10 hoặc 10.0.0.0/8)',
  'error.calcsettinglocked': 'Bộ thông số này đã dùng cho kỳ đã duyệt, hãy thêm bộ mới.',
  'error.calcsettinginvalid': 'Thông số tính lương không hợp lệ.',
  'error.calcdraftclosed': 'Bản tính này đã xác nhận hoặc đã hủy.',
  'error.calcnotready': 'Bản tính còn lỗi hoặc chưa xác nhận cảnh báo.',
  'error.calcfieldinvalid': 'Ô này không sửa được theo cách đã chọn.',
  'error.payrollattendancenotclosed': 'Hãy chốt bảng công tháng này trước khi tính lương.',
  'error.payrollrulesmissing': 'Chưa khai báo mức luật định cho kỳ này.',
};

/** Mã lỗi mà `detail` của server cụ thể hơn câu chung trong ERROR_TEXT. */
const DETAIL_FIRST = new Set([
  'error.undoconflict',
  'error.holidaymonthlocked',
  'error.holidayyearinvalid',
  'error.deleteblocked',
  'error.attendancescheduleinvalid',
  'error.attendanceunresolved',
  'error.rulemilestoneused',
  'error.backupdrivenotfound',
  'error.backupdriveapioff',
  'error.payrollattendancenotclosed',
  'error.payrollrulesmissing',
  'error.calcfieldinvalid',
]);

/** Mã lỗi 403 có câu riêng (không phải thiếu quyền chung chung). */
const FORBIDDEN_WITH_REASON = new Set(['error.leaveselfadjust', 'error.deleterequiressystemadmin']);

/** Câu báo lỗi tiếng Việt từ lỗi axios: theo mã lỗi đã biết, rồi `detail` của server, rồi câu chung. */
export const errorText = (err: any, fallback = 'Không lưu được, vui lòng thử lại'): string => {
  const res = err?.response;
  if (res?.status === 413) return 'File vượt quá dung lượng cho phép (20 MB)';
  const key: string | undefined = res?.data?.message;
  // 403 có mã lỗi nghiệp vụ riêng (vd tự điều chỉnh sổ phép của mình): câu cụ thể; còn lại câu chung.
  if (res?.status === 403) return key && FORBIDDEN_WITH_REASON.has(key) ? ERROR_TEXT[key] : 'Bạn không có quyền thực hiện thao tác này';
  // Hoàn tác bị từ chối: server liệt kê những gì đã bị sửa trong `detail`; xóa hẳn bị chặn: server liệt kê lý do;
  // bộ giờ làm không hợp lệ: server nói rõ sai ở đâu.
  if (key && DETAIL_FIRST.has(key) && res?.data?.detail) return res.data.detail;
  if (key && key !== 'error.assignmentconflict' && ERROR_TEXT[key]) return ERROR_TEXT[key];
  return res?.data?.detail ?? fallback;
};

/** Ngày kết thúc HĐ có thời hạn = ngày bắt đầu + số tháng − 1 ngày (cuối tháng thì lùi về ngày cuối như Java plusMonths). */
export const contractEnd = (startIso: string, months: number): string => {
  const [y, m, d] = startIso.split('-').map(Number);
  const totalMonth = m - 1 + months;
  const ty = y + Math.floor(totalMonth / 12);
  const tm = totalMonth % 12;
  const lastDay = new Date(Date.UTC(ty, tm + 1, 0)).getUTCDate();
  const end = new Date(Date.UTC(ty, tm, Math.min(d, lastDay)) - 86400000);
  return end.toISOString().slice(0, 10);
};

/** "Trần Minh Quân" thành "MQ". */
export const initials = (name?: string): string => {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  return parts
    .slice(-2)
    .map(p => p[0].toUpperCase())
    .join('');
};

/**
 * Hồ sơ đang xem chỉ đọc (đã lưu trữ): các tab bên trong trang chi tiết ẩn mọi nút sửa. Server vẫn chặn (400 {@code employeearchived}).
 */
export const EmployeeReadOnlyContext = createContext(false);

/**
 * Quyền theo vai trò của người đang đăng nhập. {@code canManage}: Quản trị/HR theo vai trò (xem các mục chỉ dành cho họ, kể cả trên
 * hồ sơ đã lưu trữ); {@code canWrite}: được sửa — false trong hồ sơ chỉ đọc ({@code readOnly}).
 */
export const useRoles = () => {
  const authorities: string[] = useAppSelector(state => state.authentication.account.authorities ?? []);
  const readOnly = use(EmployeeReadOnlyContext);
  const canManage = hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]);
  return {
    authorities,
    readOnly,
    canManage,
    canWrite: !readOnly && canManage,
    canSeeSensitive: hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR, AUTHORITIES.KE_TOAN]),
    sensitiveFull: hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]),
    /** Bảng lương: chỉ Quản trị, Kế toán. */
    canSeePayroll: hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.KE_TOAN]),
  };
};
