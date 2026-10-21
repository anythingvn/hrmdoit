import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Badge, Button, Form } from 'react-bootstrap';
import { Link } from 'react-router';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';

import { ProbationResultDialog, ReviewDateDialog } from './quick-actions';

export type ReminderType =
  | 'CONTRACT_EXPIRY'
  | 'CONTRACT_OVERDUE'
  | 'PROBATION_END'
  | 'PROBATION_RESULT_MISSING'
  | 'SALARY_REVIEW'
  | 'SALARY_REVIEW_OVERDUE'
  | 'BIRTHDAY';

export interface IUpcoming {
  type: ReminderType;
  typeLabel: string;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  eventDate: string;
  daysLeft: number;
  urgent: boolean;
  message: string;
  contractId?: number | null;
  salaryChangeId?: number | null;
}

const TYPE_FILTER: [ReminderType, string][] = [
  ['CONTRACT_EXPIRY', 'Hết hạn hợp đồng'],
  ['CONTRACT_OVERDUE', 'Hợp đồng đã hết hạn, chưa ký mới'],
  ['PROBATION_END', 'Hết thời gian thử việc'],
  ['PROBATION_RESULT_MISSING', 'Chưa có kết quả thử việc'],
  ['SALARY_REVIEW', 'Đến hạn xét tăng lương'],
  ['SALARY_REVIEW_OVERDUE', 'Quá hạn xét tăng lương'],
  ['BIRTHDAY', 'Sinh nhật'],
];

const PROBATION: ReminderType[] = ['PROBATION_END', 'PROBATION_RESULT_MISSING'];
const SALARY: ReminderType[] = ['SALARY_REVIEW', 'SALARY_REVIEW_OVERDUE'];

const dueLabel = (daysLeft: number) => (daysLeft < 0 ? `Quá hạn ${-daysLeft} ngày` : daysLeft === 0 ? 'Hôm nay' : `Còn ${daysLeft} ngày`);

type Dialog = { kind: 'probation' | 'review'; id: number; employeeName: string } | null;

/** Việc sắp đến hạn trong 30 ngày (và việc đã quá hạn); Quản trị, HR xử lý nhanh ngay tại đây. */
export const ReminderList = ({ canAct }: { canAct: boolean }) => {
  const [type, setType] = useState('');
  const [items, setItems] = useState<IUpcoming[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog>(null);

  const load = () =>
    axios
      .get<IUpcoming[]>('api/reminders/upcoming', { params: type ? { type } : {} })
      .then(r => {
        setItems(r.data ?? []);
        setError(null);
      })
      .catch(err => setError(errorText(err, 'Không tải được nhắc việc')));

  useEffect(() => {
    load();
  }, [type]);

  const open = (kind: 'probation' | 'review', id: number | null | undefined, employeeName: string) => {
    if (id != null) setDialog({ kind, id, employeeName });
  };

  const saved = (text: string) => {
    setDialog(null);
    setNotice(text);
    load();
  };

  return (
    <section aria-labelledby="reminders-title" className="mb-4">
      <div className="d-flex flex-wrap align-items-end justify-content-between gap-2 mb-2">
        <h2 className="h5 mb-0" id="reminders-title">
          Nhắc việc sắp tới
        </h2>
        <Form.Group controlId="reminder-type" className="d-flex align-items-center gap-2">
          <Form.Label className="mb-0">Loại việc</Form.Label>
          <Form.Select size="sm" value={type} onChange={e => setType(e.target.value)} style={{ width: 'auto' }}>
            <option value="">Tất cả</option>
            {TYPE_FILTER.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      {items?.length === 0 && <p className="text-muted">Không có việc nào trong 30 ngày tới.</p>}
      <ul className="list-unstyled reminder-list">
        {(items ?? []).map(r => (
          <li key={`${r.type}-${r.employeeCode}-${r.eventDate}`} className="border rounded p-2 mb-2">
            <div className="d-flex flex-wrap align-items-center gap-2">
              <Badge
                bg={r.daysLeft < 0 ? 'danger' : r.urgent ? 'warning' : 'secondary'}
                text={r.urgent && r.daysLeft >= 0 ? 'dark' : undefined}
              >
                {dueLabel(r.daysLeft)}
              </Badge>
              <span className="fw-semibold">{r.typeLabel}</span>
              <Link to={`/nhan-vien/${r.employeeId}`}>{r.employeeName}</Link>
              <span className="text-muted">
                ({r.employeeCode}) · {isoToVn(r.eventDate)}
              </span>
            </div>
            <p className="mb-1 small">{r.message}</p>
            {canAct && (
              <div className="d-flex flex-wrap gap-2">
                {PROBATION.includes(r.type) && r.contractId != null ? (
                  <Button size="sm" variant="outline-primary" onClick={() => open('probation', r.contractId, r.employeeName)}>
                    Ghi kết quả thử việc
                  </Button>
                ) : null}
                {SALARY.includes(r.type) && r.salaryChangeId != null ? (
                  <Button size="sm" variant="outline-primary" onClick={() => open('review', r.salaryChangeId, r.employeeName)}>
                    Hẹn xét lại
                  </Button>
                ) : null}
                {SALARY.includes(r.type) && (
                  <Link className="btn btn-sm btn-outline-secondary" to={`/nhan-vien/${r.employeeId}?tab=contracts`}>
                    Thêm điều chỉnh lương
                  </Link>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
      {dialog?.kind === 'probation' && (
        <ProbationResultDialog
          contractId={dialog.id}
          employeeName={dialog.employeeName}
          onClose={() => setDialog(null)}
          onSaved={() => saved(`Đã ghi kết quả thử việc của ${dialog.employeeName}.`)}
        />
      )}
      {dialog?.kind === 'review' && (
        <ReviewDateDialog
          salaryChangeId={dialog.id}
          employeeName={dialog.employeeName}
          onClose={() => setDialog(null)}
          onSaved={() => saved(`Đã hẹn ngày xét lại lương của ${dialog.employeeName}.`)}
        />
      )}
    </section>
  );
};

export default ReminderList;
