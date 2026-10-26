import axios from 'axios';

import { IWorkSite } from 'app/shared/model/work-site.model';

/** Cài đặt công ty ({@code GET/PUT /api/company-setting}); người đại diện chỉ có ở đây, không có trong branding công khai. */
export interface ICompanySetting {
  companyName: string;
  shortName?: string | null;
  tagline?: string | null;
  representativeName?: string | null;
  representativeTitle?: string | null;
  logoVersion?: number | null;
}

export type CompanySettingInput = Omit<ICompanySetting, 'logoVersion'>;

/** Cài đặt tự chấm công ({@code /api/company-setting/attendance-notice}); {@code noticeVersion} do máy chủ tăng khi nội dung đổi. */
export interface IAttendanceNotice {
  noticeText: string | null;
  noticeVersion?: number | null;
  locationRetentionDays: number;
}

/** Mã loại văn bản và tiền tố mã nhân viên ({@code GET/PUT /api/company-setting/numbering}); GET trả giá trị hiệu lực, PUT null/trống = về mặc định. */
export interface INumberingValues {
  documentPrefix: string | null;
  documentCodes: Record<string, string | null>;
  staffCodePrefix: string | null;
  internCodePrefix: string | null;
  serviceCodePrefix: string | null;
}

export interface INumbering extends INumberingValues {
  defaults: INumberingValues;
}

/** Mật khẩu mặc định cho tài khoản mới; {@code source}: WEB (cài trên web), ENV (file .env máy chủ), DEFAULT (mặc định cài sẵn). */
export interface IDefaultPassword {
  defaultPassword: string;
  source: 'WEB' | 'ENV' | 'DEFAULT';
}

export const MAX_LOGO_BYTES = 1048576;
const LOGO_TYPES = ['image/png', 'image/jpeg', 'image/svg+xml'];

/** Giới hạn độ dài các trường, khớp với máy chủ. */
export const COMPANY_LIMITS = { companyName: 255, shortName: 30, tagline: 100, representativeName: 100, representativeTitle: 100 };

/** Kiểm tra file logo trước khi gửi (máy chủ kiểm tra lại cả byte đầu file). */
export const logoFileError = (file: File): string | null => {
  if (file.size > MAX_LOGO_BYTES) return 'Logo tối đa 1 MB';
  if (!LOGO_TYPES.includes(file.type)) return 'Logo phải là ảnh PNG, JPG hoặc SVG';
  return null;
};

export const roundCoord = (n: number) => Math.round(n * 1e6) / 1e6;

export const mapsUrl = (lat: number, lng: number) => `https://www.google.com/maps?q=${lat},${lng}`;

/** "21,5" hoặc "21.5" → số; trống → null; sai → NaN. */
export const parseCoord = (raw: string): number | null => {
  const t = raw.trim().replace(',', '.');
  if (!t) return null;
  return /^-?\d+(\.\d+)?$/.test(t) ? Number(t) : NaN;
};

/** Câu báo khi trình duyệt không lấy được vị trí, theo {@code GeolocationPositionError.code}. */
export const geoErrorText = (code: number): string => {
  switch (code) {
    case 1:
      return 'Trình duyệt không cho lấy vị trí. Hãy cho phép trang này dùng vị trí trong cài đặt trình duyệt rồi thử lại.';
    case 2:
      return 'Không xác định được vị trí: thiết bị chưa bật định vị hoặc không có tín hiệu. Bật định vị rồi thử lại.';
    case 3:
      return 'Quá 15 giây vẫn chưa lấy được vị trí. Thử lại ở chỗ thoáng hoặc bật định vị chính xác cao.';
    default:
      return 'Không lấy được vị trí hiện tại.';
  }
};

export const NOT_SECURE_TEXT =
  'Lấy vị trí cần kết nối HTTPS (hoặc localhost). Mở trang bằng địa chỉ https:// rồi thử lại, hoặc nhập toạ độ tay.';

/** Kiểm tra toạ độ và bán kính trước khi gửi; null là hợp lệ. */
export const siteLocationError = (latRaw: string, lngRaw: string, radiusRaw: string): string | null => {
  const lat = parseCoord(latRaw);
  const lng = parseCoord(lngRaw);
  if ((lat === null) !== (lng === null)) return 'Cần nhập cả vĩ độ và kinh độ, hoặc để trống cả hai';
  if (lat !== null && (Number.isNaN(lat) || Math.abs(lat) > 90)) return 'Vĩ độ là số từ -90 đến 90';
  if (lng !== null && (Number.isNaN(lng) || Math.abs(lng) > 180)) return 'Kinh độ là số từ -180 đến 180';
  const r = radiusRaw.trim();
  if (!/^\d+$/.test(r) || Number(r) < 20 || Number(r) > 5000) return 'Bán kính là số nguyên từ 20 đến 5000 mét';
  return null;
};

export const settingsApi = {
  company: () => axios.get<ICompanySetting>('api/company-setting').then(r => r.data),
  saveCompany: (dto: CompanySettingInput) => axios.put<ICompanySetting>('api/company-setting', dto).then(r => r.data),
  uploadLogo(file: File) {
    const body = new FormData();
    body.append('file', file);
    return axios.post<ICompanySetting>('api/company-setting/logo', body).then(r => r.data);
  },
  resetLogo: () => axios.delete<ICompanySetting>('api/company-setting/logo').then(r => r.data),
  attendanceNotice: () => axios.get<IAttendanceNotice>('api/company-setting/attendance-notice').then(r => r.data),
  saveAttendanceNotice: (dto: Pick<IAttendanceNotice, 'noticeText' | 'locationRetentionDays'>) =>
    axios.put<IAttendanceNotice>('api/company-setting/attendance-notice', dto).then(r => r.data),
  numbering: () => axios.get<INumbering>('api/company-setting/numbering').then(r => r.data),
  saveNumbering: (input: INumberingValues) => axios.put<INumbering>('api/company-setting/numbering', input).then(r => r.data),
  defaultPassword: () =>
    axios
      .get<IDefaultPassword>('api/admin/account-settings/default-password', { headers: { 'Cache-Control': 'no-store' } })
      .then(r => r.data),
  saveDefaultPassword: (value: string | null) =>
    axios.put<IDefaultPassword>('api/admin/account-settings/default-password', { defaultPassword: value }).then(r => r.data),
  workSites: () => axios.get<IWorkSite[]>('api/work-sites').then(r => r.data ?? []),
  /** Chỉ gửi các trường vị trí; các trường khác của nơi làm việc giữ nguyên. PATCH bỏ qua giá trị null. */
  patchSite: (patch: Pick<IWorkSite, 'id' | 'latitude' | 'longitude' | 'radiusM' | 'officeIps'>) =>
    axios
      .patch<IWorkSite>(`api/work-sites/${patch.id}`, patch, { headers: { 'Content-Type': 'application/merge-patch+json' } })
      .then(r => r.data),
  getSite: (id: IWorkSite['id']) => axios.get<IWorkSite>(`api/work-sites/${id}`).then(r => r.data),
  /** Gửi cả bản ghi: dùng khi xoá toạ độ (PATCH không xoá được giá trị). */
  putSite: (site: IWorkSite) => axios.put<IWorkSite>(`api/work-sites/${site.id}`, site).then(r => r.data),
};
