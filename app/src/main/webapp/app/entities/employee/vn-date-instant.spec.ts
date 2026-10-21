import { instantToVn } from './vn-date';

describe('instantToVn', () => {
  it('đổi thời điểm sang ngày giờ Việt Nam (UTC+7)', () => {
    expect(instantToVn('2026-10-20T18:30:00Z')).toBe('21/10/2026');
    expect(instantToVn('2026-10-20T18:30:00Z', true)).toBe('21/10/2026 01:30');
    expect(instantToVn('2026-10-21T03:05:00Z', true)).toBe('21/10/2026 10:05');
  });

  it('trống thì ra chuỗi rỗng', () => {
    expect(instantToVn(null)).toBe('');
    expect(instantToVn(undefined, true)).toBe('');
  });
});
