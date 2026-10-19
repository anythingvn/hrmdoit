import React from 'react';
import axios from 'axios';
import { render, screen, within } from '@testing-library/react';

import HistoryTab from './history-tab';

jest.mock('axios');
const mocked = axios as jest.Mocked<typeof axios>;

const rowOf = async (label: string) => (await screen.findByRole('cell', { name: label })).closest('tr');

describe('HistoryTab', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockResolvedValue({
      data: [
        {
          at: '2026-10-19T03:00:00Z',
          login: 'hr',
          entity: 'EmploymentContract',
          action: 'SỬA',
          changes: [
            { field: 'salaryBasis', before: 'GROSS', after: 'NET' },
            { field: 'startDate', before: '2026-10-01', after: '2026-10-25' },
            { field: 'salary', before: '25000000', after: '26215000' },
            { field: 'type', before: 'HDTV', after: 'HDLD_XDTH' },
            { field: 'department', before: null, after: '3' },
            { field: 'storagePath', before: 'a', after: 'b' },
          ],
        },
      ],
    } as any);
  });

  it('hiện tên trường tiếng Việt và giá trị đã định dạng', async () => {
    render(<HistoryTab employeeId={1} />);
    const basis = await rowOf('Lương tính theo');
    expect(within(basis).getByText('Gross')).toBeTruthy();
    expect(within(basis).getByText('Net')).toBeTruthy();
    const start = await rowOf('Ngày bắt đầu');
    expect(within(start).getByText('01/10/2026')).toBeTruthy();
    expect(within(start).getByText('25/10/2026')).toBeTruthy();
    const salary = await rowOf('Mức lương');
    expect(within(salary).getByText('26.215.000')).toBeTruthy();
    const type = await rowOf('Loại hợp đồng');
    expect(within(type).getByText('HĐLĐ xác định thời hạn')).toBeTruthy();
    const dept = await rowOf('Phòng ban');
    expect(within(dept).getByText('Phòng ban #3')).toBeTruthy();
    expect(screen.queryByText('salaryBasis')).toBeNull();
    expect(screen.queryByText('storagePath')).toBeNull();
  });
});
