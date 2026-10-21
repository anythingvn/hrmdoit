import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';

import { makeStore } from '../test-utils';
import ContractDialog from './contract-dialog';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const mockGets = (advice: boolean) =>
  mocked.get.mockImplementation((url: string) => {
    if (url.includes('contract-terms')) return Promise.resolve({ data: [12, 24, 36] } as any);
    if (url.includes('contract-advice')) return Promise.resolve({ data: { nextMustBeIndefinite: advice } } as any);
    if (url.includes('gross-suggestion')) return Promise.resolve({ data: { gross: 30000000 } } as any);
    return Promise.resolve({ data: [] } as any);
  });

const renderDialog = () =>
  render(
    <Provider store={makeStore(['ROLE_HR'])}>
      <ContractDialog employeeId={1} dependants={0} contracts={[]} isOpen toggle={vi.fn()} onSaved={vi.fn()} />
    </Provider>,
  );

describe('ContractDialog', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
  });

  it('HĐ xác định thời hạn 2 năm từ 25/10/2026 thì ngày kết thúc là 24/10/2028', async () => {
    mockGets(false);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Loại hợp đồng'), { target: { value: 'HDLD_XDTH' } });
    fireEvent.click(await screen.findByLabelText('2 năm'));
    fireEvent.change(screen.getByLabelText('Ngày bắt đầu'), { target: { value: '25/10/2026' } });
    expect(screen.getByLabelText('Ngày kết thúc').value).toBe('24/10/2028');
  });

  it('lương NET thì tự điền Gross từ gợi ý của server', async () => {
    mockGets(false);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Ngày bắt đầu'), { target: { value: '25/10/2026' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '26.215.000' } });
    await waitFor(() => expect(screen.getByLabelText('Lương Gross (cho kế toán)').value).toBe('30.000.000'));
    const url = mocked.get.mock.calls.map(c => c[0]).find(u => u.includes('gross-suggestion'));
    expect(url).toContain('net=26215000');
    expect(url).toContain('month=2026-10');
  });

  it('đổi mức lương thì xóa Gross cũ và hỏi lại gợi ý', async () => {
    mockGets(false);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '26.215.000' } });
    const gross = () => screen.getByLabelText('Lương Gross (cho kế toán)').value;
    await waitFor(() => expect(gross()).toBe('30.000.000'));
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '27.000.000' } });
    expect(gross()).toBe('');
    await waitFor(() => expect(gross()).toBe('30.000.000'));
    expect(mocked.get.mock.calls.filter(c => c[0].includes('gross-suggestion')).length).toBe(2);
  });

  it('đã ký 2 HĐ xác định thời hạn thì hiện cảnh báo', async () => {
    mockGets(true);
    renderDialog();
    expect(
      await screen.findByText('Đã ký 2 HĐ xác định thời hạn liên tiếp — HĐ tiếp theo phải là HĐ không xác định thời hạn'),
    ).toBeTruthy();
  });

  it('báo lỗi tiếng Việt khi server trả error.endbeforestart', async () => {
    mockGets(false);
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.endbeforestart', title: 'x' } } });
    renderDialog();
    fireEvent.change(screen.getByLabelText('Loại hợp đồng'), { target: { value: 'HDTV' } });
    fireEvent.change(screen.getByLabelText('Ngày bắt đầu'), { target: { value: '25/10/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText('Ngày kết thúc trước ngày bắt đầu')).toBeTruthy();
  });

  it('ô tiền sai rồi bị ẩn (Gross, lương tham chiếu) không chặn Lưu', async () => {
    mockGets(false);
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Lương Gross (cho kế toán)'), { target: { value: 'abc' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'GROSS' } });
    fireEvent.change(screen.getByLabelText('Loại hợp đồng'), { target: { value: 'HDTV' } });
    fireEvent.change(screen.getByLabelText('Lương chính thức tham chiếu'), { target: { value: 'xyz' } });
    fireEvent.change(screen.getByLabelText('Loại hợp đồng'), { target: { value: 'HDDV' } });
    fireEvent.change(screen.getByLabelText('Ngày bắt đầu'), { target: { value: '25/10/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalled());
    const body = mocked.post.mock.calls[0][1] as any;
    expect(body.type).toBe('HDDV');
    expect(body.grossAmount).toBeNull();
  });

  it('ô Gross đang hiện mà sai thì vẫn chặn Lưu', async () => {
    mockGets(false);
    renderDialog();
    await act(async () => {}); // chờ tải thời hạn và lời khuyên HĐ
    fireEvent.change(screen.getByLabelText('Loại hợp đồng'), { target: { value: 'HDDV' } });
    fireEvent.change(screen.getByLabelText('Ngày bắt đầu'), { target: { value: '25/10/2026' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Lương Gross (cho kế toán)'), { target: { value: 'abc' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('kỳ chưa có biểu thuế (gợi ý trả gross null kèm lý do) thì nhắc nhập Gross thủ công', async () => {
    mocked.get.mockImplementation((url: string) => {
      if (url.includes('contract-terms')) return Promise.resolve({ data: [12, 24, 36] } as any);
      if (url.includes('contract-advice')) return Promise.resolve({ data: { nextMustBeIndefinite: false } } as any);
      if (url.includes('gross-suggestion'))
        return Promise.resolve({ data: { gross: null, estimated: false, reason: 'Chưa có biểu thuế cho kỳ này' } } as any);
      return Promise.resolve({ data: [] } as any);
    });
    renderDialog();
    fireEvent.change(screen.getByLabelText('Ngày bắt đầu'), { target: { value: '01/01/2019' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '26.215.000' } });
    expect(await screen.findByText('Chưa có biểu thuế cho kỳ này — nhập Gross thủ công.')).toBeTruthy();
    expect(screen.getByLabelText('Lương Gross (cho kế toán)').value).toBe('');
  });

  it('kỳ tương lai: điền Gross ước tính theo quy định mới nhất', async () => {
    mocked.get.mockImplementation((url: string) => {
      if (url.includes('contract-terms')) return Promise.resolve({ data: [12, 24, 36] } as any);
      if (url.includes('contract-advice')) return Promise.resolve({ data: { nextMustBeIndefinite: false } } as any);
      if (url.includes('gross-suggestion'))
        return Promise.resolve({ data: { gross: 30000000, estimated: true, rulesFrom: '2026-07' } } as any);
      return Promise.resolve({ data: [] } as any);
    });
    renderDialog();
    fireEvent.change(screen.getByLabelText('Ngày bắt đầu'), { target: { value: '01/01/2027' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '26.215.000' } });
    expect(await screen.findByText('Ước tính theo quy định mới nhất (tháng 07/2026)')).toBeTruthy();
    await waitFor(() => expect(screen.getByLabelText('Lương Gross (cho kế toán)').value).toBe('30.000.000'));
  });
  it('HĐ thử việc: ghi kết quả thử việc và ngày ghi', async () => {
    mockGets(false);
    mocked.put.mockResolvedValue({ data: {} } as any);
    render(
      <Provider store={makeStore(['ROLE_HR'])}>
        <ContractDialog
          employeeId={1}
          dependants={0}
          contracts={[]}
          contract={{ id: 9, type: 'HDTV', startDate: '2026-09-01', endDate: '2026-10-31', status: 'DA_KY' }}
          isOpen
          toggle={vi.fn()}
          onSaved={vi.fn()}
        />
      </Provider>,
    );
    fireEvent.change(await screen.findByLabelText('Kết quả thử việc'), { target: { value: 'DAT' } });
    fireEvent.change(screen.getByLabelText('Ngày ghi kết quả'), { target: { value: '30/10/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith(
        'api/employment-contracts/9',
        expect.objectContaining({ probationResult: 'DAT', probationResultDate: '2026-10-30' }),
      ),
    );
  });

  it('HĐ lao động không có ô kết quả thử việc', () => {
    mockGets(false);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Loại hợp đồng'), { target: { value: 'HDLD_KXDTH' } });
    expect(screen.queryByLabelText('Kết quả thử việc')).toBeNull();
  });
});
