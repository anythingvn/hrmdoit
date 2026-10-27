import { parseCell } from './calc-api';

describe('parseCell', () => {
  it('đọc số nguyên, số thập phân (dấu , hoặc .), số âm', () => {
    expect(parseCell('1500000')).toBe(1500000);
    expect(parseCell('21,5')).toBe(21.5);
    expect(parseCell('21.5')).toBe(21.5);
    expect(parseCell('-300000')).toBe(-300000);
  });
  it('trống là null, chữ là NaN', () => {
    expect(parseCell('  ')).toBeNull();
    expect(Number.isNaN(parseCell('abc') as number)).toBe(true);
  });
});
