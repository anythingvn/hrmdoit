import React from 'react';
import { render, screen } from '@testing-library/react';

import { PageHeader } from './page-header';

describe('PageHeader', () => {
  it('hiện 3 gạch cam (ẩn với trình đọc màn hình), tiêu đề h1 và dòng phụ', () => {
    const { container } = render(<PageHeader title="Nghỉ phép" subtitle="Số ngày phép của bạn" />);
    expect(screen.getByRole('heading', { level: 1, name: 'Nghỉ phép' })).toBeTruthy();
    expect(screen.getByText('Số ngày phép của bạn')).toBeTruthy();
    const bars = container.querySelector('.doit-page-bars');
    expect(bars?.getAttribute('aria-hidden')).toBe('true');
    expect(bars?.querySelectorAll('span')).toHaveLength(3);
  });

  it('cụm hành động nằm cùng hàng tiêu đề; không có thì không vẽ khung rỗng', () => {
    const { container, rerender } = render(<PageHeader title="Nhân viên" actions={<button type="button">Thêm nhân viên</button>} />);
    expect(container.querySelector('.doit-page-actions')?.textContent).toBe('Thêm nhân viên');
    rerender(<PageHeader title="Nhân viên" />);
    expect(container.querySelector('.doit-page-actions')).toBeNull();
    expect(container.querySelector('.doit-page-subtitle')).toBeNull();
  });

  it('nhận id, data-cy và mức tiêu đề h2', () => {
    render(<PageHeader as="h2" title="Người dùng" titleId="user-heading" titleProps={{ 'data-cy': 'UserHeading' }} />);
    const h = screen.getByRole('heading', { level: 2, name: 'Người dùng' });
    expect(h.id).toBe('user-heading');
    expect(h.getAttribute('data-cy')).toBe('UserHeading');
  });
});
