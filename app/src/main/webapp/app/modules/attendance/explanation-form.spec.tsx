import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import ExplanationForm from './explanation-form';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const setup = (props: Record<string, unknown> = {}) => {
  const onClose = vi.fn();
  const onDone = vi.fn();
  render(<ExplanationForm day="2026-10-13" onClose={onClose} onDone={onDone} {...props} />);
  return { onClose, onDone };
};
const type = (label: RegExp, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Gửi giải trình' }));

describe('ExplanationForm', () => {
  beforeEach(() => {
    mocked.post.mockReset();
  });

  it('lý do bắt buộc', () => {
    setup();
    type(/Giờ ra/, '17:12');
    submit();
    expect(screen.getByText('Cần nhập lý do giải trình')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('cần ít nhất một giờ', () => {
    setup();
    type(/Lý do/, 'Quên chấm');
    submit();
    expect(screen.getByText('Nhập giờ vào hoặc giờ ra đề xuất')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('giờ phải dạng HH:mm', () => {
    setup();
    type(/Giờ vào/, '25:00');
    type(/Lý do/, 'Quên chấm');
    submit();
    expect(screen.getByText(/dạng HH:mm/)).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('gửi: POST explanations với ngày, giờ, lý do', async () => {
    mocked.post.mockResolvedValue({ data: { id: 5 } } as any);
    const { onDone } = setup();
    expect(screen.getByText(/Giải trình 13\/10\/2026/)).toBeTruthy();
    type(/Giờ ra/, '17:12');
    type(/Lý do/, '  Quên bấm chấm ra  ');
    submit();
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/attendance/explanations', {
        workDay: '2026-10-13',
        proposedIn: null,
        proposedOut: '17:12',
        reason: 'Quên bấm chấm ra',
      }),
    );
    await waitFor(() => expect(onDone).toHaveBeenCalled());
  });

  it('server trả explanationpending: hiện câu tiếng Việt, không đóng', async () => {
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.explanationpending' } } });
    const { onDone } = setup();
    type(/Giờ vào/, '08:00');
    type(/Lý do/, 'Quên');
    submit();
    expect(await screen.findByText('Ngày này đã có giải trình đang chờ duyệt; rút giải trình đó trước khi gửi lại')).toBeTruthy();
    expect(onDone).not.toHaveBeenCalled();
  });
});
