import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';

export interface IDocumentSequence {
  type: string;
  code: string;
  year: number;
  nextValue: number;
  lastIssued: number;
  nextNumber: string;
}

export const DOCUMENT_TYPE_LABEL: Record<string, string> = {
  HDLD: 'Hợp đồng lao động',
  HDTV: 'Hợp đồng thử việc',
  HDDT: 'Hợp đồng đào tạo',
  HDDV: 'Hợp đồng dịch vụ',
  QD_CU_NHAN_SU: 'Quyết định cử nhân sự',
};

interface Props {
  /** Chỉ Quản trị đặt được số bắt đầu; Nhân sự chỉ xem. */
  canEdit: boolean;
  initialYear?: number;
}

/** Dãy số văn bản theo loại và năm: số đã cấp, số sẽ cấp tiếp; Quản trị đặt số bắt đầu để nối tiếp số đã ký tay. */
export const DocumentNumbers = ({ canEdit, initialYear }: Props) => {
  const [year, setYear] = useState(() => initialYear ?? new Date().getFullYear());
  const [rows, setRows] = useState<IDocumentSequence[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  useEffect(() => {
    if (!year || year < 2000) return;
    setError(null);
    axios
      .get<IDocumentSequence[]>('api/admin/document-sequences', { params: { year } })
      .then(res => {
        setRows(res.data);
        setDrafts({});
      })
      .catch(err => setError(errorText(err, 'Không tải được dãy số văn bản')));
  }, [year]);

  const save = async (row: IDocumentSequence) => {
    const value = Number(drafts[row.type]);
    if (!Number.isInteger(value) || value < 1) {
      setError('Số bắt đầu phải là số nguyên từ 1 trở lên');
      return;
    }
    setError(null);
    setSaved(null);
    try {
      const res = await axios.put<IDocumentSequence>(`api/admin/document-sequences/${row.type}/${row.year}`, { nextValue: value });
      setRows(rs => rs.map(r => (r.type === row.type ? res.data : r)));
      setDrafts(d => ({ ...d, [row.type]: '' }));
      setSaved(`Đã đặt: văn bản ${row.code} tiếp theo sẽ là ${res.data.nextNumber}.`);
    } catch (err) {
      setError(errorText(err));
    }
  };

  return (
    <div className="doit-card">
      <p className="text-muted">
        Mỗi loại văn bản có một dãy số riêng, chạy lại từ 1 mỗi năm, dạng <code>DOIT/năm/mã loại/số</code>. Số đã cấp không bị dùng lại, kể
        cả khi văn bản bị hủy.
      </p>
      <Form.Group className="mb-3" style={{ maxWidth: 160 }} controlId="document-numbers-year">
        <Form.Label>Năm</Form.Label>
        <Form.Control type="number" min={2000} value={year} onChange={e => setYear(Number(e.target.value))} />
      </Form.Group>
      {error && <Alert variant="danger">{error}</Alert>}
      {saved && <Alert variant="success">{saved}</Alert>}
      <Table responsive className="align-middle">
        <thead>
          <tr>
            <th>Loại văn bản</th>
            <th>Mã</th>
            <th className="text-end">Đã cấp đến</th>
            <th>Số tiếp theo</th>
            {canEdit && <th>Đặt số bắt đầu</th>}
          </tr>
        </thead>
        <tbody>
          {rows.map(r => (
            <tr key={r.type}>
              <td>{DOCUMENT_TYPE_LABEL[r.type] ?? r.type}</td>
              <td>{r.code}</td>
              <td className="text-end">{r.lastIssued > 0 ? r.lastIssued : 'Chưa cấp'}</td>
              <td>
                <code>{r.nextNumber}</code>
              </td>
              {canEdit && (
                <td>
                  <div className="d-flex gap-2">
                    <Form.Control
                      type="number"
                      min={1}
                      style={{ maxWidth: 120 }}
                      aria-label={`Số bắt đầu ${r.code}`}
                      value={drafts[r.type] ?? ''}
                      placeholder={String(r.nextValue)}
                      onChange={e => setDrafts(d => ({ ...d, [r.type]: e.target.value }))}
                    />
                    <Button variant="outline-primary" aria-label={`Lưu số ${r.code}`} disabled={!drafts[r.type]} onClick={() => save(r)}>
                      Lưu
                    </Button>
                  </div>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </Table>
    </div>
  );
};

export default DocumentNumbers;
