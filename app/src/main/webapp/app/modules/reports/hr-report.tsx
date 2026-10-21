import React, { useState } from 'react';
import { Alert, Button, Form } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import MonthInput, { monthsAgo } from 'app/shared/month-input';
import { downloadFile } from 'app/shared/util/download';

const MAX_MONTHS = 36;
const ISO = /^\d{4}-\d{2}$/;

const monthsBetween = (from: string, to: string) =>
  (Number(to.slice(0, 4)) - Number(from.slice(0, 4))) * 12 + Number(to.slice(5, 7)) - Number(from.slice(5, 7)) + 1;

/** Báo cáo nhân sự Excel: biến động, nghỉ việc; chi phí lương chỉ có trong file của Quản trị và Kế toán. */
export const HrReport = () => {
  const [from, setFrom] = useState(() => monthsAgo(11));
  const [to, setTo] = useState(() => monthsAgo(0));
  const [groupBy, setGroupBy] = useState('DEPARTMENT');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const download = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!ISO.test(from) || !ISO.test(to)) {
      setError('Nhập tháng dạng MM/yyyy, ví dụ 06/2026.');
      return;
    }
    if (from > to) {
      setError('Từ tháng phải trước hoặc bằng Đến tháng.');
      return;
    }
    if (monthsBetween(from, to) > MAX_MONTHS) {
      setError(`Báo cáo tối đa ${MAX_MONTHS} tháng.`);
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await downloadFile('api/reports/hr.xlsx', `Báo cáo nhân sự ${from} – ${to}.xlsx`, { from, to, groupBy });
    } catch (err) {
      setError(errorText(err, 'Không tải được báo cáo'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1 className="h3 mb-3">Báo cáo nhân sự</h1>
      <p className="text-muted">
        File Excel gồm tổng hợp, biến động nhân sự theo tháng và nghỉ việc. Quản trị và Kế toán có thêm sheet chi phí lương theo bảng lương
        chính thức của từng kỳ.
      </p>
      {error && <Alert variant="danger">{error}</Alert>}
      <Form onSubmit={download} className="row g-3 align-items-end" noValidate>
        <Form.Group className="col-auto" controlId="report-from">
          <Form.Label>Từ tháng (MM/yyyy)</Form.Label>
          <MonthInput value={from} onChange={setFrom} />
        </Form.Group>
        <Form.Group className="col-auto" controlId="report-to">
          <Form.Label>Đến tháng (MM/yyyy)</Form.Label>
          <MonthInput value={to} onChange={setTo} />
        </Form.Group>
        <Form.Group className="col-auto" controlId="report-group">
          <Form.Label>Gom chi phí theo</Form.Label>
          <Form.Select value={groupBy} onChange={e => setGroupBy(e.target.value)}>
            <option value="DEPARTMENT">Phòng ban</option>
            <option value="POSITION">Vị trí</option>
          </Form.Select>
        </Form.Group>
        <div className="col-auto">
          <Button type="submit" disabled={busy}>
            Tải báo cáo
          </Button>
        </div>
      </Form>
    </div>
  );
};

export default HrReport;
