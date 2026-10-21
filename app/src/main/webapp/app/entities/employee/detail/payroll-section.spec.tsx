import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { render, screen } from '@testing-library/react';

import PayrollSection from './payroll-section';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

describe('PayrollSection', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockResolvedValue({
      data: [
        {
          period: '2026-06',
          status: 'DA_DUYET',
          version: 1,
          thucNhan: 16546425,
          tongChiPhi: 18425500,
          thueTncn: 120000,
          tongBhNld: 1500000,
        },
      ],
    } as any);
  });

  it('Kế toán thấy các kỳ lương của nhân viên', async () => {
    render(<PayrollSection employeeId={5} visible />);
    expect(await screen.findByText('06/2026')).toBeTruthy();
    expect(screen.getByText('16.546.425')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/employees/5/payroll');
  });

  it('vai trò khác: không hiện, không gọi API', () => {
    const { container } = render(<PayrollSection employeeId={5} visible={false} />);
    expect(container.innerHTML).toBe('');
    expect(mocked.get).not.toHaveBeenCalled();
  });
});
