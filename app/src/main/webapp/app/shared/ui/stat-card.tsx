import React from 'react';

import { hasContent } from './has-content';

export interface IStatCardProps {
  label: React.ReactNode;
  value: React.ReactNode;
  /** Dòng phụ nhạt dưới số. */
  sub?: React.ReactNode;
  /** Nội dung thêm (thanh tiến độ...). */
  children?: React.ReactNode;
  className?: string;
}

/** Thẻ số liệu: nhãn nhỏ, số lớn đậm, dòng phụ nhạt. Đặt trong `.doit-stats` để thành lưới. */
export const StatCard = ({ label, value, sub, children, className }: IStatCardProps) => (
  <div className={`doit-stat${className ? ` ${className}` : ''}`}>
    <span className="doit-stat-label">{label}</span>
    <span className="doit-stat-value">{value}</span>
    {hasContent(sub) && <span className="doit-stat-sub">{sub}</span>}
    {children}
  </div>
);

export default StatCard;
