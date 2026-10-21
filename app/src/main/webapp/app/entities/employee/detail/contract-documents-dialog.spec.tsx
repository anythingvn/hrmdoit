import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import ContractDocumentsDialog from './contract-documents-dialog';
import { IContract } from './detail-api';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const contract: IContract = { id: 7, type: 'HDLD_XDTH', contractNo: null, startDate: '2026-10-25', status: 'NHAP' };

const docx = { id: 31, kind: 'DOCX', fileName: 'DOIT-2026-HĐLĐ-1 Nguyễn Văn A.docx', createdAt: '2026-10-21T03:00:00Z' };
const pdf = { id: 32, kind: 'PDF', fileName: 'DOIT-2026-HĐLĐ-1 Nguyễn Văn A.pdf', createdAt: '2026-10-21T03:00:01Z', sourceDocumentId: 31 };

const renderDialog = (onChanged = vi.fn()) => {
  render(<ContractDocumentsDialog contract={contract} onClose={vi.fn()} onChanged={onChanged} />);
  return onChanged;
};

describe('ContractDocumentsDialog', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockResolvedValue({ data: [] } as any);
  });

  it('Tạo văn bản gọi đúng API, báo số đã cấp và tải lại danh sách', async () => {
    mocked.post.mockResolvedValue({ status: 201, data: { contractNo: 'DOIT/2026/HĐLĐ/1', docx, pdf, pdfError: null } } as any);
    const onChanged = renderDialog();
    await waitFor(() => expect(mocked.get).toHaveBeenCalledWith('api/employment-contracts/7/documents'));
    mocked.get.mockResolvedValue({ data: [pdf, docx] } as any);
    fireEvent.click(screen.getByRole('button', { name: 'Tạo văn bản' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/employment-contracts/7/documents', null, { params: { force: false } }),
    );
    expect(await screen.findByText('Đã tạo văn bản số DOIT/2026/HĐLĐ/1 (Word và PDF).')).toBeTruthy();
    expect(await screen.findByText('DOIT-2026-HĐLĐ-1 Nguyễn Văn A.pdf')).toBeTruthy();
    expect(onChanged).toHaveBeenCalled();
  });

  it('hồ sơ thiếu trường: hiện danh sách và nút Vẫn tạo', async () => {
    mocked.post.mockRejectedValueOnce({
      response: {
        status: 400,
        data: { message: 'error.missingfields', detail: 'Hồ sơ còn thiếu: Số CCCD, Nơi cư trú', missingFields: ['Số CCCD', 'Nơi cư trú'] },
      },
    });
    renderDialog();
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo văn bản' }));
    expect(await screen.findByText('Số CCCD')).toBeTruthy();
    expect(screen.getByText('Nơi cư trú')).toBeTruthy();
    mocked.post.mockResolvedValue({ status: 201, data: { contractNo: 'DOIT/2026/HĐLĐ/1', docx, pdf, pdfError: null } } as any);
    fireEvent.click(screen.getByRole('button', { name: 'Vẫn tạo' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenLastCalledWith('api/employment-contracts/7/documents', null, { params: { force: true } }),
    );
  });

  it('chưa tạo được PDF: cảnh báo và nút Tạo lại PDF', async () => {
    mocked.post.mockResolvedValueOnce({
      status: 201,
      data: { contractNo: 'DOIT/2026/HĐLĐ/1', docx, pdf: null, pdfError: 'Chưa tạo được PDF — dịch vụ chuyển đổi không phản hồi' },
    } as any);
    renderDialog();
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo văn bản' }));
    expect(await screen.findByText(/Chưa tạo được PDF — dịch vụ chuyển đổi không phản hồi/)).toBeTruthy();
    mocked.post.mockResolvedValue({ status: 200, data: { contractNo: 'DOIT/2026/HĐLĐ/1', docx, pdf, pdfError: null } } as any);
    fireEvent.click(screen.getByRole('button', { name: 'Tạo lại PDF' }));
    await waitFor(() => expect(mocked.post).toHaveBeenLastCalledWith('api/employment-contracts/7/documents/pdf'));
    expect(await screen.findByText('Đã tạo PDF.')).toBeTruthy();
  });

  it('bản Word mới nhất chưa có PDF thì cũng có nút Tạo lại PDF', async () => {
    mocked.get.mockResolvedValue({ data: [docx] } as any);
    renderDialog();
    expect(await screen.findByRole('button', { name: 'Tạo lại PDF' })).toBeTruthy();
  });

  it('HĐ nháp: Đánh dấu đã ký', async () => {
    mocked.post.mockResolvedValue({ status: 200, data: { ...contract, status: 'DA_KY' } } as any);
    const onChanged = renderDialog();
    fireEvent.click(await screen.findByRole('button', { name: 'Đánh dấu đã ký' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/employment-contracts/7/sign'));
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
  });

  it('tải bản đã ký lên: chỉ nhận PDF', async () => {
    renderDialog();
    const input = await screen.findByLabelText('Tải bản đã ký lên (PDF)');
    fireEvent.change(input, { target: { files: [new File(['x'], 'scan.jpg', { type: 'image/jpeg' })] } });
    expect(await screen.findByText('Bản đã ký phải là file PDF')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
    mocked.post.mockResolvedValue({ status: 201, data: { id: 40, kind: 'SIGNED', fileName: 'ky.pdf' } } as any);
    fireEvent.change(input, { target: { files: [new File(['%PDF-'], 'ky.pdf', { type: 'application/pdf' })] } });
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/employment-contracts/7/signed', expect.any(FormData)));
  });
});
