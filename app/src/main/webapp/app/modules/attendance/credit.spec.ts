import { computeCredit } from './credit';

const H = { start: '08:00', end: '17:00', breakStart: '12:00', breakEnd: '13:00' };
const R = { graceMinutes: 5, halfDayThresholdMinutes: 90 };
const c = (i: string | null, o: string | null, part: 'SANG' | 'CHIEU' | null = null, absent = false) =>
  computeCredit(i, o, H, R, part, absent);

describe('computeCredit (giống DayCredit)', () => {
  it('đi muộn: 09:30 vẫn 1 công, 09:31 nửa công', () => {
    expect(c('09:30', '17:00')).toEqual({ status: 'DI_LAM', lateMinutes: 90, earlyMinutes: 0, credit: 1 });
    expect(c('09:31', '17:00')).toEqual({ status: 'DI_LAM', lateMinutes: 91, earlyMinutes: 0, credit: 0.5 });
  });

  it('muộn trong số phút cho phép coi như đúng giờ', () => {
    expect(c('08:05', '17:00').lateMinutes).toBe(0);
    expect(c('08:06', '17:00').lateMinutes).toBe(6);
  });

  it('về sớm đối xứng: 15:30 vẫn 1 công, 15:29 nửa công', () => {
    expect(c('08:00', '15:30')).toMatchObject({ earlyMinutes: 90, credit: 1 });
    expect(c('08:00', '15:29')).toMatchObject({ earlyMinutes: 91, credit: 0.5 });
  });

  it('dưới nửa giờ chuẩn hoặc vừa muộn vừa về sớm quá mốc: 0 công, vắng', () => {
    expect(c('08:00', '11:59')).toMatchObject({ status: 'VANG', credit: 0 });
    expect(c('08:00', '12:00')).toMatchObject({ status: 'DI_LAM', credit: 0.5 });
    expect(c('09:31', '15:29')).toMatchObject({ status: 'VANG', credit: 0 });
  });

  it('thiếu giờ, chưa nhập, đánh dấu vắng', () => {
    expect(c('08:00', null)).toMatchObject({ status: 'THIEU_GIO', credit: null });
    expect(c(null, null)).toMatchObject({ status: 'CHUA_NHAP', credit: null });
    expect(c('08:00', '17:00', null, true)).toMatchObject({ status: 'VANG', credit: 0 });
  });

  it('nghỉ nửa ngày: buổi còn lại đủ giờ là 0,5 công, muộn chỉ ghi lại', () => {
    expect(c('13:00', '17:00', 'SANG')).toEqual({ status: 'DI_LAM', lateMinutes: 0, earlyMinutes: 0, credit: 0.5 });
    expect(c('15:00', '17:00', 'SANG')).toMatchObject({ lateMinutes: 120, credit: 0.5 });
    expect(c('08:00', '12:00', 'CHIEU')).toMatchObject({ earlyMinutes: 0, credit: 0.5 });
  });
});
