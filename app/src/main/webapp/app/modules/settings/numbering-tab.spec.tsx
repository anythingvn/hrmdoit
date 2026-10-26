import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import NumberingTab from './numbering-tab';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const DEFAULTS = {
  documentPrefix: 'DOIT',
  documentCodes: { HDLD: 'HĐLĐ', HDTV: 'HĐTV', HDDT: 'HĐĐT', HDDV: 'HĐDV', QD_CU_NHAN_SU: 'QĐCNS' },
  staffCodePrefix: 'DOIT',
  internCodePrefix: 'TT',
  serviceCodePrefix: 'HDDV',
};
const NUMBERING = { ...DEFAULTS, defaults: DEFAULTS };
const YEAR = new Date().getFullYear();
const SEQUENCES = [
  { type: 'HDLD', code: 'HĐLĐ', year: YEAR, nextValue: 13, lastIssued: 12, nextNumber: `DOIT/${YEAR}/HĐLĐ/13` },
  { type: 'HDTV', code: 'HĐTV', year: YEAR, nextValue: 1, lastIssued: 0, nextNumber: `DOIT/${YEAR}/HĐTV/1` },
];

describe('NumberingTab', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.get.mockImplementation((url: string) =>
      Promise.resolve({ data: url === 'api/company-setting/numbering' ? NUMBERING : SEQUENCES } as any),
    );
  });

  it('hiện giá trị hiệu lực, "mặc định: …", câu chỉ áp dụng cho mới và bảng dãy số bên dưới', async () => {
    render(<NumberingTab canEdit />);
    const prefix = (await screen.findByLabelText('Tiền tố số văn bản')) as HTMLInputElement;
    await waitFor(() => expect(prefix.value).toBe('DOIT'));
    expect((screen.getByLabelText('Mã Hợp đồng lao động') as HTMLInputElement).value).toBe('HĐLĐ');
    expect((screen.getByLabelText('Tiền tố mã thực tập') as HTMLInputElement).value).toBe('TT');
    expect(screen.getAllByText('mặc định: HĐLĐ').length).toBeGreaterThan(0);
    expect(screen.getAllByText('mặc định: HDDV').length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Chỉ áp dụng cho văn bản và nhân viên tạo mới; số và mã đã cấp giữ nguyên\./).length).toBe(2);
    // Bảng đặt số bắt đầu (DocumentNumbers) nằm dưới.
    expect(await screen.findByLabelText('Số bắt đầu HĐLĐ')).toBeTruthy();
  });

  it('gõ tiền tố mới thì dòng xem trước đổi theo, dùng số kế tiếp của năm hiện tại', async () => {
    render(<NumberingTab canEdit />);
    expect(await screen.findByText(`Số tiếp theo: DOIT/${YEAR}/HĐLĐ/13`)).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Tiền tố số văn bản'), { target: { value: 'ABC' } });
    expect(screen.getByText(`Số tiếp theo: ABC/${YEAR}/HĐLĐ/13`)).toBeTruthy();
  });

  it('Lưu gửi PUT: ô trống hoặc bằng mặc định gửi null, giá trị khác gửi nguyên', async () => {
    mocked.put.mockResolvedValue({ data: { ...NUMBERING, documentPrefix: 'ABC' } } as any);
    render(<NumberingTab canEdit />);
    const prefix = await screen.findByLabelText('Tiền tố số văn bản');
    await waitFor(() => expect((prefix as HTMLInputElement).value).toBe('DOIT'));
    fireEvent.change(prefix, { target: { value: 'ABC' } });
    fireEvent.change(screen.getByLabelText('Mã Hợp đồng thử việc'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('Mã Hợp đồng đào tạo'), { target: { value: 'DT1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu số văn bản' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/company-setting/numbering', {
        documentPrefix: 'ABC',
        documentCodes: { HDLD: null, HDTV: null, HDDT: 'DT1', HDDV: null, QD_CU_NHAN_SU: null },
        staffCodePrefix: null,
        internCodePrefix: null,
        serviceCodePrefix: null,
      }),
    );
    expect(await screen.findByText(/Đã lưu/)).toBeTruthy();
  });

  it('thẻ Mã nhân viên: lưu tiền tố riêng, các ô văn bản chưa lưu không bị gửi theo', async () => {
    mocked.put.mockResolvedValue({ data: { ...NUMBERING, serviceCodePrefix: 'DV' } } as any);
    render(<NumberingTab canEdit />);
    const svc = await screen.findByLabelText('Tiền tố mã HĐ dịch vụ');
    await waitFor(() => expect((svc as HTMLInputElement).value).toBe('HDDV'));
    fireEvent.change(screen.getByLabelText('Tiền tố số văn bản'), { target: { value: 'XYZ' } });
    fireEvent.change(svc, { target: { value: 'DV' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mã nhân viên' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalled());
    expect(mocked.put.mock.calls[0][1]).toMatchObject({ documentPrefix: null, serviceCodePrefix: 'DV' });
  });

  it('lỗi 400 trùng mã loại hiện câu tiếng Việt', async () => {
    mocked.put.mockRejectedValue({ response: { status: 400, data: { message: 'error.numberingdocduplicate' } } });
    render(<NumberingTab canEdit />);
    const code = await screen.findByLabelText('Mã Hợp đồng thử việc');
    await waitFor(() => expect((code as HTMLInputElement).value).toBe('HĐTV'));
    fireEvent.change(code, { target: { value: 'HĐLĐ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu số văn bản' }));
    expect(await screen.findByText('Mã các loại văn bản phải khác nhau.')).toBeTruthy();
  });

  it('HR chỉ xem: ô khóa, không có nút Lưu, không có ô sửa số bắt đầu', async () => {
    render(<NumberingTab canEdit={false} />);
    const prefix = (await screen.findByLabelText('Tiền tố số văn bản')) as HTMLInputElement;
    await waitFor(() => expect(prefix.value).toBe('DOIT'));
    expect(prefix.disabled).toBe(true);
    expect((screen.getByLabelText('Tiền tố mã nhân viên') as HTMLInputElement).disabled).toBe(true);
    expect(screen.queryByRole('button', { name: 'Lưu số văn bản' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Lưu mã nhân viên' })).toBeNull();
    expect(await screen.findByText(`DOIT/${YEAR}/HĐLĐ/13`)).toBeTruthy();
    expect(screen.queryByLabelText('Số bắt đầu HĐLĐ')).toBeNull();
  });
});
