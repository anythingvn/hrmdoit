import { describe, expect, it, vi } from 'vitest';

import axios from 'axios';

import setupAxiosInterceptors from './axios-interceptor';

describe('Axios Interceptor', () => {
  describe('setupAxiosInterceptors', () => {
    const onUnauthenticated = vi.fn();
    setupAxiosInterceptors(onUnauthenticated);

    it('onRequestSuccess is called on fulfilled request', () => {
      expect((axios.interceptors.request as any).handlers[0].fulfilled({ data: 'foo', url: '/test' })).toMatchObject({
        data: 'foo',
      });
    });
    it('onResponseSuccess is called on fulfilled response', () => {
      expect((axios.interceptors.response as any).handlers[0].fulfilled({ data: 'foo' })).toEqual({ data: 'foo' });
    });
    it('onResponseError is called on rejected response', async () => {
      const rejectError = {
        response: {
          statusText: 'NotFound',
          status: 401,
          data: { message: 'Page not found' },
        },
      };
      await expect((axios.interceptors.response as any).handlers[0].rejected(rejectError)).rejects.toEqual(rejectError);
      expect(onUnauthenticated).toHaveBeenCalledTimes(1);
    });

    it('403 mustchangepassword gọi onMustChangePassword, không coi là hết phiên', async () => {
      const onUnauth = vi.fn();
      const onMustChange = vi.fn();
      axios.interceptors.response.clear();
      setupAxiosInterceptors(onUnauth, onMustChange);
      const err = { response: { status: 403, data: { message: 'error.mustchangepassword' } } };
      await expect((axios.interceptors.response as any).handlers[0].rejected(err)).rejects.toEqual(err);
      expect(onMustChange).toHaveBeenCalledTimes(1);
      expect(onUnauth).not.toHaveBeenCalled();
      const other = { response: { status: 403, data: { message: 'error.http.403' } } };
      await expect((axios.interceptors.response as any).handlers[0].rejected(other)).rejects.toEqual(other);
      expect(onMustChange).toHaveBeenCalledTimes(1);
    });
  });
});
