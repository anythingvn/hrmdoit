import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button } from 'react-bootstrap';

import { downloadFile } from 'app/shared/util/download';

import { isoToVn } from '../vn-date';
import DecisionDialog from './decision-dialog';
import { FILE_KIND_LABEL, IGeneratedDocument, IGenerationResult, errorText } from './detail-api';

export interface IDecision {
  id: number;
  decisionNo: string;
  signDate: string;
  type: 'CONG_TAC' | 'KHACH_HANG';
  unit: string;
  fromDate: string;
  toDate?: string | null;
}

interface IDecisionWithDocuments {
  decision: IDecision;
  documents: IGeneratedDocument[];
}

interface Props {
  employeeId: number;
  /** Tạo quyết định, tạo lại PDF: Quản trị, HR trên hồ sơ chưa lưu trữ. */
  canWrite: boolean;
  /** Xem danh sách và tải văn bản: chỉ Quản trị, HR (quyết định chứa dữ liệu nhạy cảm), kể cả hồ sơ đã lưu trữ. Mặc định = canWrite. */
  canView?: boolean;
  /** Gọi sau khi tạo quyết định (giai đoạn vị trí vừa được ghi). */
  onCreated: () => void;
}

/** Quyết định cử nhân sự của nhân viên: tạo mới, tải .docx/.pdf, tạo lại PDF. */
export const DecisionsSection = ({ employeeId, canWrite, canView = canWrite, onCreated }: Props) => {
  const [items, setItems] = useState<IDecisionWithDocuments[]>([]);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = () =>
    axios
      .get<IDecisionWithDocuments[]>(`api/employees/${employeeId}/assignment-decisions`)
      .then(r => setItems(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được danh sách quyết định')));

  useEffect(() => {
    if (canView) load();
  }, [employeeId, canView]);

  if (!canView) return null;

  const retryPdf = async (d: IDecision) => {
    setError(null);
    setNotice(null);
    try {
      const res = await axios.post<IGenerationResult>(`api/assignment-decisions/${d.id}/documents/pdf`);
      if (res.data?.pdfError) setError(res.data.pdfError);
      else setNotice(`Đã tạo PDF cho quyết định ${d.decisionNo}.`);
      await load();
    } catch (err) {
      setError(errorText(err, 'Không tạo được PDF'));
    }
  };

  const download = async (doc: IGeneratedDocument) => {
    setError(null);
    try {
      await downloadFile(`api/generated-documents/${doc.id}/download`, doc.fileName);
    } catch (err) {
      setError(errorText(err, 'Không tải được file'));
    }
  };

  return (
    <section className="doit-section mt-4" aria-labelledby="h-decisions">
      <div className="doit-section-head">
        <h2 id="h-decisions">Quyết định cử nhân sự</h2>
        {canWrite && (
          <Button type="button" variant="outline-primary" onClick={() => setOpen(true)}>
            Cử đi công tác / làm tại khách hàng
          </Button>
        )}
      </div>
      {error && (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      )}
      {notice && <Alert variant="success">{notice}</Alert>}
      <div className="table-responsive">
        <table className="table doit-table">
          <thead>
            <tr>
              <th scope="col">Số quyết định</th>
              <th scope="col">Nơi đến · thời gian</th>
              <th scope="col">Văn bản</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 && (
              <tr>
                <td colSpan={3}>Chưa có quyết định</td>
              </tr>
            )}
            {items.map(({ decision: d, documents }) => {
              const docx = documents.find(x => x.kind === 'DOCX');
              const hasPdf = docx != null && documents.some(x => x.kind === 'PDF' && x.sourceDocumentId === docx.id);
              return (
                <tr key={d.id}>
                  <td>
                    <span className="fw-semibold">{d.decisionNo}</span>
                    <div className="text-muted">Ký ngày {isoToVn(d.signDate)}</div>
                  </td>
                  <td>{`${d.unit} · ${isoToVn(d.fromDate)} – ${d.toDate ? isoToVn(d.toDate) : 'khi có quyết định khác'}`}</td>
                  <td className="text-nowrap">
                    {documents.map(doc => (
                      <Button
                        key={doc.id}
                        variant="link"
                        className="doit-row-action"
                        onClick={() => download(doc)}
                        aria-label={`Tải ${FILE_KIND_LABEL[doc.kind]} ${d.decisionNo}`}
                      >
                        {FILE_KIND_LABEL[doc.kind]}
                      </Button>
                    ))}
                    {canWrite && docx && !hasPdf && (
                      <Button
                        variant="link"
                        className="doit-row-action"
                        onClick={() => retryPdf(d)}
                        aria-label={`Tạo lại PDF ${d.decisionNo}`}
                      >
                        Tạo lại PDF
                      </Button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {open && (
        <DecisionDialog
          employeeId={employeeId}
          onClose={() => setOpen(false)}
          onSaved={message => {
            setNotice(message);
            load();
            onCreated();
          }}
        />
      )}
    </section>
  );
};

export default DecisionsSection;
