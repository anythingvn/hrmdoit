import React from 'react';

/**
 * Màu chip theo canvas: ok (xanh: Chính thức, Hiệu lực, Đã duyệt), wait (vàng: Chờ duyệt),
 * warn (cam nhạt: Sắp hết hạn, Từ chối), info (xanh xám: Thử việc), muted (xám: Đã kết thúc), danger (cam đậm, nền đặc).
 */
export type ChipTone = 'ok' | 'wait' | 'warn' | 'info' | 'muted' | 'danger';

export interface IStatusChipProps extends React.HTMLAttributes<HTMLSpanElement> {
  tone?: ChipTone;
}

/** Nhãn trạng thái bo góc, chữ nhỏ đậm, nền nhạt theo màu. */
export const StatusChip = ({ tone = 'muted', className, children, ...rest }: IStatusChipProps) => (
  <span className={`doit-chip doit-chip-${tone}${className ? ` ${className}` : ''}`} {...rest}>
    {children}
  </span>
);

export default StatusChip;
