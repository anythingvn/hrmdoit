import React from 'react';
import { render, screen } from '@testing-library/react';

import { SectionCard } from './section-card';
import { StatCard } from './stat-card';

describe('SectionCard', () => {
  it('là vùng có tên theo tiêu đề, link phụ bên phải', () => {
    render(
      <SectionCard title="Đơn chờ duyệt" titleId="h-approve" actions={<a href="/duyet-don">Xem tất cả</a>}>
        <p>Nội dung</p>
      </SectionCard>,
    );
    expect(screen.getByRole('region', { name: 'Đơn chờ duyệt' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Xem tất cả' })).toBeTruthy();
  });

  it('thẻ tối và không đệm', () => {
    const { container } = render(<SectionCard dark flush as="div" />);
    expect(container.firstElementChild?.className).toBe('doit-card doit-card-dark doit-card-flush');
    expect(container.querySelector('.doit-card-head')).toBeNull();
  });
});

describe('StatCard', () => {
  it('nhãn, số, dòng phụ', () => {
    render(<StatCard label="Tổng nhân sự" value={98} sub="+2 so với tháng 9" />);
    expect(screen.getByText('Tổng nhân sự').className).toBe('doit-stat-label');
    expect(screen.getByText('98').className).toBe('doit-stat-value');
    expect(screen.getByText('+2 so với tháng 9').className).toBe('doit-stat-sub');
  });
});
