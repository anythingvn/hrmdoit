import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import DocumentTemplates from './document-templates';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const kinds = [
  {
    kind: 'HDLD',
    label: 'Hợp đồng lao động',
    activeVersion: 1,
    hasDefault: true,
    allowedTags: ['hoTen', 'soHopDong'],
    versions: [
      { id: 5, kind: 'HDLD', version: 1, fileName: 'HĐLĐ 2026.docx', uploadedBy: 'hr1', uploadedAt: '2026-10-20T03:00:00Z', active: true },
    ],
  },
  { kind: 'HDTV', label: 'Hợp đồng thử việc', activeVersion: null, hasDefault: false, allowedTags: ['hoTen'], versions: [] },
];

const docxFile = (name = 'mau.docx') =>
  new File(['PK'], name, { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' });

describe('DocumentTemplates', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockResolvedValue({ data: kinds } as any);
  });

  it('hiện phiên bản đang dùng của từng loại mẫu', async () => {
    render(<DocumentTemplates />);
    const hdld = await screen.findByTestId('template-HDLD');
    expect(within(hdld).getByText('Hợp đồng lao động')).toBeTruthy();
    expect(within(hdld).getByText('Đang dùng: phiên bản 1 — HĐLĐ 2026.docx')).toBeTruthy();
    expect(
      within(screen.getByTestId('template-HDTV')).getByText('Chưa có mẫu — tải mẫu Word lên trước khi tạo văn bản loại này'),
    ).toBeTruthy();
  });

  it('tải mẫu mới lên rồi tải lại danh sách', async () => {
    mocked.post.mockResolvedValue({ status: 201, data: {} } as any);
    render(<DocumentTemplates />);
    const hdld = await screen.findByTestId('template-HDLD');
    fireEvent.change(within(hdld).getByLabelText('Tải mẫu mới Hợp đồng lao động'), { target: { files: [docxFile()] } });
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/document-templates/HDLD', expect.any(FormData)));
    await waitFor(() => expect(mocked.get).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Đã nhận mẫu mới cho Hợp đồng lao động.')).toBeTruthy();
  });

  it('mẫu có thẻ bị cắt thì hiện câu báo của máy chủ', async () => {
    mocked.post.mockRejectedValue({
      response: {
        status: 400,
        data: { message: 'error.templatebrokentags', detail: 'Thẻ bị cắt: {{hoTen}}. Trong Word, xóa thẻ rồi gõ lại' },
      },
    });
    render(<DocumentTemplates />);
    const hdld = await screen.findByTestId('template-HDLD');
    fireEvent.change(within(hdld).getByLabelText('Tải mẫu mới Hợp đồng lao động'), { target: { files: [docxFile()] } });
    expect(await screen.findByText('Thẻ bị cắt: {{hoTen}}. Trong Word, xóa thẻ rồi gõ lại')).toBeTruthy();
  });

  it('chọn file không phải .docx thì báo ngay, không gửi', async () => {
    render(<DocumentTemplates />);
    const hdld = await screen.findByTestId('template-HDLD');
    fireEvent.change(within(hdld).getByLabelText('Tải mẫu mới Hợp đồng lao động'), { target: { files: [docxFile('mau.doc')] } });
    expect(await screen.findByText('Chỉ nhận file Word .docx (Word 2007 trở lên)')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('quay lại mẫu gốc', async () => {
    mocked.post.mockResolvedValue({ status: 200 } as any);
    render(<DocumentTemplates />);
    const hdld = await screen.findByTestId('template-HDLD');
    fireEvent.click(within(hdld).getByRole('button', { name: 'Dùng mẫu gốc' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/document-templates/HDLD/versions/0/activate'));
  });
});
