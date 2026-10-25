import React from 'react';
import { render, screen } from '@testing-library/react';

import { StatusChip } from './status-chip';

describe('StatusChip', () => {
  it('gắn lớp màu theo tone', () => {
    render(<StatusChip tone="ok">Đã duyệt</StatusChip>);
    expect(screen.getByText('Đã duyệt').className).toBe('doit-chip doit-chip-ok');
  });

  it('mặc định màu xám, giữ lớp và thuộc tính truyền thêm', () => {
    render(
      <StatusChip className="ms-2" data-testid="chip" title="Hết hiệu lực">
        Đã kết thúc
      </StatusChip>,
    );
    const chip = screen.getByTestId('chip');
    expect(chip.className).toBe('doit-chip doit-chip-muted ms-2');
    expect(chip.getAttribute('title')).toBe('Hết hiệu lực');
  });
});
