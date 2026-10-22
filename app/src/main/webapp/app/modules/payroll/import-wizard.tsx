import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Badge, Button, Form, Table } from 'react-bootstrap';
import { Link, useNavigate, useParams } from 'react-router';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { MAX_UPLOAD_BYTES, downloadFile } from 'app/shared/util/download';

import Steps from 'app/shared/layout/steps';
import MonthInput, { monthsAgo } from 'app/shared/month-input';

import { IImportCheck, IIssue, periodText } from './payroll-api';
import RowMapping from './row-mapping';

const STEPS = ['Tải file', 'Nhận diện cột', 'Kiểm tra', 'Xác nhận'];

/** Import bảng lương 4 bước: tải file → nhận diện cột → kiểm tra (gắn hồ sơ, xác nhận cảnh báo) → xác nhận lưu phiên bản. */
export const ImportWizard = () => {
  const { id } = useParams<'id'>();
  const navigate = useNavigate();
  const isNew = !id || id === 'moi';
  const [step, setStep] = useState(isNew ? 1 : 2);
  const [check, setCheck] = useState<IImportCheck | null>(null);
  // Bảng lương thường làm cho tháng trước.
  const [period, setPeriod] = useState(() => monthsAgo(1));
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (isNew) return;
    axios
      .get<IImportCheck>(`api/payroll/imports/${id}`)
      .then(r => setCheck(r.data))
      .catch(err => setError(errorText(err, 'Không tải được bản nháp')));
  }, [id]);

  const upload = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setError(null);
    if (!/^\d{4}-\d{2}$/.test(period)) {
      setError('Nhập kỳ lương dạng MM/yyyy, ví dụ 06/2026');
      return;
    }
    if (!file) {
      setError('Chọn file bảng lương (.xlsx)');
      return;
    }
    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      setError('Chỉ nhận file Excel .xlsx');
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setError('File vượt quá dung lượng cho phép (20 MB)');
      return;
    }
    const data = new FormData();
    data.append('period', period);
    data.append('file', file);
    setBusy(true);
    try {
      const res = await axios.post<IImportCheck>('api/payroll/imports', data);
      setCheck(res.data);
      setStep(2);
      navigate(`/bang-luong/import/${res.data.id}`, { replace: true });
    } catch (err) {
      setError(errorText(err, 'Không đọc được file'));
    } finally {
      setBusy(false);
    }
  };

  const acknowledge = async () => {
    if (!check) return;
    try {
      const res = await axios.post<IImportCheck>(`api/payroll/imports/${check.id}/acknowledge-warnings`);
      setCheck(res.data);
    } catch (err) {
      setError(errorText(err));
    }
  };

  const confirm = async () => {
    if (!check) return;
    setError(null);
    setBusy(true);
    try {
      const res = await axios.post<{ period: string; version: number }>(`api/payroll/imports/${check.id}/confirm`);
      navigate(`/bang-luong/${res.data.period}`);
    } catch (err) {
      setError(errorText(err, 'Không lưu được bảng lương'));
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    if (!check) return;
    try {
      await axios.delete(`api/payroll/imports/${check.id}`);
      navigate('/bang-luong');
    } catch (err) {
      setError(errorText(err));
    }
  };

  const issuesOf = (row: number): IIssue[] => check?.issues.filter(i => i.excelRow === row) ?? [];
  const fileIssues = check?.issues.filter(i => i.excelRow === 0) ?? [];

  return (
    <div>
      <h2 className="h4">Import bảng lương{check ? ` kỳ ${periodText(check.period)}` : ''}</h2>
      <Steps steps={STEPS} current={step} />
      {error && <Alert variant="danger">{error}</Alert>}

      {step === 1 && (
        <Form onSubmit={upload} noValidate>
          <h3 className="h5">Bước 1. Tải file</h3>
          <p className="text-muted">
            Dùng lần đầu hoặc chưa có file theo mẫu?{' '}
            <Button
              variant="link"
              className="p-0 align-baseline"
              onClick={() =>
                downloadFile('api/payroll/imports/template.xlsx', 'mau-bang-luong.xlsx').catch(() => setError('Không tải được file mẫu'))
              }
            >
              Tải file mẫu bảng lương
            </Button>{' '}
            (đúng các cột hệ thống đọc được, kèm hướng dẫn).
          </p>
          <div className="row g-3">
            <Form.Group className="col-12 col-md-4" controlId="payroll-period">
              <Form.Label>Kỳ lương (MM/yyyy)</Form.Label>
              <MonthInput value={period} onChange={setPeriod} />
            </Form.Group>
            <Form.Group className="col-12 col-md-8" controlId="payroll-file">
              <Form.Label>File bảng lương (.xlsx)</Form.Label>
              <Form.Control type="file" accept=".xlsx" onChange={e => setFile((e.target as HTMLInputElement).files?.[0] ?? null)} />
            </Form.Group>
          </div>
          <Button type="submit" className="mt-3" disabled={busy}>
            Tải lên và đọc file
          </Button>
        </Form>
      )}

      {step === 2 && check && (
        <section>
          <h3 className="h5">Bước 2. Nhận diện cột</h3>
          <p className="text-muted">
            File {check.fileName}: đọc được {check.rows.length} dòng nhân viên. Cột được nhận diện theo tiêu đề của mẫu bảng lương DOIT.
          </p>
          {check.missingRequired.length > 0 && <Alert variant="danger">Thiếu cột bắt buộc: {check.missingRequired.join(', ')}</Alert>}
          <Table size="sm" responsive>
            <thead>
              <tr>
                <th scope="col">Trường</th>
                <th scope="col">Cột trong file</th>
              </tr>
            </thead>
            <tbody>
              {check.columns.map(c => (
                <tr key={c.field}>
                  <td>
                    {c.label} {c.required && <Badge bg="secondary">bắt buộc</Badge>}
                  </td>
                  <td>{c.column ?? <span className="text-danger">Không thấy</span>}</td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Button disabled={check.missingRequired.length > 0} onClick={() => setStep(3)}>
            Tiếp tục
          </Button>
        </section>
      )}

      {step === 3 && check && (
        <section>
          <h3 className="h5">Bước 3. Kiểm tra</h3>
          <p>
            {check.errorCount} lỗi, {check.warningCount} cảnh báo.{' '}
            <Button
              variant="link"
              className="p-0 align-baseline"
              onClick={() => downloadFile(`api/payroll/imports/${check.id}/issues.xlsx`, `loi-bang-luong-${check.period}.xlsx`)}
            >
              Tải báo cáo lỗi
            </Button>
          </p>
          {fileIssues.map((i, k) => (
            <Alert key={k} variant={i.severity === 'ERROR' ? 'danger' : 'warning'}>
              {i.message}
            </Alert>
          ))}
          <Table size="sm" responsive className="align-top">
            <thead>
              <tr>
                <th scope="col">Dòng</th>
                <th scope="col">Trong file</th>
                <th scope="col">Hồ sơ</th>
                <th scope="col">Lỗi / cảnh báo</th>
              </tr>
            </thead>
            <tbody>
              {check.rows.map(r => {
                const issues = issuesOf(r.excelRow);
                const hasError = issues.some(i => i.severity === 'ERROR');
                return (
                  <tr key={r.excelRow} className={hasError ? 'table-danger' : undefined}>
                    <td>{r.excelRow}</td>
                    <td>
                      {r.fileCode ?? <em>(thiếu mã)</em>} – {r.fileName}
                      {r.position && <div className="text-muted small">{r.position}</div>}
                    </td>
                    <td>
                      {r.employeeCode && !r.mappedManually && (
                        <div>
                          {r.employeeCode} – {r.employeeName}
                        </div>
                      )}
                      {(hasError || r.mappedManually || !r.employeeId) && check.status === 'DANG_LAM' && (
                        <RowMapping importId={check.id} row={r} onChanged={setCheck} />
                      )}
                    </td>
                    <td>
                      {issues.map((i, k) => (
                        <div key={k} className={i.severity === 'ERROR' ? 'text-danger' : 'text-warning-emphasis'}>
                          {i.fieldLabel ? `${i.fieldLabel}: ` : ''}
                          {i.message}
                        </div>
                      ))}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
          {check.warningCount > 0 && (
            <Form.Check
              id="ack-warnings"
              className="mb-3"
              label="Đã xem và xác nhận các cảnh báo"
              checked={check.warningsAcknowledged}
              disabled={check.warningsAcknowledged}
              onChange={acknowledge}
            />
          )}
          <div className="d-flex gap-2">
            <Button variant="outline-secondary" onClick={() => setStep(2)}>
              Quay lại
            </Button>
            <Button disabled={!check.canConfirm} onClick={() => setStep(4)}>
              Tiếp tục
            </Button>
          </div>
        </section>
      )}

      {step === 4 && check && (
        <section>
          <h3 className="h5">Bước 4. Xác nhận</h3>
          <p>
            Lưu {check.rows.length} dòng thành phiên bản mới của kỳ {periodText(check.period)}
            {check.warningCount > 0 ? ` (${check.warningCount} cảnh báo đã xác nhận)` : ''}. File gốc được lưu kèm.
          </p>
          <div className="d-flex gap-2">
            <Button variant="outline-secondary" onClick={() => setStep(3)}>
              Quay lại
            </Button>
            <Button onClick={confirm} disabled={busy || !check.canConfirm}>
              Xác nhận lưu bảng lương
            </Button>
          </div>
        </section>
      )}

      <div className="mt-4 d-flex gap-3">
        <Link to="/bang-luong">Về danh sách kỳ lương</Link>
        {check?.status === 'DANG_LAM' && (
          <Button variant="link" className="p-0 text-danger" onClick={cancel}>
            Hủy bản nháp
          </Button>
        )}
      </div>
    </div>
  );
};

export default ImportWizard;
