import { vi } from 'vitest';
import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { Storage } from 'react-jhipster';

import setupAxiosInterceptors from 'app/config/axios-interceptor';
import { AUTHENTICATION_TOKEN_KEY } from 'app/shared/jhipster/constants';

import { getBrandingForTests, refreshBranding, resetBrandingForTests } from './branding';

/**
 * Dùng axios thật (không mock) và interceptor thật: token cũ đã hết hạn vẫn nằm trong bộ nhớ trình duyệt. Máy chủ trả 401 cho
 * mọi request mang token sai, kể cả API công khai; branding phải gửi request không kèm token nên vẫn tải được.
 */
describe('branding với token đã hết hạn', () => {
  const originalAdapter = axios.defaults.adapter;
  const seen: (string | undefined)[] = [];
  let onUnauthenticated: ReturnType<typeof vi.fn>;
  let reqId: number;
  let resId: number;

  beforeEach(() => {
    resetBrandingForTests();
    seen.length = 0;
    Storage.local.set(AUTHENTICATION_TOKEN_KEY, 'expired-token');
    const adapter: AxiosAdapter = (config: InternalAxiosRequestConfig) => {
      const auth = config.headers?.Authorization as string | undefined;
      seen.push(auth);
      if (auth) {
        const response = { status: 401, statusText: 'Unauthorized', data: {}, headers: {}, config };
        return Promise.reject(new AxiosError('Unauthorized', AxiosError.ERR_BAD_REQUEST, config, null, response));
      }
      return Promise.resolve({
        status: 200,
        statusText: 'OK',
        data: { companyName: 'Công ty ABC', shortName: 'ABC HR', tagline: 'Làm thật', logoVersion: 7 },
        headers: {},
        config,
      });
    };
    axios.defaults.adapter = adapter;
    onUnauthenticated = vi.fn();
    const before = { req: (axios.interceptors.request as any).handlers.length, res: (axios.interceptors.response as any).handlers.length };
    setupAxiosInterceptors(onUnauthenticated);
    reqId = before.req;
    resId = before.res;
  });

  afterEach(() => {
    axios.interceptors.request.eject(reqId);
    axios.interceptors.response.eject(resId);
    axios.defaults.adapter = originalAdapter;
    Storage.local.remove(AUTHENTICATION_TOKEN_KEY);
  });

  it('request branding không kèm Authorization nên không bị 401, branding tải được và không đăng xuất', async () => {
    await refreshBranding();
    expect(seen).toEqual([undefined]);
    expect(getBrandingForTests()).toMatchObject({ shortName: 'ABC HR', logoVersion: 7, loaded: true });
    expect(onUnauthenticated).not.toHaveBeenCalled();
  });

  it('API thường vẫn kèm token (interceptor toàn cục vẫn chạy cho axios mặc định)', async () => {
    await expect(axios.get('api/account')).rejects.toBeTruthy();
    expect(seen).toEqual(['Bearer expired-token']);
    expect(onUnauthenticated).toHaveBeenCalledTimes(1);
  });
});
