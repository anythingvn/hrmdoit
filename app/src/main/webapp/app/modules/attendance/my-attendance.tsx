import React, { useEffect, useState } from 'react';
import { Alert, Badge, Button, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';

import { IMonthBoard, attendanceApi, cellTitle, isAutoFilled, isRedCell, monthVn, previousMonth, shiftMonth } from './attendance-api';

const TOUCH = { minHeight: 44 };
const WEEKDAY = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

const fmt = (n: number | null | undefined) => (n === null || n === undefined ? '' : String(n).replace('.', ','));

const weekday = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return WEEKDAY[new Date(y, m - 1, d).getDay()];
};

/** "Bảng công của tôi": bảng tháng của chính mình, chỉ đọc. Tháng đã chốt thì số tổng là số đã chốt. */
export const MyAttendance = () => {
  const [month, setMonth] = useState<string>(() => previousMonth());
  const [board, setBoard] = useState<IMonthBoard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError(null);
    attendanceApi
      .my(month)
      .then(setBoard)
      .catch(err => {
        setBoard(null);
        setError(errorText(err, 'Không tải được bảng công'));
      });
  }, [month]);

  const row = board?.rows[0];
  const t = row?.totals;

  return (
    <div>
      <h2>Bảng công của tôi</h2>
      <div className="d-flex flex-wrap gap-2 align-items-center mb-3">
        <Button variant="outline-secondary" style={TOUCH} onClick={() => setMonth(shiftMonth(month, -1))}>
          ‹ Tháng trước
        </Button>
        <strong>Tháng {monthVn(month)}</strong>
        <Button variant="outline-secondary" style={TOUCH} onClick={() => setMonth(shiftMonth(month, 1))}>
          Tháng sau ›
        </Button>
        {board && (
          <Badge bg={board.status === 'DA_CHOT' ? 'success' : 'secondary'}>{board.status === 'DA_CHOT' ? 'Đã chốt' : 'Đang mở'}</Badge>
        )}
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {board && !row && <p className="text-muted">Bạn không có bảng công tháng {monthVn(board.month)}.</p>}
      {row && t && (
        <>
          {row.changedAfterClose && (
            <Alert variant="warning">Dữ liệu tháng này đã thay đổi sau khi chốt; số tổng dưới đây là số đã chốt.</Alert>
          )}
          <dl className="d-flex flex-wrap gap-4 mb-3" data-testid="my-totals">
            <div>
              <dt>Công chuẩn</dt>
              <dd>{fmt(t.standardDays)}</dd>
            </div>
            <div>
              <dt>Công thực tế</dt>
              <dd>
                <strong>{fmt(t.actualDays)}</strong>
              </dd>
            </div>
            <div>
              <dt>Đi muộn</dt>
              <dd>{t.lateCount > 0 ? `${t.lateCount} lần, ${t.lateMinutes} phút` : 'Không'}</dd>
            </div>
            <div>
              <dt>Ngày nửa công</dt>
              <dd>{t.halfDays}</dd>
            </div>
            <div>
              <dt>Ngày vắng</dt>
              <dd>{t.absentDays}</dd>
            </div>
            <div>
              <dt>Thiếu giờ / chưa nhập</dt>
              <dd className={t.missingDays > 0 ? 'text-danger' : undefined}>{t.missingDays}</dd>
            </div>
          </dl>
          <Table responsive size="sm" className="align-middle">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Vào</th>
                <th>Ra</th>
                <th>Công</th>
                <th>Ghi chú</th>
              </tr>
            </thead>
            <tbody>
              {row.days.map(v => (
                <tr key={v.day} data-testid={`my-day-${v.day}`} className={isRedCell(v) ? 'table-danger' : undefined}>
                  <td className="text-nowrap">
                    {isoToVn(v.day)} <span className="text-muted">{weekday(v.day)}</span>
                  </td>
                  <td>{isAutoFilled(v) ? '' : (v.in ?? '')}</td>
                  <td>{isAutoFilled(v) ? '' : (v.out ?? '')}</td>
                  <td>{v.autoKind === 'NGHI' || v.autoKind === 'NGOAI_HD' ? '' : fmt(v.actualCredit)}</td>
                  <td>{cellTitle(v)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </>
      )}
    </div>
  );
};

export default MyAttendance;
