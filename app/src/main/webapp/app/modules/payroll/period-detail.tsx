import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Badge, Button, Form, Table } from 'react-bootstrap';
import { Link, useParams } from 'react-router';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { downloadFile } from 'app/shared/util/download';

import { IIssue, PERIOD_STATUS_LABEL, PeriodStatus, money, periodText } from './payroll-api';

interface IVersion {
  version: number;
  importedBy?: string | null;
  importedAt: string;
  fileName: string;
  headcount: number;
  totalNet: number;
  totalCost: number;
  rules?: { effectiveFrom: string; estimated: boolean; personalDeduction?: number } | null;
  issues: IIssue[];
}

interface IDetail {
  period: string;
  status: PeriodStatus;
  officialVersion?: number | null;
  approvedVersion?: number | null;
  approvedBy?: string | null;
  versions: IVersion[];
  events: { action: 'DUYET' | 'MO_LAI'; version?: number | null; actor?: string | null; at: string; reason?: string | null }[];
}

interface ILine {
  excelRow: number;
  employeeCode: string;
  employeeName: string;
  fileName: string;
  fileCode?: string | null;
  mappedManually: boolean;
  amounts: Record<string, number>;
  thucNhan: number;
  tongChiPhi: number;
  thueTncn: number;
  tongBhNld: number;
}

const dateText = (iso: string) => new Date(iso).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });

/** Chi tiết một kỳ lương: các phiên bản, luật và cảnh báo lúc xác nhận (bản chụp), dòng lương; Quản trị duyệt / mở lại. */
export const PeriodDetail = ({ canApprove }: { canApprove: boolean }) => {
  const { period } = useParams<'period'>();
  const [detail, setDetail] = useState<IDetail | null>(null);
  const [version, setVersion] = useState<number | null>(null);
  const [lines, setLines] = useState<ILine[]>([]);
  const [fields, setFields] = useState<{ field: string; label: string }[]>([]);
  const [showAll, setShowAll] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reopening, setReopening] = useState(false);
  const [reason, setReason] = useState('');

  const load = () =>
    axios
      .get<IDetail>(`api/payroll/periods/${period}`)
      .then(r => {
        setDetail(r.data);
        setVersion(v => v ?? r.data.officialVersion ?? null);
      })
      .catch(err => setError(errorText(err, 'Không tải được kỳ lương')));

  useEffect(() => {
    load();
    axios
      .get<{ field: string; label: string }[]>('api/payroll/fields')
      .then(r => setFields(r.data ?? []))
      .catch(() => undefined);
  }, [period]);

  useEffect(() => {
    if (version == null) return;
    axios
      .get<ILine[]>(`api/payroll/periods/${period}/versions/${version}/lines`)
      .then(r => setLines(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được dòng lương')));
  }, [period, version]);

  const approve = async () => {
    setError(null);
    try {
      await axios.post(`api/payroll/periods/${period}/approve`);
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  };

  const reopen = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setError(null);
    try {
      await axios.post(`api/payroll/periods/${period}/reopen`, { reason: reason.trim() });
      setReopening(false);
      setReason('');
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  };

  if (!detail) return error ? <Alert variant="danger">{error}</Alert> : <p>Đang tải...</p>;
  const v = detail.versions.find(x => x.version === version);

  return (
    <div>
      <div className="d-flex flex-wrap gap-2 align-items-center mb-2">
        <h2 className="h4 mb-0">Kỳ lương {periodText(detail.period)}</h2>
        <Badge bg={detail.status === 'DA_DUYET' ? 'success' : 'secondary'}>{PERIOD_STATUS_LABEL[detail.status]}</Badge>
        {canApprove && detail.status === 'NHAP' && detail.versions.length > 0 && <Button onClick={approve}>Duyệt kỳ</Button>}
        {canApprove && detail.status === 'DA_DUYET' && (
          <Button variant="outline-danger" onClick={() => setReopening(true)}>
            Mở lại
          </Button>
        )}
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {reopening && (
        <Form onSubmit={reopen} className="mb-3">
          <Form.Group controlId="reopen-reason">
            <Form.Label>Lý do mở lại</Form.Label>
            <Form.Control value={reason} onChange={e => setReason(e.target.value)} />
          </Form.Group>
          <div className="d-flex gap-2 mt-2">
            <Button type="submit" variant="danger">
              Xác nhận mở lại
            </Button>
            <Button variant="outline-secondary" onClick={() => setReopening(false)}>
              Hủy
            </Button>
          </div>
        </Form>
      )}
      {detail.versions.length > 1 && (
        <Form.Group controlId="payroll-version" className="mb-2" style={{ maxWidth: 260 }}>
          <Form.Label>Phiên bản</Form.Label>
          <Form.Select value={version ?? ''} onChange={e => setVersion(Number(e.target.value))}>
            {detail.versions.map(x => (
              <option key={x.version} value={x.version}>
                Phiên bản {x.version}
                {x.version === detail.officialVersion ? ' (chính thức)' : ''}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
      )}
      {v && (
        <div className="mb-3">
          <div>
            Phiên bản {v.version} · import bởi {v.importedBy ?? '—'} lúc {dateText(v.importedAt)} ·{' '}
            <Button
              variant="link"
              className="p-0 align-baseline"
              onClick={() => downloadFile(`api/payroll/periods/${period}/versions/${v.version}/file`, v.fileName)}
            >
              Tải file gốc
            </Button>
          </div>
          <div>
            {v.headcount} người · Thực nhận <strong>{money(v.totalNet)}</strong> · Tổng chi phí <strong>{money(v.totalCost)}</strong>
          </div>
          <div className="text-muted">
            {v.rules
              ? `Luật áp dụng: mốc ${periodText(v.rules.effectiveFrom)}${v.rules.estimated ? ' (ước tính)' : ''}`
              : 'Kỳ này chưa có mức luật định để đối chiếu'}
          </div>
          {v.issues.length > 0 && (
            <details className="mt-1">
              <summary>Cảnh báo lúc xác nhận ({v.issues.length})</summary>
              <ul className="small mb-0">
                {v.issues.map((i, k) => (
                  <li key={k}>
                    {i.excelRow > 0 ? `Dòng ${i.excelRow}: ` : ''}
                    {i.message}
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
      <Form.Check id="show-all" className="mb-2" label="Hiện đủ 36 khoản" checked={showAll} onChange={() => setShowAll(s => !s)} />
      <Table size="sm" responsive striped>
        <thead>
          <tr>
            <th scope="col">Dòng</th>
            <th scope="col">Nhân viên</th>
            <th scope="col" className="text-end">
              Thực nhận
            </th>
            <th scope="col" className="text-end">
              Tổng chi phí
            </th>
            <th scope="col" className="text-end">
              Thuế TNCN
            </th>
            <th scope="col" className="text-end">
              BH người lao động
            </th>
            {showAll &&
              fields.map(f => (
                <th key={f.field} scope="col" className="text-end">
                  {f.label}
                </th>
              ))}
          </tr>
        </thead>
        <tbody>
          {lines.map(l => (
            <tr key={l.excelRow}>
              <td>{l.excelRow}</td>
              <td>
                {l.employeeCode} – {l.employeeName}
                {l.mappedManually && <div className="small text-muted">Gắn tay (trong file: {l.fileCode ?? 'không có mã'})</div>}
              </td>
              <td className="text-end">{money(l.thucNhan)}</td>
              <td className="text-end">{money(l.tongChiPhi)}</td>
              <td className="text-end">{money(l.thueTncn)}</td>
              <td className="text-end">{money(l.tongBhNld)}</td>
              {showAll &&
                fields.map(f => (
                  <td key={f.field} className="text-end">
                    {money(l.amounts[f.field])}
                  </td>
                ))}
            </tr>
          ))}
        </tbody>
      </Table>
      {detail.events.length > 0 && (
        <section className="mt-3">
          <h3 className="h6">Lịch sử duyệt</h3>
          <ul className="small">
            {detail.events.map((e, k) => (
              <li key={k}>
                {dateText(e.at)}: {e.action === 'DUYET' ? 'Duyệt' : 'Mở lại'} phiên bản {e.version ?? '—'} ({e.actor ?? '—'})
                {e.reason ? ` – ${e.reason}` : ''}
              </li>
            ))}
          </ul>
        </section>
      )}
      <Link to="/bang-luong">Về danh sách kỳ lương</Link>
    </div>
  );
};

export default PeriodDetail;
