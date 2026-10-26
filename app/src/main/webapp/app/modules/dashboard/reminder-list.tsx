import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button } from 'react-bootstrap';
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

/** Nhóm lọc dạng viên thuốc (canvas): Hợp đồng / Thử việc / Lương / Sinh nhật. */
export type ReminderGroup = 'CONTRACT' | 'PROBATION' | 'SALARY' | 'BIRTHDAY';

const GROUP_OF: Record<ReminderType, ReminderGroup> = {
  CONTRACT_EXPIRY: 'CONTRACT',
  CONTRACT_OVERDUE: 'CONTRACT',
  PROBATION_END: 'PROBATION',
  PROBATION_RESULT_MISSING: 'PROBATION',
  SALARY_REVIEW: 'SALARY',
  SALARY_REVIEW_OVERDUE: 'SALARY',
  BIRTHDAY: 'BIRTHDAY',
};

const GROUPS: [ReminderGroup, string][] = [
  ['CONTRACT', 'Hợp đồng'],
  ['PROBATION', 'Thử việc'],
  ['SALARY', 'Lương'],
  ['BIRTHDAY', 'Sinh nhật'],
];

const GROUP_LABEL = Object.fromEntries(GROUPS) as Record<ReminderGroup, string>;

const PROBATION: ReminderType[] = ['PROBATION_END', 'PROBATION_RESULT_MISSING'];
const SALARY: ReminderType[] = ['SALARY_REVIEW', 'SALARY_REVIEW_OVERDUE'];

const dueLabel = (daysLeft: number) => (daysLeft < 0 ? `Quá hạn ${-daysLeft} ngày` : daysLeft === 0 ? 'Hôm nay' : `Còn ${daysLeft} ngày`);

type Dialog = { kind: 'probation' | 'review'; id: number; employeeName: string } | null;

/** Việc sắp đến hạn trong 30 ngày (và việc đã quá hạn); Quản trị, HR xử lý nhanh ngay tại đây. */
export const ReminderList = ({ canAct, onCount }: { canAct: boolean; onCount?: (n: number | null) => void }) => {
  const [group, setGroup] = useState<ReminderGroup | ''>('');
  const [items, setItems] = useState<IUpcoming[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog>(null);

  const load = () =>
    axios
      .get<IUpcoming[]>('api/reminders/upcoming', { params: {} })
      .then(r => {
        setItems(r.data ?? []);
        setError(null);
        onCount?.((r.data ?? []).length);
      })
      .catch(err => {
        setError(errorText(err, 'Không tải được nhắc việc'));
        onCount?.(null);
      });

  useEffect(() => {
    load();
  }, []);

  const shown = (items ?? []).filter(r => !group || GROUP_OF[r.type] === group);

  const open = (kind: 'probation' | 'review', id: number | null | undefined, employeeName: string) => {
    if (id != null) setDialog({ kind, id, employeeName });
  };

  const saved = (text: string) => {
    setDialog(null);
    setNotice(text);
    load();
  };

  const pill = (value: ReminderGroup | '', label: string) => (
    <button key={value || 'all'} type="button" className="btn doit-pill" aria-pressed={group === value} onClick={() => setGroup(value)}>
      {label}
    </button>
  );

  return (
    <section aria-labelledby="reminders-title" className="doit-card">
      <div className="doit-card-head">
        <h2 className="doit-card-title" id="reminders-title">
          Nhắc việc sắp tới
        </h2>
        <div className="d-flex flex-wrap gap-2" role="group" aria-label="Lọc theo loại việc">
          {pill('', 'Tất cả')}
          {GROUPS.map(([v, l]) => pill(v, l))}
        </div>
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      {items?.length === 0 && <p className="text-muted mb-0">Không có việc nào trong 30 ngày tới.</p>}
      {items && items.length > 0 && shown.length === 0 && <p className="text-muted mb-0">Không có việc loại này trong 30 ngày tới.</p>}
      <ul className="list-unstyled reminder-list mb-0">
        {shown.map(r => (
          <li key={`${r.type}-${r.employeeCode}-${r.eventDate}`} className="reminder-item">
            <span className={`reminder-kind reminder-kind-${GROUP_OF[r.type].toLowerCase()}`} title={r.typeLabel}>
              {GROUP_LABEL[GROUP_OF[r.type]]}
            </span>
            <div className="reminder-body">
              <span className="reminder-who">
                <Link to={`/nhan-vien/${r.employeeId}`}>{r.employeeName}</Link>
                <span className="text-muted fw-normal"> · {r.employeeCode}</span>
              </span>
              <span className="reminder-message">{r.message}</span>
              {canAct && (PROBATION.includes(r.type) || SALARY.includes(r.type)) && (
                <div className="d-flex flex-wrap gap-2 mt-1">
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
            </div>
            <span className="reminder-when">
              <span className="reminder-date">{isoToVn(r.eventDate)}</span>
              <span
                className={r.daysLeft < 0 ? 'reminder-due reminder-overdue' : r.urgent ? 'reminder-due reminder-urgent' : 'reminder-due'}
              >
                {dueLabel(r.daysLeft)}
              </span>
            </span>
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
