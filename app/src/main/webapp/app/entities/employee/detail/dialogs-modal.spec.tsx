import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';

import SetPasswordDialog from 'app/modules/administration/user-management/set-password-dialog';
import { makeStore } from '../test-utils';
import AllowanceDialog from './allowance-dialog';
import ContractDialog from './contract-dialog';
import SalaryChangeDialog from './salary-change-dialog';
import TerminateDialog from './terminate-dialog';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

type DialogCase = [string, string, (isOpen: boolean, toggle: () => void) => React.JSX.Element];

// Các hộp thoại dùng Modal của react-bootstrap (show / onHide / nút đóng ×) thay cho reactstrap (isOpen / toggle).
const dialogs: DialogCase[] = [
  [
    'TerminateDialog',
    'Cho nghỉ việc',
    (isOpen, toggle) => <TerminateDialog employeeId={7} isOpen={isOpen} toggle={toggle} onDone={vi.fn()} />,
  ],
  [
    'SalaryChangeDialog',
    'Điều chỉnh lương',
    (isOpen, toggle) => <SalaryChangeDialog employeeId={1} dependants={0} isOpen={isOpen} toggle={toggle} onSaved={vi.fn()} />,
  ],
  [
    'ContractDialog',
    'Thêm hợp đồng',
    (isOpen, toggle) => (
      <Provider store={makeStore(['ROLE_HR'])}>
        <ContractDialog employeeId={1} dependants={0} contracts={[]} isOpen={isOpen} toggle={toggle} onSaved={vi.fn()} />
      </Provider>
    ),
  ],
  [
    'AllowanceDialog',
    'Thêm phụ cấp',
    (isOpen, toggle) => <AllowanceDialog employeeId={1} isOpen={isOpen} toggle={toggle} onSaved={vi.fn()} />,
  ],
  [
    'SetPasswordDialog',
    'Đặt mật khẩu: nhanvien1',
    (isOpen, toggle) => <SetPasswordDialog login="nhanvien1" isOpen={isOpen} toggle={toggle} />,
  ],
];

describe('Hộp thoại (react-bootstrap Modal)', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockResolvedValue({ data: [] } as any);
  });

  it.each(dialogs)('%s: đang mở thì có tiêu đề làm tên hộp thoại, bấm × (nhãn "Đóng") thì gọi toggle', (_name, title, make) => {
    const toggle = vi.fn();
    render(make(true, toggle));
    expect(screen.getByRole('dialog', { name: title })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Đóng' }));
    expect(toggle).toHaveBeenCalledTimes(1);
  });

  it.each(dialogs)('%s: đóng (isOpen=false) thì không hiển thị gì', (_name, _title, make) => {
    render(make(false, vi.fn()));
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});
