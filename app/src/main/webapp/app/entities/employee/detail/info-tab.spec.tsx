import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';

import { makeStore } from '../test-utils';
import InfoTab from './info-tab';

jest.mock('axios');
const mocked = axios as jest.Mocked<typeof axios>;

const employee = { id: 1, fullName: 'Trần Minh Quân', code: 'NV0142', status: 'DANG_LAM_VIEC' as const };

const renderTab = (role: string) =>
  render(
    <Provider store={makeStore([role])}>
      <InfoTab employee={employee} />
    </Provider>,
  );

describe('InfoTab', () => {
  beforeEach(() => mocked.get.mockReset());

  it('Ban giám đốc không thấy nút "Hiện"', () => {
    renderTab('ROLE_BGD');
    expect(screen.queryByRole('button', { name: 'Hiện' })).toBeNull();
    expect(screen.getAllByText('••••').length).toBeGreaterThan(0);
  });

  it('Nhân sự bấm "Hiện" thì gọi /sensitive và thấy số CCCD', async () => {
    mocked.get.mockResolvedValue({ data: { idNumber: '001096012345', bankAccount: '123456789' } } as any);
    renderTab('ROLE_HR');
    expect(screen.queryByText('001096012345')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Hiện' }));
    expect(await screen.findByText('001096012345')).toBeTruthy();
    await waitFor(() => expect(mocked.get).toHaveBeenCalledWith(expect.stringMatching(/api\/employees\/1\/sensitive$/)));
  });

  it('Hiện → Ẩn → Hiện gọi /sensitive hai lần và Ẩn che lại dữ liệu', async () => {
    mocked.get.mockResolvedValue({ data: { idNumber: '001096012345' } } as any);
    renderTab('ROLE_HR');
    fireEvent.click(screen.getByRole('button', { name: 'Hiện' }));
    await screen.findByText('001096012345');
    fireEvent.click(screen.getByRole('button', { name: 'Ẩn' }));
    expect(screen.queryByText('001096012345')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Hiện' }));
    await screen.findByText('001096012345');
    expect(mocked.get.mock.calls.filter(c => String(c[0]).endsWith('/sensitive'))).toHaveLength(2);
  });
});
