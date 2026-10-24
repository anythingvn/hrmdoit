import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import SelfPunch from './self-punch';

// Khung Lịch sử có spec riêng (punch-history.spec); ở đây chỉ kiểm tra màn chấm có đặt khung đó.
vi.mock('./punch-history', () => ({ default: () => <div data-testid="punch-history" /> }));

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const TODAY = {
  day: '2026-10-23',
  siteName: 'Văn phòng DOIT',
  siteKind: 'VAN_PHONG',
  start: '08:00',
  end: '17:00',
  consentRequired: false,
  noticeText: 'Hệ thống lấy vị trí một lần đúng lúc bấm.',
  punches: [] as unknown[],
};

const setGeo = (impl: ((ok: (p: unknown) => void, fail: (e: unknown) => void) => void) | undefined) => {
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: impl ? { getCurrentPosition: vi.fn(impl) } : undefined,
  });
  return navigator.geolocation as unknown as { getCurrentPosition: ReturnType<typeof vi.fn> } | undefined;
};

const gps = (ok: (p: unknown) => void) => ok({ coords: { latitude: 10.5, longitude: 106.7, accuracy: 25 } });

describe('SelfPunch', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockResolvedValue({ data: TODAY } as any);
  });

  it('hai cột: thẻ chấm hôm nay và khung Lịch sử', async () => {
    render(<SelfPunch />);
    expect(await screen.findByText(/Hôm nay chấm tại/)).toBeTruthy();
    expect(screen.getByTestId('punch-history')).toBeTruthy();
    expect(screen.getByTestId('punch-card').className).toContain('self-punch-card');
  });

  it('lần đầu: hộp thông báo, bấm "Tôi đồng ý" gọi consent', async () => {
    mocked.get.mockResolvedValueOnce({ data: { ...TODAY, consentRequired: true } } as any);
    mocked.post.mockResolvedValue({ status: 204, data: '' } as any);
    render(<SelfPunch />);
    expect(await screen.findByText('Hệ thống lấy vị trí một lần đúng lúc bấm.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Tôi đồng ý' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/consent'));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Tôi đồng ý' })).toBeNull());
  });

  it('bấm Vào: lấy vị trí rồi gửi kèm tọa độ; hiện nơi chấm', async () => {
    const geo = setGeo(gps);
    mocked.post.mockResolvedValue({
      data: {
        id: 1,
        kind: 'VAO',
        time: '08:03',
        status: 'CHAP_NHAN',
        method: 'GPS',
        siteName: 'Văn phòng DOIT',
        distanceM: 20,
        message: 'Đã chấm Vào 08:03 tại Văn phòng DOIT',
      },
    } as any);
    render(<SelfPunch />);
    expect(await screen.findByText(/Hôm nay chấm tại: Văn phòng DOIT/)).toBeTruthy();
    expect(screen.getByText(/Giờ làm 08:00–17:00/)).toBeTruthy();
    expect(screen.getByText(/23\/10\/2026/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Vào' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/attendance/punches', { kind: 'VAO', latitude: 10.5, longitude: 106.7, accuracy: 25 }),
    );
    expect(geo?.getCurrentPosition).toHaveBeenCalled();
    const res = await screen.findByTestId('punch-result');
    expect(res.className).toContain('alert-success');
    expect(res.textContent).toContain('Đã chấm Vào 08:03 tại Văn phòng DOIT');
  });

  it('vị trí bị từ chối: vẫn gửi, không kèm tọa độ', async () => {
    setGeo((_ok, fail) => fail({ code: 1 }));
    mocked.post.mockResolvedValue({
      data: {
        id: 2,
        kind: 'RA',
        time: '17:05',
        status: 'CHO_DUYET',
        method: null,
        siteName: 'X',
        distanceM: null,
        message: 'Không có vị trí',
      },
    } as any);
    render(<SelfPunch />);
    fireEvent.click(await screen.findByRole('button', { name: 'Ra' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/punches', { kind: 'RA' }));
  });

  it('trình duyệt không có geolocation: vẫn gửi không tọa độ', async () => {
    setGeo(undefined);
    mocked.post.mockResolvedValue({ data: { id: 2, kind: 'VAO', time: '08:00', status: 'CHAP_NHAN', message: 'ok' } } as any);
    render(<SelfPunch />);
    fireEvent.click(await screen.findByRole('button', { name: 'Vào' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/punches', { kind: 'VAO' }));
  });

  it.each([
    ['CHAP_NHAN', 'alert-success'],
    ['CHO_DUYET', 'alert-warning'],
    ['TU_CHOI', 'alert-danger'],
  ])('kết quả %s có nền %s kèm message', async (status, cls) => {
    setGeo(undefined);
    mocked.post.mockResolvedValue({ data: { id: 3, kind: 'VAO', time: '08:41', status, message: `Thông điệp ${status}` } } as any);
    render(<SelfPunch />);
    fireEvent.click(await screen.findByRole('button', { name: 'Vào' }));
    const res = await screen.findByTestId('punch-result');
    expect(res.className).toContain(cls);
    expect(res.textContent).toContain(`Thông điệp ${status}`);
    if (status === 'TU_CHOI') {
      fireEvent.click(screen.getByRole('button', { name: 'Gửi giải trình' }));
      expect(await screen.findByText(/Giải trình 23\/10\/2026/)).toBeTruthy();
    } else {
      expect(screen.queryByRole('button', { name: 'Gửi giải trình' })).toBeNull();
    }
  });

  it('hai nút Vào / Ra cao ít nhất 44px; liệt kê các lần chấm trong ngày', async () => {
    mocked.get.mockResolvedValue({
      data: {
        ...TODAY,
        punches: [
          { id: 1, kind: 'VAO', time: '08:41', status: 'CHO_DUYET', method: 'GPS', distanceM: 140, message: null },
          { id: 2, kind: 'VAO', time: '08:39', status: 'TU_CHOI', method: 'GPS', distanceM: 2400, message: null },
          { id: 3, kind: 'VAO', time: '08:03', status: 'CHAP_NHAN', method: 'IP', distanceM: null, message: null },
        ],
      },
    } as any);
    render(<SelfPunch />);
    for (const name of ['Vào', 'Ra']) {
      const btn = await screen.findByRole('button', { name });
      expect(parseInt(btn.style.minHeight, 10)).toBeGreaterThanOrEqual(44);
    }
    expect(screen.getByText('Chờ duyệt')).toBeTruthy();
    expect(screen.getByText('Từ chối')).toBeTruthy();
    expect(screen.getByText('Được tính')).toBeTruthy();
  });

  it('lỗi từ server hiện câu tiếng Việt', async () => {
    setGeo(undefined);
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.attendancemonthclosed' } } });
    render(<SelfPunch />);
    fireEvent.click(await screen.findByRole('button', { name: 'Vào' }));
    expect(await screen.findByText('Bảng công tháng này đã chốt, không sửa được giờ')).toBeTruthy();
  });

  it('lần chấm đã được duyệt hiện nhãn "Được tính (đã duyệt)"', async () => {
    mocked.get.mockResolvedValue({
      data: {
        ...TODAY,
        punches: [{ id: 1, kind: 'VAO', time: '08:41', status: 'DA_DUYET', method: 'GPS', distanceM: 140, message: null }],
      },
    } as any);
    render(<SelfPunch />);
    expect(await screen.findByText('Được tính (đã duyệt)')).toBeTruthy();
    expect(screen.queryByText('DA_DUYET')).toBeNull();
  });

  it('đang chờ vị trí: hiện "Đang lấy vị trí…"; gửi giải trình xong báo "Đã gửi giải trình"', async () => {
    let grant: (p: unknown) => void = () => undefined;
    setGeo(ok => {
      grant = ok;
    });
    mocked.post.mockResolvedValue({ data: { id: 3, kind: 'VAO', time: '08:41', status: 'TU_CHOI', message: 'Quá xa' } } as any);
    render(<SelfPunch />);
    fireEvent.click(await screen.findByRole('button', { name: 'Vào' }));
    expect(await screen.findByText('Đang lấy vị trí…')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Vào' }) as HTMLButtonElement).disabled).toBe(true);
    grant({ coords: { latitude: 1, longitude: 2, accuracy: 3 } });
    await waitFor(() => expect(screen.queryByText('Đang lấy vị trí…')).toBeNull());
    fireEvent.click(await screen.findByRole('button', { name: 'Gửi giải trình' }));
    fireEvent.change(screen.getByLabelText(/Giờ vào/), { target: { value: '08:00' } });
    fireEvent.change(screen.getByLabelText(/Lý do/), { target: { value: 'Đi nhầm chỗ' } });
    mocked.post.mockResolvedValue({ data: { id: 1 } } as any);
    fireEvent.click(screen.getAllByRole('button', { name: 'Gửi giải trình' }).pop() as HTMLElement);
    expect(await screen.findByText('Đã gửi giải trình')).toBeTruthy();
  });
});
