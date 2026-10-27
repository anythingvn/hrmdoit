import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Storage } from 'react-jhipster';

import { configureStore } from '@reduxjs/toolkit';
import axios from 'axios';

import { AUTHENTICATION_TOKEN_KEY } from 'app/shared/jhipster/constants';
import authentication, { SESSION_RETRY_MS, getAccount, getSession } from 'app/shared/reducers/authentication';
import locale from 'app/shared/reducers/locale';

/** Máy chủ đang khởi động lại (vd build lại Docker): không được coi là chưa đăng nhập khi trình duyệt vẫn còn token. */
describe('Phiên đăng nhập khi máy chủ khởi động lại', () => {
  const networkError = Object.assign(new Error('Network Error'), { isAxiosError: true, code: 'ERR_NETWORK' });
  const httpError = (status: number) =>
    Object.assign(new Error(`Request failed with status code ${status}`), { isAxiosError: true, response: { status } });
  const account = { data: { login: 'admin', activated: true, authorities: ['ROLE_ADMIN'] } };
  const makeStore = () => configureStore({ reducer: { authentication, locale } });

  beforeEach(() => {
    Storage.session.set(AUTHENTICATION_TOKEN_KEY, 'token-cu');
  });

  afterEach(() => {
    vi.useRealTimers();
    Storage.session.remove(AUTHENTICATION_TOKEN_KEY);
    Storage.local.remove(AUTHENTICATION_TOKEN_KEY);
  });

  it.each([
    ['lỗi mạng', networkError],
    ['502', httpError(502)],
    ['503', httpError(503)],
    ['504', httpError(504)],
  ])('%s khi còn token: chưa kết luận phiên, đánh dấu máy chủ chưa sẵn sàng, giữ token', async (_name, err) => {
    axios.get = vi.fn().mockRejectedValue(err);
    const store = makeStore();
    await store.dispatch(getAccount());
    const s = store.getState().authentication;
    expect(s.serverUnavailable).toBe(true);
    expect(s.sessionHasBeenFetched).toBe(false);
    expect(s.showModalLogin).toBe(false);
    expect(Storage.session.get(AUTHENTICATION_TOKEN_KEY)).toBe('token-cu');
  });

  it('401: vẫn là chưa đăng nhập như cũ', async () => {
    axios.get = vi.fn().mockRejectedValue(httpError(401));
    const store = makeStore();
    await store.dispatch(getAccount());
    const s = store.getState().authentication;
    expect(s.serverUnavailable).toBe(false);
    expect(s.sessionHasBeenFetched).toBe(true);
    expect(s.isAuthenticated).toBe(false);
  });

  it('lỗi mạng khi không có token: là chưa đăng nhập (không chờ vô hạn)', async () => {
    Storage.session.remove(AUTHENTICATION_TOKEN_KEY);
    axios.get = vi.fn().mockRejectedValue(networkError);
    const store = makeStore();
    await store.dispatch(getAccount());
    const s = store.getState().authentication;
    expect(s.serverUnavailable).toBe(false);
    expect(s.sessionHasBeenFetched).toBe(true);
    expect(s.isAuthenticated).toBe(false);
  });

  it('getSession tự thử lại tới khi máy chủ lên, rồi vào lại phiên cũ', async () => {
    vi.useFakeTimers();
    axios.get = vi.fn().mockRejectedValueOnce(networkError).mockRejectedValueOnce(httpError(503)).mockResolvedValue(account);
    const store = makeStore();
    await (store.dispatch(getSession()) as unknown as Promise<void>);
    expect(store.getState().authentication.serverUnavailable).toBe(true);

    await vi.advanceTimersByTimeAsync(SESSION_RETRY_MS);
    expect(store.getState().authentication.serverUnavailable).toBe(true);

    await vi.advanceTimersByTimeAsync(SESSION_RETRY_MS);
    const s = store.getState().authentication;
    expect(s.serverUnavailable).toBe(false);
    expect(s.isAuthenticated).toBe(true);
    expect(s.sessionHasBeenFetched).toBe(true);
    expect(axios.get).toHaveBeenCalledTimes(3);
  });

  it('nhiều lần gọi getSession lúc máy chủ chưa lên không tạo nhiều vòng thử lại song song', async () => {
    vi.useFakeTimers();
    axios.get = vi.fn().mockRejectedValueOnce(networkError).mockRejectedValueOnce(networkError).mockResolvedValue(account);
    const store = makeStore();
    await (store.dispatch(getSession()) as unknown as Promise<void>);
    await (store.dispatch(getSession()) as unknown as Promise<void>);
    await vi.advanceTimersByTimeAsync(SESSION_RETRY_MS);
    expect(axios.get).toHaveBeenCalledTimes(3);
    expect(store.getState().authentication.isAuthenticated).toBe(true);
  });
});
