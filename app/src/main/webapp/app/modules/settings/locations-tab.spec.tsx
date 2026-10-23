import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import LocationsTab from './locations-tab';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const sites = [
  { id: 1, name: 'Văn phòng Hà Nội', type: 'VAN_PHONG', active: true, latitude: null, longitude: null, radiusM: 200, officeIps: null },
  {
    id: 2,
    name: 'Khách hàng VNPT',
    type: 'KHACH_HANG',
    active: false,
    latitude: 10.5,
    longitude: 106.7,
    radiusM: 300,
    officeIps: '1.2.3.4',
  },
  { id: 3, name: 'Làm từ xa', type: 'TU_XA', active: true, radiusM: 200 },
  { id: 4, name: 'Công tác', type: 'CONG_TAC', active: true, radiusM: 200 },
];

type GeoSuccess = (p: { coords: { latitude: number; longitude: number; accuracy: number } }) => void;
type GeoError = (e: { code: number; message: string }) => void;

const mockGeo = (impl: (ok: GeoSuccess, fail: GeoError, opts: PositionOptions) => void) => {
  const getCurrentPosition = vi.fn(impl);
  Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition }, configurable: true });
  return getCurrentPosition;
};

const setSecure = (value: boolean) => Object.defineProperty(window, 'isSecureContext', { value, configurable: true });

const openEditor = async (name = 'Văn phòng Hà Nội') => {
  const row = (await screen.findByText(name)).closest('tr')!;
  fireEvent.click(within(row).getByRole('button', { name: 'Sửa' }));
  return screen.findByRole('form', { name: `Địa điểm chấm công: ${name}` });
};

describe('LocationsTab', () => {
  beforeEach(() => {
    setSecure(true);
    mocked.get.mockReset();
    mocked.patch.mockReset();
    mocked.put.mockReset();
    mocked.get.mockResolvedValue({ data: sites } as any);
  });

  it('chỉ liệt kê văn phòng và khách hàng; nơi ngừng dùng hiện mờ nhưng vẫn sửa được', async () => {
    render(<LocationsTab />);
    expect(await screen.findByText('Văn phòng Hà Nội')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/work-sites');
    expect(screen.queryByText('Làm từ xa')).toBeNull();
    expect(screen.queryByText('Công tác')).toBeNull();
    const inactive = screen.getByText('Khách hàng VNPT').closest('tr')!;
    expect(inactive.className).toContain('text-muted');
    expect(within(inactive).getByText('Ngừng dùng')).toBeTruthy();
    expect(within(inactive).getByRole('button', { name: 'Sửa' })).toBeTruthy();
  });

  it('useCurrentLocationFillsCoords: điền toạ độ và hiện sai số', async () => {
    const geo = mockGeo(ok => ok({ coords: { latitude: 21.0285114, longitude: 105.8048167, accuracy: 12.4 } }));
    render(<LocationsTab />);
    const form = await openEditor();
    fireEvent.click(within(form).getByRole('button', { name: 'Lấy vị trí hiện tại' }));
    await waitFor(() => expect((within(form).getByLabelText('Vĩ độ') as HTMLInputElement).value).toBe('21.028511'));
    expect((within(form).getByLabelText('Kinh độ') as HTMLInputElement).value).toBe('105.804817');
    expect(within(form).getByText('Sai số khoảng 12 m')).toBeTruthy();
    expect(geo.mock.calls[0][2]).toEqual({ enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
    const link = within(form).getByRole('link', { name: 'Xem trên bản đồ' });
    expect(link.getAttribute('href')).toBe('https://www.google.com/maps?q=21.028511,105.804817');
    expect(link.getAttribute('target')).toBe('_blank');
  });

  it('geolocationDeniedShowsReason: báo lý do, ô toạ độ giữ nguyên', async () => {
    mockGeo((_ok, fail) => fail({ code: 1, message: 'denied' }));
    render(<LocationsTab />);
    const form = await openEditor('Khách hàng VNPT');
    fireEvent.click(within(form).getByRole('button', { name: 'Lấy vị trí hiện tại' }));
    expect(await within(form).findByText(/Trình duyệt không cho lấy vị trí/)).toBeTruthy();
    expect((within(form).getByLabelText('Vĩ độ') as HTMLInputElement).value).toBe('10.5');
    expect((within(form).getByLabelText('Kinh độ') as HTMLInputElement).value).toBe('106.7');
  });

  it('không phải HTTPS: báo cần HTTPS, không gọi geolocation', async () => {
    setSecure(false);
    const geo = mockGeo(() => undefined);
    render(<LocationsTab />);
    const form = await openEditor();
    fireEvent.click(within(form).getByRole('button', { name: 'Lấy vị trí hiện tại' }));
    expect(await within(form).findByText(/cần kết nối HTTPS/)).toBeTruthy();
    expect(geo).not.toHaveBeenCalled();
  });

  it('lưu bằng PATCH chỉ các trường vị trí', async () => {
    mocked.patch.mockResolvedValue({ data: {} } as any);
    render(<LocationsTab />);
    const form = await openEditor();
    fireEvent.change(within(form).getByLabelText('Vĩ độ'), { target: { value: '21,028511' } });
    fireEvent.change(within(form).getByLabelText('Kinh độ'), { target: { value: '105.804817' } });
    fireEvent.change(within(form).getByLabelText('Bán kính cho phép (m)'), { target: { value: '150' } });
    fireEvent.change(within(form).getByLabelText(/IP public của mạng văn phòng/), { target: { value: '113.190.1.10\n10.0.0.0/8' } });
    fireEvent.click(within(form).getByRole('button', { name: 'Lưu địa điểm' }));
    await waitFor(() =>
      expect(mocked.patch).toHaveBeenCalledWith(
        'api/work-sites/1',
        { id: 1, latitude: 21.028511, longitude: 105.804817, radiusM: 150, officeIps: '113.190.1.10\n10.0.0.0/8' },
        { headers: { 'Content-Type': 'application/merge-patch+json' } },
      ),
    );
    expect(await screen.findByText('Đã lưu địa điểm chấm công Văn phòng Hà Nội.')).toBeTruthy();
  });

  it('xoá toạ độ đã có: dùng PUT cả bản ghi (PATCH bỏ qua giá trị rỗng)', async () => {
    mocked.put.mockResolvedValue({ data: {} } as any);
    render(<LocationsTab />);
    const form = await openEditor('Khách hàng VNPT');
    fireEvent.change(within(form).getByLabelText('Vĩ độ'), { target: { value: '' } });
    fireEvent.change(within(form).getByLabelText('Kinh độ'), { target: { value: '' } });
    fireEvent.click(within(form).getByRole('button', { name: 'Lưu địa điểm' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/work-sites/2', {
        ...sites[1],
        latitude: null,
        longitude: null,
        radiusM: 300,
        officeIps: '1.2.3.4',
      }),
    );
    expect(mocked.patch).not.toHaveBeenCalled();
  });
});
