import React from 'react';
import { Badge } from 'react-bootstrap';

/** Nhãn "Lưu trữ" cạnh tên người ở bảng lương, phiếu lương và tổng hợp kỳ cũ (hồ sơ đã chuyển vào vùng lưu trữ). */
export const ArchivedBadge = ({ archived }: { archived?: boolean | null }) =>
  archived ? (
    <Badge bg="secondary" className="ms-2" data-testid="archived-badge">
      Lưu trữ
    </Badge>
  ) : null;

export default ArchivedBadge;
