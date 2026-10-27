import { parseCell } from './calc-api';

describe('parseCell', () => {
  it('ô tiền: dấu chấm là phân cách hàng nghìn, số âm cho thừa/thiếu', () => {
    expect(parseCell('1500000', 'THUONG_KPI')).toBe(1500000);
    expect(parseCell('500.000', 'THUONG_KPI')).toBe(500000);
    expect(parseCell('2.000.000', 'LUONG_GOP')).toBe(2000000);
    expect(parseCell(' 2.000.000 ', 'LUONG_GOP')).toBe(2000000);
    expect(parseCell('-300.000', 'THUA_THIEU')).toBe(-300000);
    expect(parseCell('-300000', 'THUA_THIEU')).toBe(-300000);
  });
  it('ô tiền: không nhận số lẻ, dấu chấm sai chỗ', () => {
    expect(Number.isNaN(parseCell('20,5', 'THUONG_KPI') as number)).toBe(true);
    expect(Number.isNaN(parseCell('1000.5', 'THUONG_KPI') as number)).toBe(true);
    expect(Number.isNaN(parseCell('50.00', 'THUONG_KPI') as number)).toBe(true);
  });
  it('ô ngày công / số người: dấu , hoặc . là phần lẻ', () => {
    expect(parseCell('20,5', 'NGAY_CONG_THUC_TE')).toBe(20.5);
    expect(parseCell('21.5', 'NGAY_CONG_THUC_TE')).toBe(21.5);
    expect(parseCell('2', 'SO_NGUOI_PHU_THUOC')).toBe(2);
  });
  it('trống là null, chữ là NaN', () => {
    expect(parseCell('  ', 'THUONG_KPI')).toBeNull();
    expect(Number.isNaN(parseCell('abc', 'THUONG_KPI') as number)).toBe(true);
    expect(Number.isNaN(parseCell('abc', 'NGAY_CONG_THUC_TE') as number)).toBe(true);
  });
});
