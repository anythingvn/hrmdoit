import './import-wizard.scss';

import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form, Table } from 'react-bootstrap';
import { Link, useNavigate, useParams } from 'react-router';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { MAX_UPLOAD_BYTES, downloadFile } from 'app/shared/util/download';

import Steps from 'app/shared/layout/steps';
import { ChipTone, PageHeader, SectionCard, StatusChip } from 'app/shared/ui';
import MonthInput, { monthsAgo } from 'app/shared/month-input';

import { IImportCheck, IIssue, IRow, periodText } from './payroll-api';
import RowMapping from './row-mapping';

const STEPS = ['Tải file', 'Nhận diện cột', 'Kiểm tra', 'Xác nhận'];

type RowResult = 'error' | 'warning' | 'ok';

const RESULT: Record<RowResult, { label: string; tone: ChipTone }> = {
  error: { label: 'Lỗi', tone: 'warn' },
  warning: { label: 'Cảnh báo', tone: 'wait' },
  ok: { label: 'Hợp lệ', tone: 'ok' },
};

/** Kết quả một dòng: có lỗi; chỉ cảnh báo; hợp lệ. */
const rowResult = (issues: IIssue[]): RowResult =>
  issues.some(i => i.severity === 'ERROR') ? 'error' : issues.length > 0 ? 'warning' : 'ok';

/** Thẻ file viền nét đứt (canvas): ô XLSX, tên file, số dòng, số cột nhận diện, nút chọn file khác. */
const FileCard = ({ check, onChange }: { check: IImportCheck; onChange?: () => void }) => {
  const found = check.columns.filter(c => c.column).length;
  return (
    <section className="payroll-file-card" aria-label="File đang import">
      <span className="payroll-file-kind" aria-hidden="true">
        XLSX
      </span>
      <div className="payroll-file-body">
        <span className="payroll-file-name">{check.fileName}</span>
        <span className="payroll-file-meta">
          {check.rows.length} dòng nhân viên · nhận diện {found}/{check.columns.length} cột
        </span>
      </div>
      {onChange && (
        <Button variant="outline-secondary" onClick={onChange}>
          Chọn file khác
        </Button>
      )}
    </section>
  );
};

/** Cột Excel → trường hệ thống (máy chủ tự nhận diện theo tiêu đề của mẫu; không ghép tay). */
const ColumnsCard = ({ check, children }: { check: IImportCheck; children?: React.ReactNode }) => (
  <SectionCard
    title="Cột Excel → trường hệ thống"
    titleId="payroll-columns"
    subtitle="Cột được nhận diện tự động theo tiêu đề của mẫu bảng lương DOIT."
  >
    {check.missingRequired.length > 0 && (
      <Alert variant="danger" className="mb-0">
        Thiếu cột bắt buộc: {check.missingRequired.join(', ')}
      </Alert>
    )}
    <ul className="payroll-columns" aria-label="Cột nhận diện được">
      {check.columns.map(c => (
        <li key={c.field}>
          <span className={`payroll-column-cell${c.column ? '' : ' payroll-column-missing'}`}>{c.column ?? 'Không thấy'}</span>
          <span className="payroll-column-field">
            {c.label} {c.required && <StatusChip tone="muted">bắt buộc</StatusChip>}
          </span>
        </li>
      ))}
    </ul>
    {children}
  </SectionCard>
);

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
  const [onlyIssues, setOnlyIssues] = useState(true);

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
  const results = (check?.rows ?? []).map(r => ({ row: r, issues: issuesOf(r.excelRow), result: rowResult(issuesOf(r.excelRow)) }));
  const count = (k: RowResult) => results.filter(x => x.result === k).length;
  /** Cần xử lý: có lỗi / cảnh báo hoặc chưa gắn được hồ sơ. */
  const needsWork = (x: { row: IRow; result: RowResult }) => x.result !== 'ok' || !x.row.employeeId;
  const filtering = onlyIssues && results.some(needsWork);
  const shownRows = filtering ? results.filter(needsWork) : results;

  const downloadTemplate = () =>
    downloadFile('api/payroll/imports/template.xlsx', 'mau-bang-luong.xlsx').catch(() => setError('Không tải được file mẫu'));

  return (
    <div className="doit-page payroll-import">
      <PageHeader
        as="h2"
        title={check ? `Import bảng lương tháng ${periodText(check.period)}` : 'Import bảng lương'}
        subtitle="Dùng file Excel theo mẫu bảng lương DOIT · đối chiếu hồ sơ theo mã nhân viên trong file"
        actions={
          <Button variant="link" onClick={downloadTemplate}>
            Tải file mẫu (.xlsx)
          </Button>
        }
      />
      <Steps steps={STEPS} current={step} />
      {error && <Alert variant="danger">{error}</Alert>}

      {step === 1 && (
        <Form onSubmit={upload} noValidate className="doit-card">
          <h3 className="doit-card-title">Tải file</h3>
          <p className="text-muted">
            Dùng lần đầu hoặc chưa có file theo mẫu? Tải file mẫu ở góc trên (đúng các cột hệ thống đọc được, kèm hướng dẫn).
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
          <Button type="submit" disabled={busy}>
            Tải lên và đọc file
          </Button>
        </Form>
      )}

      {step >= 2 && check && (
        <FileCard
          check={check}
          onChange={
            check.status === 'DANG_LAM'
              ? () => {
                  setPeriod(check.period);
                  setFile(null);
                  setStep(1);
                }
              : undefined
          }
        />
      )}

      {step === 2 && check && (
        <ColumnsCard check={check}>
          <Button className="align-self-start" disabled={check.missingRequired.length > 0} onClick={() => setStep(3)}>
            Tiếp tục
          </Button>
        </ColumnsCard>
      )}

      {step === 3 && check && (
        <div className="doit-grid-2 payroll-check-grid">
          <ColumnsCard check={check} />
          <SectionCard
            title="Kiểm tra dữ liệu"
            titleId="payroll-check"
            actions={
              <div className="d-flex gap-2" role="group" aria-label="Lọc dòng">
                <button type="button" className="btn doit-pill" aria-pressed={onlyIssues} onClick={() => setOnlyIssues(true)}>
                  Cần xử lý
                </button>
                <button type="button" className="btn doit-pill" aria-pressed={!onlyIssues} onClick={() => setOnlyIssues(false)}>
                  Tất cả
                </button>
              </div>
            }
          >
            <dl className="payroll-counts">
              <div className="payroll-count-ok">
                <dt>Hợp lệ</dt>
                <dd>{count('ok')}</dd>
              </div>
              <div className="payroll-count-wait">
                <dt>Cảnh báo</dt>
                <dd>{count('warning')}</dd>
              </div>
              <div className="payroll-count-error">
                <dt>Lỗi</dt>
                <dd>{count('error')}</dd>
              </div>
            </dl>
            <p className="doit-card-sub">
              Số dòng nhân viên theo kết quả kiểm tra; tổng {check.errorCount} lỗi, {check.warningCount} cảnh báo (tính cả cảnh báo của cả
              file).
            </p>
            {fileIssues.map((i, k) => (
              <Alert key={k} variant={i.severity === 'ERROR' ? 'danger' : 'warning'} className="mb-0">
                {i.message}
              </Alert>
            ))}
            <div className="table-responsive">
              <Table size="sm" className="align-top payroll-check-table">
                <thead>
                  <tr>
                    <th scope="col">Dòng</th>
                    <th scope="col">Mã NV</th>
                    <th scope="col">Họ tên</th>
                    <th scope="col">Kết quả</th>
                  </tr>
                </thead>
                <tbody>
                  {shownRows.length === 0 && (
                    <tr>
                      <td colSpan={4}>Không có dòng nào cần xử lý.</td>
                    </tr>
                  )}
                  {shownRows.map(({ row: r, issues, result }) => (
                    <tr key={r.excelRow}>
                      <td className="text-muted">{r.excelRow}</td>
                      <td className="fw-semibold">{r.fileCode ?? <em>(thiếu mã)</em>}</td>
                      <td>
                        {r.fileName}
                        {r.position && <span className="doit-cell-sub">{r.position}</span>}
                        {r.employeeCode && !r.mappedManually && (
                          <span className="doit-cell-sub">
                            Hồ sơ: {r.employeeCode} – {r.employeeName}
                          </span>
                        )}
                        {(result === 'error' || r.mappedManually || !r.employeeId) && check.status === 'DANG_LAM' && (
                          <RowMapping importId={check.id} row={r} onChanged={setCheck} />
                        )}
                      </td>
                      <td>
                        <StatusChip tone={RESULT[result].tone}>{RESULT[result].label}</StatusChip>
                        {issues.map((i, k) => (
                          <span key={k} className={`doit-cell-sub ${i.severity === 'ERROR' ? 'text-danger' : 'text-warning-emphasis'}`}>
                            {i.fieldLabel ? `${i.fieldLabel}: ` : ''}
                            {i.message}
                          </span>
                        ))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
            {check.warningCount > 0 && (
              <Form.Check
                id="ack-warnings"
                label="Đã xem và xác nhận các cảnh báo"
                checked={check.warningsAcknowledged}
                disabled={check.warningsAcknowledged}
                onChange={acknowledge}
              />
            )}
            <div className="d-flex flex-wrap gap-2">
              <Button
                variant="outline-secondary"
                onClick={() => downloadFile(`api/payroll/imports/${check.id}/issues.xlsx`, `loi-bang-luong-${check.period}.xlsx`)}
              >
                Tải báo cáo lỗi
              </Button>
              <Button variant="outline-secondary" onClick={() => setStep(2)}>
                Quay lại
              </Button>
              <Button className="ms-sm-auto" disabled={!check.canConfirm} onClick={() => setStep(4)}>
                Tiếp tục
              </Button>
            </div>
          </SectionCard>
        </div>
      )}

      {step === 4 && check && (
        <SectionCard title="Xác nhận lưu" titleId="payroll-confirm">
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
        </SectionCard>
      )}

      <div className="d-flex gap-3">
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
