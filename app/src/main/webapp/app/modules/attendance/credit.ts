/**
 * Tính công một ngày ở trình duyệt, giống hệt lõi {@code vn.doit.hr.attendance.DayCredit} (để hiện công ngay khi gõ, trước khi lưu).
 * Giờ dạng "HH:mm". Đi muộn / về sớm trong số phút cho phép coi như đúng giờ; quá mốc nửa ngày thì nửa công; vừa muộn quá mốc vừa về
 * sớm quá mốc, hoặc giờ làm trong ca dưới nửa giờ chuẩn thì 0 công (vắng). Nghỉ nửa ngày: buổi còn lại có đủ giờ vào/ra là 0,5 công.
 */

export type CreditStatus = 'DI_LAM' | 'THIEU_GIO' | 'VANG' | 'CHUA_NHAP';
export type LeavePart = 'SANG' | 'CHIEU';

export interface IHours {
  start: string;
  end: string;
  breakStart: string;
  breakEnd: string;
}

export interface IRules {
  graceMinutes: number;
  halfDayThresholdMinutes: number;
}

export interface ICreditResult {
  status: CreditStatus;
  lateMinutes: number;
  earlyMinutes: number;
  /** Công đi làm (0; 0,5; 1); null khi chưa đủ giờ. */
  credit: number | null;
}

export const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

/** "HH:mm" (hoặc "HH:mm:ss") thành số phút trong ngày; sai dạng thì null. */
export const toMinutes = (t?: string | null): number | null => {
  const s = (t ?? '').trim().slice(0, 5);
  if (!HHMM.test(s)) return null;
  return Number(s.slice(0, 2)) * 60 + Number(s.slice(3, 5));
};

const pos = (n: number) => Math.max(0, n);
const overlap = (a1: number, a2: number, b1: number, b2: number) => pos(Math.min(a2, b2) - Math.max(a1, b1));

export const computeCredit = (
  inTime: string | null | undefined,
  outTime: string | null | undefined,
  hours: IHours,
  rules: IRules,
  leavePart: LeavePart | null | undefined,
  absent: boolean,
): ICreditResult => {
  const firstIn = toMinutes(inTime);
  const lastOut = toMinutes(outTime);
  if (absent) return { status: 'VANG', lateMinutes: 0, earlyMinutes: 0, credit: 0 };
  if (firstIn === null && lastOut === null) return { status: 'CHUA_NHAP', lateMinutes: 0, earlyMinutes: 0, credit: null };
  if (firstIn === null || lastOut === null) return { status: 'THIEU_GIO', lateMinutes: 0, earlyMinutes: 0, credit: null };
  const start = toMinutes(hours.start) ?? 0;
  const end = toMinutes(hours.end) ?? 0;
  const breakStart = toMinutes(hours.breakStart) ?? 0;
  const breakEnd = toMinutes(hours.breakEnd) ?? 0;
  const segStart = leavePart === 'SANG' ? breakEnd : start;
  const segEnd = leavePart === 'CHIEU' ? breakStart : end;
  let late = pos(firstIn - segStart);
  let early = pos(segEnd - lastOut);
  if (late <= rules.graceMinutes) late = 0;
  if (early <= rules.graceMinutes) early = 0;
  if (leavePart) return { status: 'DI_LAM', lateMinutes: late, earlyMinutes: early, credit: 0.5 };
  const standard = end - start - (breakEnd - breakStart);
  const worked = overlap(firstIn, lastOut, start, end) - overlap(firstIn, lastOut, breakStart, breakEnd);
  const lateOver = late > rules.halfDayThresholdMinutes;
  const earlyOver = early > rules.halfDayThresholdMinutes;
  if ((lateOver && earlyOver) || worked * 2 < standard) return { status: 'VANG', lateMinutes: late, earlyMinutes: early, credit: 0 };
  return { status: 'DI_LAM', lateMinutes: late, earlyMinutes: early, credit: lateOver || earlyOver ? 0.5 : 1 };
};
