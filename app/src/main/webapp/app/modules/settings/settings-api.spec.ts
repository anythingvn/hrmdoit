import { vi } from 'vitest';
import axios from 'axios';

import { MAX_LOGO_BYTES, geoErrorText, logoFileError, mapsUrl, roundCoord, settingsApi, siteLocationError } from './settings-api';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const fileOf = (size: number, type: string, name = 'logo.png') => {
  const f = new File(['x'], name, { type });
  Object.defineProperty(f, 'size', { value: size });
  return f;
};

describe('settings-api', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
    mocked.patch.mockReset();
    mocked.delete.mockReset();
  });

  it('logo: tối đa 1 MB, chỉ PNG, JPG, SVG', () => {
    expect(MAX_LOGO_BYTES).toBe(1048576);
    expect(logoFileError(fileOf(MAX_LOGO_BYTES, 'image/png'))).toBeNull();
    expect(logoFileError(fileOf(MAX_LOGO_BYTES + 1, 'image/png'))).toBe('Logo tối đa 1 MB');
    expect(logoFileError(fileOf(10, 'image/jpeg'))).toBeNull();
    expect(logoFileError(fileOf(10, 'image/svg+xml'))).toBeNull();
    expect(logoFileError(fileOf(10, 'image/gif', 'a.gif'))).toBe('Logo phải là ảnh PNG, JPG hoặc SVG');
  });

  it('toạ độ làm tròn 6 chữ số, link Google Maps', () => {
    expect(roundCoord(21.0285114999)).toBe(21.028511);
    expect(roundCoord(-105.80481749)).toBe(-105.804817);
    expect(roundCoord(105.8048176)).toBe(105.804818);
    expect(mapsUrl(21.028511, 105.804817)).toBe('https://www.google.com/maps?q=21.028511,105.804817');
  });

  it('câu báo lỗi lấy vị trí theo mã lỗi', () => {
    expect(geoErrorText(1)).toMatch(/^Trình duyệt không cho lấy vị trí/);
    expect(geoErrorText(2)).toMatch(/Không xác định được vị trí/);
    expect(geoErrorText(3)).toMatch(/15 giây/);
  });

  it('kiểm tra toạ độ, bán kính trước khi gửi', () => {
    expect(siteLocationError('21.1', '', '200')).toMatch(/cả vĩ độ và kinh độ/);
    expect(siteLocationError('91', '105', '200')).toMatch(/Vĩ độ/);
    expect(siteLocationError('21', '181', '200')).toMatch(/Kinh độ/);
    expect(siteLocationError('21', '105', '10')).toMatch(/20 đến 5000/);
    expect(siteLocationError('21', '105', '200.5')).toMatch(/20 đến 5000/);
    expect(siteLocationError('', '', '200')).toBeNull();
    expect(siteLocationError('21,028511', '105.804817', '5000')).toBeNull();
  });

  it('tải logo gửi multipart trường "file"', async () => {
    mocked.post.mockResolvedValue({ data: { logoVersion: 5 } } as any);
    const f = fileOf(10, 'image/png');
    await settingsApi.uploadLogo(f);
    const [url, body] = mocked.post.mock.calls[0];
    expect(url).toBe('api/company-setting/logo');
    expect((body as FormData).get('file')).toBe(f);
  });
});
