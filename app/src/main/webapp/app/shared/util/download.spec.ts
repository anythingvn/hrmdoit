import { vi } from 'vitest';
import axios from 'axios';

import { downloadFile } from './download';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

describe('downloadFile', () => {
  it('lỗi nghiệp vụ trả JSON trong blob: đọc lại thành object để hiện câu lỗi', async () => {
    const blob = new Blob([JSON.stringify({ message: 'error.attendancenotclosed' })], { type: 'application/problem+json' });
    mocked.get.mockRejectedValue({ response: { status: 400, data: blob } });
    await expect(downloadFile('api/x.xlsx', 'x.xlsx')).rejects.toMatchObject({
      response: { data: { message: 'error.attendancenotclosed' } },
    });
  });
});
