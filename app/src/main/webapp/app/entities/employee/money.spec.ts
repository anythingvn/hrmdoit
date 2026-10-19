import { formatMoney, parseMoney } from './money';

describe('money', () => {
  it('parseMoney chấp nhận 15.000.000, 15000000, 15,000,000', () => {
    expect(parseMoney('15.000.000')).toBe(15000000);
    expect(parseMoney('15000000')).toBe(15000000);
    expect(parseMoney('15,000,000')).toBe(15000000);
    expect(parseMoney(' 15.000.000 ')).toBe(15000000);
  });

  it('parseMoney trả null khi gõ chữ hoặc để trống hoặc sai nhóm', () => {
    expect(parseMoney('15tr')).toBeNull();
    expect(parseMoney('')).toBeNull();
    expect(parseMoney('   ')).toBeNull();
    expect(parseMoney('abc')).toBeNull();
    expect(parseMoney('15.00.000')).toBeNull();
    expect(parseMoney('-5')).toBeNull();
  });

  it('formatMoney nhóm ba chữ số bằng dấu chấm', () => {
    expect(formatMoney(26215000)).toBe('26.215.000');
    expect(formatMoney(15000000)).toBe('15.000.000');
    expect(formatMoney(0)).toBe('0');
    expect(formatMoney(999)).toBe('999');
  });
});
