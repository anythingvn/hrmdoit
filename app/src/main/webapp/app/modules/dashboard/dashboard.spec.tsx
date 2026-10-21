import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';

import { makeStore } from 'app/entities/employee/test-utils';
import Dashboard from './dashboard';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const movement = Array.from({ length: 12 }, (_, i) => ({
  month: `2026-${String(i + 1).padStart(2, '0')}`,
  hires: i % 3,
  leavers: i % 2,
  closing: 10 + i,
}));

const renderAs = (authorities: string[], data: Record<string, unknown>) => {
  mocked.get.mockImplementation((url: string) => {
    if (url === 'api/dashboard') return Promise.resolve({ data } as any);
    return Promise.resolve({ data: [] } as any);
  });
  return render(
    <Provider store={makeStore(authorities)}>
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    </Provider>,
  );
};

describe('Dashboard', () => {
  beforeEach(() => mocked.get.mockReset());

  it('Kế toán thấy chi phí lương kỳ gần nhất', async () => {
    renderAs(['ROLE_KE_TOAN'], {
      headcount: 137,
      onProbation: 1,
      contractsExpiring30: 2,
      payrollCost: { period: '2026-06', status: 'NHAP', totalCost: 245709000 },
      movement,
    });
    expect(await screen.findByText('Chi phí lương kỳ 06/2026')).toBeTruthy();
    expect(screen.getByText('245.709.000')).toBeTruthy();
    expect(screen.getByText('Nhân viên đang làm')).toBeTruthy();
    expect(screen.getByText('137')).toBeTruthy();
    expect(screen.getByRole('img', { name: /Biến động nhân sự 12 tháng/ })).toBeTruthy();
  });

  it('không có payrollCost thì không có ô chi phí', async () => {
    renderAs(['ROLE_HR'], { headcount: 14, onProbation: 1, contractsExpiring30: 2, movement });
    expect(await screen.findByText('Nhân viên đang làm')).toBeTruthy();
    expect(screen.queryByText(/Chi phí lương kỳ/)).toBeNull();
  });
});
