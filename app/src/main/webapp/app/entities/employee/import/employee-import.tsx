import React, { useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form, Table } from 'react-bootstrap';
import { Link } from 'react-router';

import { errorText } from 'app/entities/employee/detail/detail-api';
import Steps from 'app/shared/layout/steps';
import { PageHeader, SectionCard } from 'app/shared/ui';
import { PERSON_TYPE_LABEL, PersonType } from 'app/shared/model/employee.model';
import { MAX_UPLOAD_BYTES, downloadFile } from 'app/shared/util/download';

import { IEmployeeImportCheck, IEmployeeImportIssue, IEmployeeImportResult } from './employee-import.model';
import { useEffectivePrefixes } from 'app/modules/settings/use-numbering';

const STEPS = ['Tải file', 'Kiểm tra', 'Kết quả'];
const BASE = 'api/employees/import';

const personTypeText = (t: string) => PERSON_TYPE_LABEL[t as PersonType] ?? t;

const issueText = (i: IEmployeeImportIssue) =>
  i.excelRow === 0 ? i.message : `Dòng ${i.excelRow}${i.fieldLabel ? ` – ${i.fieldLabel}` : ''}: ${i.message}`;

/** Nhập danh sách nhân viên từ Excel: tải file → kiểm tra (không ghi gì) → kết quả sau khi lưu một giao dịch. */
export const EmployeeImport = () => {
  const { serviceCodePrefix } = useEffectivePrefixes();
  const [step, setStep] = useState(1);
  const [file, setFile] = useState<File | null>(null);
  const [check, setCheck] = useState<IEmployeeImportCheck | null>(null);
  const [result, setResult] = useState<IEmployeeImportResult | null>(null);
  const [acknowledged, setAcknowledged] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const form = (f: File) => {
    const data = new FormData();
    data.append('file', f);
    return data;
  };

  const onFile = async (f: File | null) => {
    setError(null);
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.xlsx')) {
      setError('Chỉ nhận file Excel .xlsx');
      return;
    }
    if (f.size > MAX_UPLOAD_BYTES) {
      setError('File vượt quá dung lượng cho phép (20 MB)');
      return;
    }
    setBusy(true);
    try {
      const res = await axios.post<IEmployeeImportCheck>(`${BASE}/check`, form(f));
      setFile(f);
      setCheck(res.data);
      setAcknowledged(false);
      setStep(2);
    } catch (err) {
      setError(errorText(err, 'Không đọc được file'));
    } finally {
      setBusy(false);
    }
  };

  const downloadIssues = async () => {
    if (!file) return;
    try {
      const res = await axios.post<Blob>(`${BASE}/issues.xlsx`, form(file), { responseType: 'blob' });
      const href = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = href;
      a.download = 'loi-nhap-nhan-vien.xlsx';
      a.click();
      URL.revokeObjectURL(href);
    } catch (err) {
      setError(errorText(err, 'Không tải được báo cáo lỗi'));
    }
  };

  const save = async () => {
    if (!file) return;
    setError(null);
    setBusy(true);
    try {
      const data = form(file);
      data.append('warningsAcknowledged', String(acknowledged));
      const res = await axios.post<IEmployeeImportResult>(BASE, data);
      setResult(res.data);
      setStep(3);
    } catch (err: any) {
      const fresh: IEmployeeImportCheck | undefined = err?.response?.data?.check;
      if (fresh) {
        // Dữ liệu đổi giữa lúc kiểm tra và lúc lưu: hiện lại kết quả mới, người dùng xác nhận lại.
        setCheck(fresh);
        setAcknowledged(false);
        setError(errorText(err, 'Không lưu được, kết quả kiểm tra đã được cập nhật'));
      } else {
        setError(errorText(err, 'Không lưu được, vui lòng thử lại'));
      }
    } finally {
      setBusy(false);
    }
  };

  const chooseAnother = () => {
    setStep(1);
    setFile(null);
    setCheck(null);
    setAcknowledged(false);
    setError(null);
  };

  const issuesOf = (row: number) => check?.issues.filter(i => i.excelRow === row) ?? [];
  const fileIssues = check?.issues.filter(i => i.excelRow === 0) ?? [];
  const rowIssues = check?.issues.filter(i => i.excelRow !== 0) ?? [];
  const needAck = (check?.warningCount ?? 0) > 0;
  const canSave = !!check && check.canSave && (!needAck || acknowledged);

  return (
    <div className="doit-page">
      <nav aria-label="Đường dẫn" className="doit-breadcrumb">
        <Link to="/nhan-vien">Nhân viên</Link> / Nhập từ Excel
      </nav>
      <PageHeader title="Nhập danh sách nhân viên từ Excel" />
      <Steps steps={STEPS} current={step} />
      {error && <Alert variant="danger">{error}</Alert>}

      {step === 1 && (
        <SectionCard title="Bước 1. Tải file">
          <p className="text-muted mb-0">
            Dùng lần đầu hoặc chưa có file theo mẫu?{' '}
            <Button
              variant="link"
              className="p-0 align-baseline"
              onClick={() =>
                downloadFile(`${BASE}/template.xlsx`, 'mau-nhap-nhan-vien.xlsx').catch(() => setError('Không tải được file mẫu'))
              }
            >
              Tải file mẫu
            </Button>{' '}
            (đúng các cột hệ thống đọc được, kèm hướng dẫn).
          </p>
          <Form.Group controlId="employee-import-file" className="col-12 col-md-8">
            <Form.Label>File danh sách nhân viên (.xlsx)</Form.Label>
            <Form.Control
              type="file"
              accept=".xlsx"
              disabled={busy}
              onChange={e => {
                const input = e.target as HTMLInputElement;
                const picked = input.files?.[0] ?? null;
                input.value = ''; // chọn lại cùng file vẫn kích hoạt change
                void onFile(picked);
              }}
            />
          </Form.Group>
        </SectionCard>
      )}

      {step === 2 && check && (
        <SectionCard title="Bước 2. Kiểm tra">
          <p>{`${check.rowCount} dòng · ${check.errorCount} lỗi · ${check.warningCount} cảnh báo`}</p>
          {check.newDepartments.length > 0 && (
            <p>
              Phòng ban sẽ tạo:{' '}
              {check.newDepartments.map((d, k) => (
                <span key={d.name}>
                  {k > 0 && ', '}
                  {d.name}
                </span>
              ))}
            </p>
          )}
          {fileIssues.map((i, k) => (
            <Alert key={k} variant={i.severity === 'ERROR' ? 'danger' : 'warning'}>
              {i.message}
            </Alert>
          ))}
          <Table size="sm" responsive className="align-top">
            <thead>
              <tr>
                <th scope="col">Dòng</th>
                <th scope="col">Họ tên</th>
                <th scope="col">CCCD</th>
                <th scope="col">Loại</th>
                <th scope="col">Mã</th>
                <th scope="col">Phòng ban</th>
                <th scope="col">Quản lý</th>
              </tr>
            </thead>
            <tbody>
              {check.rows.map(r => {
                const issues = issuesOf(r.excelRow);
                const cls = issues.some(i => i.severity === 'ERROR') ? 'table-danger' : issues.length > 0 ? 'table-warning' : undefined;
                return (
                  <tr key={r.excelRow} className={cls}>
                    <td>{r.excelRow}</td>
                    <td>{r.fullName}</td>
                    <td>{r.idNumber}</td>
                    <td>{personTypeText(r.personType)}</td>
                    <td>
                      {r.code === null ? (
                        `${serviceCodePrefix} (tự sinh)`
                      ) : (
                        <>
                          {r.code} {r.codeProvisional && <span className="text-muted small">dự kiến</span>}
                        </>
                      )}
                    </td>
                    <td>{r.departmentName}</td>
                    <td>{r.managerRef}</td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
          {rowIssues.length > 0 && (
            <ul className="list-unstyled">
              {rowIssues.map((i, k) => (
                <li key={k} className={i.severity === 'ERROR' ? 'text-danger' : 'text-warning-emphasis'}>
                  {issueText(i)}
                </li>
              ))}
            </ul>
          )}
          {needAck && (
            <Form.Check
              id="employee-import-ack"
              className="mb-3"
              label="Đã xem cảnh báo"
              checked={acknowledged}
              onChange={e => setAcknowledged(e.target.checked)}
            />
          )}
          <div className="d-flex gap-2 flex-wrap">
            <Button variant="outline-secondary" onClick={chooseAnother}>
              Chọn file khác
            </Button>
            <Button variant="outline-secondary" onClick={() => void downloadIssues()}>
              Tải báo cáo lỗi
            </Button>
            <Button disabled={busy || !canSave} onClick={() => void save()}>
              {`Lưu ${check.rowCount} hồ sơ`}
            </Button>
          </div>
        </SectionCard>
      )}

      {step === 3 && result && (
        <SectionCard title="Bước 3. Kết quả">
          <p>{`Đã tạo ${result.createdCount} hồ sơ`}</p>
          {result.createdDepartments.length > 0 && <p>Phòng ban mới: {result.createdDepartments.join(', ')}</p>}
          <Table size="sm" responsive>
            <thead>
              <tr>
                <th scope="col">Dòng</th>
                <th scope="col">Họ tên</th>
                <th scope="col">Mã</th>
              </tr>
            </thead>
            <tbody>
              {result.created.map(c => (
                <tr key={c.excelRow}>
                  <td>{c.excelRow}</td>
                  <td>{c.fullName}</td>
                  <td>{c.code}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </SectionCard>
      )}

      <div>
        <Link to="/nhan-vien">Về danh sách nhân viên</Link>
      </div>
    </div>
  );
};

export default EmployeeImport;
