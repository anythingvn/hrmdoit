import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button, Form } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';

import { ICandidate, IImportCheck, IRow } from './payroll-api';
import { useEffectivePrefixes } from 'app/modules/settings/use-numbering';

interface Props {
  importId: number;
  row: Pick<IRow, 'excelRow' | 'fileCode' | 'fileName' | 'employeeId' | 'mappedManually'> & Partial<IRow>;
  onChanged: (check: IImportCheck) => void;
}

/** Gắn một dòng lương với hồ sơ: chọn trong danh sách gợi ý theo họ tên, hoặc tạo hồ sơ rút gọn rồi gắn luôn. */
export const RowMapping = ({ importId, row, onChanged }: Props) => {
  const { serviceCodePrefix } = useEffectivePrefixes();
  const base = `api/payroll/imports/${importId}/rows/${row.excelRow}`;
  const [mode, setMode] = useState<'none' | 'pick' | 'create'>('none');
  const [q, setQ] = useState('');
  const [candidates, setCandidates] = useState<ICandidate[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({ fullName: row.fileName ?? '', personType: 'NHAN_VIEN', code: '' });

  useEffect(() => {
    if (mode !== 'pick') return;
    axios
      .get<ICandidate[]>(`${base}/candidates`, { params: { q: q.trim() || undefined } })
      .then(r => setCandidates(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tìm được hồ sơ')));
  }, [mode, q]);

  const done = (data: IImportCheck) => {
    setMode('none');
    setError(null);
    onChanged(data);
  };

  const pick = async (c: ICandidate) => {
    setError(null);
    try {
      const res = await axios.put<IImportCheck>(`${base}/employee`, { employeeId: c.id });
      done(res.data);
    } catch (err) {
      setError(errorText(err, 'Không gắn được hồ sơ'));
    }
  };

  const unmap = async () => {
    setError(null);
    try {
      const res = await axios.delete<IImportCheck>(`${base}/employee`);
      done(res.data);
    } catch (err) {
      setError(errorText(err));
    }
  };

  const create = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setError(null);
    try {
      const res = await axios.post<IImportCheck>(`${base}/new-employee`, {
        fullName: form.fullName.trim(),
        personType: form.personType,
        // Để trống mã: máy chủ cấp mã kế tiếp của dãy theo tiền tố cấu hình.
        code: form.personType === 'DICH_VU' ? null : form.code.trim() || null,
      });
      done(res.data);
    } catch (err) {
      setError(errorText(err, 'Không tạo được hồ sơ'));
    }
  };

  return (
    <div className="payroll-row-mapping">
      {row.mappedManually && row.employeeCode && (
        <div className="small">
          Đã gắn tay: {row.employeeCode} – {row.employeeName}{' '}
          <Button variant="link" size="sm" onClick={unmap} aria-label={`Bỏ gắn dòng ${row.excelRow}`}>
            Bỏ gắn
          </Button>
        </div>
      )}
      <div className="d-flex flex-wrap gap-2">
        <Button
          variant="outline-primary"
          size="sm"
          onClick={() => setMode(mode === 'pick' ? 'none' : 'pick')}
          aria-label={`Chọn hồ sơ cho dòng ${row.excelRow}`}
        >
          Chọn hồ sơ
        </Button>
        <Button
          variant="outline-secondary"
          size="sm"
          onClick={() => setMode(mode === 'create' ? 'none' : 'create')}
          aria-label={`Tạo hồ sơ rút gọn cho dòng ${row.excelRow}`}
        >
          Tạo hồ sơ rút gọn
        </Button>
      </div>
      {error && (
        <div role="alert" className="text-danger small mt-1">
          {error}
        </div>
      )}
      {mode === 'pick' && (
        <div className="mt-2">
          <Form.Group controlId={`pick-${row.excelRow}`}>
            <Form.Label className="small">Tìm hồ sơ (dòng {row.excelRow})</Form.Label>
            <Form.Control size="sm" value={q} placeholder={row.fileName} onChange={e => setQ(e.target.value)} />
          </Form.Group>
          <ul className="list-unstyled mt-1 mb-0">
            {candidates.length === 0 && <li className="small text-muted">Không có hồ sơ phù hợp — tạo hồ sơ rút gọn</li>}
            {candidates.map(c => (
              <li key={c.id}>
                <Button
                  variant="link"
                  size="sm"
                  className="text-start"
                  onClick={() => pick(c)}
                  aria-label={`Chọn ${c.code} – ${c.fullName}`}
                >
                  {c.code} – {c.fullName}
                  {c.position ? ` · ${c.position}` : ''}
                  {c.exactName ? ' (trùng tên)' : ''}
                </Button>
              </li>
            ))}
          </ul>
        </div>
      )}
      {mode === 'create' && (
        <Form className="mt-2" onSubmit={create}>
          <div className="row g-2">
            <Form.Group className="col-12 col-md-5" controlId={`new-name-${row.excelRow}`}>
              <Form.Label className="small">Họ tên</Form.Label>
              <Form.Control size="sm" value={form.fullName} onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))} />
            </Form.Group>
            <Form.Group className="col-6 col-md-4" controlId={`new-type-${row.excelRow}`}>
              <Form.Label className="small">Loại</Form.Label>
              <Form.Select size="sm" value={form.personType} onChange={e => setForm(f => ({ ...f, personType: e.target.value }))}>
                <option value="NHAN_VIEN">Nhân viên</option>
                <option value="THUC_TAP">Thực tập (học việc)</option>
                <option value="DICH_VU">Hợp đồng dịch vụ (mã tự sinh {serviceCodePrefix})</option>
              </Form.Select>
            </Form.Group>
            {form.personType !== 'DICH_VU' && (
              <Form.Group className="col-6 col-md-3" controlId={`new-code-${row.excelRow}`}>
                <Form.Label className="small">Mã nhân viên</Form.Label>
                <Form.Control
                  size="sm"
                  value={form.code}
                  placeholder="Để trống: tự cấp"
                  onChange={e => setForm(f => ({ ...f, code: e.target.value }))}
                />
              </Form.Group>
            )}
          </div>
          <Button type="submit" size="sm" className="mt-2">
            Tạo và gắn
          </Button>
        </Form>
      )}
    </div>
  );
};

export default RowMapping;
