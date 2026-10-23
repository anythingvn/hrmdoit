import { vi } from 'vitest';

import { getPosition } from './geolocation';

const setGeo = (getCurrentPosition: (ok: (p: unknown) => void, fail: () => void, opts: unknown) => void) =>
  Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition }, configurable: true });

describe('getPosition', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('timeout của trình duyệt 10 giây (không tính lúc hộp xin quyền còn mở), maximumAge 0, độ chính xác cao', async () => {
    const spy = vi.fn((ok: (p: unknown) => void) => ok({ coords: { latitude: 1, longitude: 2, accuracy: 3 } }));
    setGeo(spy);
    await expect(getPosition()).resolves.toEqual({ latitude: 1, longitude: 2, accuracy: 3 });
    expect(spy.mock.calls[0][2]).toEqual({ timeout: 10000, maximumAge: 0, enableHighAccuracy: true });
  });

  it('bộ đếm ngoài là 30 giây: 10 giây chưa bỏ cuộc (người dùng còn đang trả lời hộp xin quyền), 30 giây thì trả null', async () => {
    vi.useFakeTimers();
    setGeo(() => undefined); // trình duyệt không bao giờ trả lời
    let settled = false;
    const p = getPosition().then(v => {
      settled = true;
      return v;
    });
    await vi.advanceTimersByTimeAsync(10_001);
    expect(settled).toBe(false);
    await vi.advanceTimersByTimeAsync(20_000);
    expect(settled).toBe(true);
    await expect(p).resolves.toBeNull();
  });
});
