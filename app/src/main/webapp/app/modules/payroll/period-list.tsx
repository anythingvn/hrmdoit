import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form, Table } from 'react-bootstrap';
import { Link } from 'react-router';

import { errorText } from 'app/entities/employee/detail/detail-api';
import MonthInput, { monthsAgo } from 'app/shared/month-input';
import { StatusChip } from 'app/shared/ui';
import { downloadFile } from 'app/shared/util/download';

import { PERIOD_STATUS_LABEL, PeriodStatus, money, periodText } from './payroll-api';

interface IPeriodSummary {
  period: string;
  status: PeriodStatus;
  officialVersion?: number | null;
  latestVersion?: number | null;
  headcount: number;
  totalNet: number;
  totalCost: number;
}

interface IDraft {
  id: number;
  period: string;
  fileName: string;
  createdBy?: string | null;
}

/** Danh sách kỳ lương, các bản nháp import đang dở và nút tải file bảng lương có ngày công theo tháng. */
export const PeriodList = () => {
  const [periods, setPeriods] = useState<IPeriodSummary[]>([]);
  const [drafts, setDrafts] = useState<IDraft[]>([]);
  const [error, setError] = useState<string | null>(null);
  // Bảng lương thường làm cho tháng trước.
  const [month, setMonth] = useState(() => monthsAgo(1));
  const [downloadError, setDownloadError] = useState<string | null>(null);

  useEffect(() => {
    axios
      .get<IPeriodSummary[]>('api/payroll/periods')
      .then(r => setPeriods(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được danh sách kỳ lương')));
    axios
      .get<IDraft[]>('api/payroll/imports', { params: { status: 'DANG_LAM' } })
      .then(r => setDrafts(r.data ?? []))
      .catch(() => undefined);
  }, []);

  const downloadPrefilled = () => {
    setDownloadError(null);
    if (!/^\d{4}-\d{2}$/.test(month)) {
      setDownloadError('Nhập tháng dạng MM/yyyy, ví dụ 11/2026');
      return;
    }
    downloadFile('api/payroll/imports/template.xlsx', `bang-luong-${month}.xlsx`, { period: month }).catch(err =>
      setDownloadError(errorText(err, 'Không tải được file bảng lương')),
    );
  };

  return (
    <div className="doit-card">
      <Link to="/bang-luong/import/moi" className="btn btn-primary mb-3">
        Import bảng lương
      </Link>
      <section className="mb-3" aria-label="File bảng lương có ngày công">
        <div className="d-flex flex-wrap gap-2 align-items-end">
          <Form.Group controlId="prefill-month" style={{ maxWidth: 180 }}>
            <Form.Label>Tháng (MM/yyyy)</Form.Label>
            <MonthInput value={month} onChange={setMonth} />
          </Form.Group>
          <Button variant="outline-primary" style={{ minHeight: 44 }} onClick={downloadPrefilled}>
            Tải file bảng lương có ngày công
          </Button>
        </div>
        <div className="form-text">Điền sẵn danh sách và ngày công từ bảng công đã chốt; điền phần còn lại rồi import như thường.</div>
        {downloadError && (
          <Alert variant="danger" className="mt-2 mb-0">
            {downloadError}
          </Alert>
        )}
      </section>
      {error && <Alert variant="danger">{error}</Alert>}
      {drafts.length > 0 && (
        <Alert variant="info">
          Bản nháp đang dở:
          <ul className="mb-0">
            {drafts.map(d => (
              <li key={d.id}>
                <Link to={`/bang-luong/import/${d.id}`}>
                  Kỳ {periodText(d.period)} – {d.fileName}
                </Link>
                {d.createdBy ? ` (${d.createdBy})` : ''}
              </li>
            ))}
          </ul>
        </Alert>
      )}
      <Table responsive>
        <thead>
          <tr>
            <th scope="col">Kỳ</th>
            <th scope="col">Trạng thái</th>
            <th scope="col">Phiên bản</th>
            <th scope="col" className="text-end">
              Số người
            </th>
            <th scope="col" className="text-end">
              Thực nhận
            </th>
            <th scope="col" className="text-end">
              Tổng chi phí
            </th>
          </tr>
        </thead>
        <tbody>
          {periods.length === 0 && (
            <tr>
              <td colSpan={6}>Chưa có kỳ lương nào</td>
            </tr>
          )}
          {periods.map(p => (
            <tr key={p.period}>
              <td>
                <Link to={`/bang-luong/${p.period}`}>{periodText(p.period)}</Link>
              </td>
              <td>
                <StatusChip tone={p.status === 'DA_DUYET' ? 'ok' : 'muted'}>{PERIOD_STATUS_LABEL[p.status]}</StatusChip>
              </td>
              <td>
                {p.officialVersion ?? '—'}
                {p.latestVersion && p.latestVersion !== p.officialVersion ? ` (mới nhất ${p.latestVersion})` : ''}
              </td>
              <td className="text-end">{p.headcount}</td>
              <td className="text-end">{money(p.totalNet)}</td>
              <td className="text-end">{money(p.totalCost)}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    </div>
  );
};

export default PeriodList;
