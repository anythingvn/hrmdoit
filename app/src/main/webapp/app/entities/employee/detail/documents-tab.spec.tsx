import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';

import { makeStore } from '../test-utils';
import DocumentsTab, { MAX_DOCUMENT_BYTES } from './documents-tab';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderTab = () =>
  render(
    <Provider store={makeStore(['ROLE_HR'])}>
      <DocumentsTab employeeId={7} />
    </Provider>,
  );

const fileOfSize = (name: string, type: string, size: number) => {
  const file = new File(['x'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
};

describe('DocumentsTab (Hồ sơ scan)', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockResolvedValue({ data: [] } as any);
  });

  it('chặn file lớn hơn 20 MB ngay ở trình duyệt, báo tiếng Việt, không gửi lên máy chủ', async () => {
    renderTab();
    await screen.findByText('Chưa có hồ sơ');
    const input = document.getElementById('document-file') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [fileOfSize('scan.pdf', 'application/pdf', MAX_DOCUMENT_BYTES + 1)] } });
    expect((await screen.findByRole('alert')).textContent).toContain('vượt quá 20 MB');
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('file đúng 20 MB vẫn được tải lên', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderTab();
    await screen.findByText('Chưa có hồ sơ');
    const input = document.getElementById('document-file') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [fileOfSize('scan.pdf', 'application/pdf', MAX_DOCUMENT_BYTES)] } });
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/employees/7/documents', expect.any(FormData)));
  });
});
