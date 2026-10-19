import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import SalaryChangeDialog from './salary-change-dialog';

jest.mock('axios');
const mocked = axios as jest.Mocked<typeof axios>;

const renderDialog = () => render(<SalaryChangeDialog employeeId={1} dependants={0} isOpen toggle={jest.fn()} onSaved={jest.fn()} />);

describe('SalaryChangeDialog', () => {
  beforeEach(() => mocked.get.mockReset());

  it('chưa có biểu thuế cho năm đó thì nhắc nhập Gross thủ công', async () => {
    mocked.get.mockResolvedValue({ data: { gross: null } } as any);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Ngày hiệu lực'), { target: { value: '01/03/2027' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '20.000.000' } });
    expect(await screen.findByText('Chưa có biểu thuế cho năm 2027 — nhập Gross thủ công.')).toBeTruthy();
  });

  it('có gợi ý thì điền Gross và không hiện lời nhắc', async () => {
    mocked.get.mockResolvedValue({ data: { gross: 23000000 } } as any);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Ngày hiệu lực'), { target: { value: '01/03/2026' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '20.000.000' } });
    await waitFor(() => expect(screen.getByLabelText('Lương Gross (cho kế toán)').value).toBe('23.000.000'));
    expect(screen.queryByText(/Chưa có biểu thuế/)).toBeNull();
  });
});
