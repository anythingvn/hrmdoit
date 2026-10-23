import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { act, render, screen, waitFor } from '@testing-library/react';

import { applyBrandingToDocument, logoUrl, resetBrandingForTests, useBranding } from './branding';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const Probe = ({ id }: { id: string }) => {
  const b = useBranding();
  return (
    <div data-testid={id}>
      {b.shortName}|{b.tagline}|{b.logoVersion}|{b.loaded ? 'loaded' : 'default'}
      <button onClick={() => void b.refresh()}>refresh-{id}</button>
    </div>
  );
};

describe('useBranding', () => {
  beforeEach(() => {
    resetBrandingForTests();
    mocked.get.mockReset();
    mocked.get.mockResolvedValue({ data: { companyName: 'Công ty ABC', shortName: 'ABC HR', tagline: 'Làm thật', logoVersion: 7 } } as any);
  });

  it('hiện giá trị mặc định rồi giá trị của máy chủ; nhiều chỗ dùng chỉ gọi API một lần', async () => {
    render(
      <>
        <Probe id="a" />
        <Probe id="b" />
      </>,
    );
    expect(screen.getByTestId('a').textContent).toContain('DOIT HRM|Do it if you can');
    await waitFor(() => expect(screen.getByTestId('a').textContent).toContain('ABC HR|Làm thật|7|loaded'));
    expect(screen.getByTestId('b').textContent).toContain('ABC HR|Làm thật|7|loaded');
    expect(mocked.get).toHaveBeenCalledTimes(1);
    expect(mocked.get).toHaveBeenCalledWith('api/public/branding');
  });

  it('refresh() tải lại và mọi chỗ dùng đều cập nhật', async () => {
    render(
      <>
        <Probe id="a" />
        <Probe id="b" />
      </>,
    );
    await waitFor(() => expect(screen.getByTestId('a').textContent).toContain('|7|'));
    mocked.get.mockResolvedValue({ data: { companyName: 'Công ty ABC', shortName: 'ABC', tagline: 'Mới', logoVersion: 9 } } as any);
    act(() => {
      screen.getByText('refresh-a').click();
    });
    await waitFor(() => expect(screen.getByTestId('b').textContent).toContain('ABC|Mới|9|loaded'));
    expect(mocked.get).toHaveBeenCalledTimes(2);
  });

  it('tên viết tắt trống thì dùng "DOIT HRM"; lỗi mạng giữ mặc định', async () => {
    mocked.get.mockResolvedValue({ data: { companyName: 'X', shortName: '  ', tagline: null, logoVersion: 1 } } as any);
    render(<Probe id="a" />);
    await waitFor(() => expect(screen.getByTestId('a').textContent).toContain('DOIT HRM||1|loaded'));

    resetBrandingForTests();
    mocked.get.mockRejectedValue(new Error('offline'));
    render(<Probe id="c" />);
    await waitFor(() => expect(mocked.get).toHaveBeenCalledTimes(2));
    expect(screen.getByTestId('c').textContent).toContain('DOIT HRM|Do it if you can|0|default');
  });
});

describe('applyBrandingToDocument', () => {
  it('đặt tiêu đề tab theo tên viết tắt và favicon theo phiên bản logo', () => {
    const link = document.createElement('link');
    link.rel = 'icon';
    link.href = '/favicon.ico';
    document.head.appendChild(link);
    applyBrandingToDocument({ shortName: 'ABC HR', logoVersion: 42 });
    expect(document.title).toBe('ABC HR');
    expect(link.getAttribute('href')).toBe('/api/public/branding/logo?v=42');
    expect(logoUrl(42)).toBe('/api/public/branding/logo?v=42');
    link.remove();
  });
});
