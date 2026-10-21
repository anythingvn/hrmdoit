import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { MAX_UPLOAD_BYTES, downloadFile } from 'app/shared/util/download';
import { CONTRACT_TYPE_LABEL } from 'app/shared/model/employee.model';

import { FILE_KIND_LABEL, IContract, IGeneratedDocument, IGenerationResult, errorText } from './detail-api';
import { instantToVn } from '../vn-date';
import { ServerError } from './form-fields';

interface Props {
  contract: IContract;
  onClose: () => void;
  /** Gọi khi số HĐ được cấp, trạng thái hoặc bản đã ký thay đổi (tải lại danh sách HĐ). */
  onChanged: () => void;
}

/** Văn bản của một hợp đồng: tạo từ mẫu (Word + PDF), tạo lại PDF, tải về, tải bản đã ký lên, đánh dấu đã ký. */
export const ContractDocumentsDialog = ({ contract, onClose, onChanged }: Props) => {
  const base = `api/employment-contracts/${contract.id}`;
  const [docs, setDocs] = useState<IGeneratedDocument[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [missing, setMissing] = useState<string[] | null>(null);
  const [pdfError, setPdfError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState(contract.status);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = () =>
    axios
      .get<IGeneratedDocument[]>(`${base}/documents`)
      .then(r => setDocs(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được danh sách văn bản')));

  useEffect(() => {
    load();
  }, [contract.id]);

  const reset = () => {
    setError(null);
    setNotice(null);
    setMissing(null);
  };

  const showResult = (r: IGenerationResult, text: string) => {
    setPdfError(r.pdfError ?? null);
    setNotice(r.pdfError ? null : text);
  };

  const generate = async (force: boolean) => {
    reset();
    setBusy(true);
    try {
      const res = await axios.post<IGenerationResult>(`${base}/documents`, null, { params: { force } });
      showResult(res.data, `Đã tạo văn bản số ${res.data.contractNo} (Word và PDF).`);
      onChanged();
      await load();
    } catch (err: any) {
      const data = err?.response?.data;
      if (data?.message === 'error.missingfields' && Array.isArray(data.missingFields)) {
        setMissing(data.missingFields);
      } else {
        setError(errorText(err, 'Không tạo được văn bản'));
      }
    } finally {
      setBusy(false);
    }
  };

  const retryPdf = async () => {
    reset();
    setBusy(true);
    try {
      const res = await axios.post<IGenerationResult>(`${base}/documents/pdf`);
      showResult(res.data, 'Đã tạo PDF.');
      await load();
    } catch (err) {
      setError(errorText(err, 'Không tạo được PDF'));
    } finally {
      setBusy(false);
    }
  };

  const uploadSigned = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (fileRef.current) fileRef.current.value = '';
    if (!file) return;
    reset();
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      setError('Bản đã ký phải là file PDF');
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setError('File vượt quá dung lượng cho phép (20 MB)');
      return;
    }
    const data = new FormData();
    data.append('file', file);
    setBusy(true);
    try {
      await axios.post(`${base}/signed`, data);
      setNotice('Đã lưu bản đã ký.');
      onChanged();
      await load();
    } catch (err) {
      setError(errorText(err, 'Không tải lên được bản đã ký'));
    } finally {
      setBusy(false);
    }
  };

  const markSigned = async () => {
    reset();
    try {
      await axios.post(`${base}/sign`);
      setStatus('DA_KY');
      setNotice('Đã đánh dấu hợp đồng đã ký.');
      onChanged();
    } catch (err) {
      setError(errorText(err));
    }
  };

  const download = async (d: IGeneratedDocument) => {
    setError(null);
    try {
      await downloadFile(`api/generated-documents/${d.id}/download`, d.fileName);
    } catch (err) {
      setError(errorText(err, 'Không tải được file'));
    }
  };

  const latestDocx = docs.find(d => d.kind === 'DOCX');
  const latestDocxHasPdf = latestDocx != null && docs.some(d => d.kind === 'PDF' && d.sourceDocumentId === latestDocx.id);
  const showRetryPdf = pdfError != null || (latestDocx != null && !latestDocxHasPdf);
  const name = contract.type ? CONTRACT_TYPE_LABEL[contract.type] : 'Hợp đồng';

  return (
    <Modal className="doit-modal" show onHide={onClose} size="lg" aria-labelledby="contract-docs-title" animation={false}>
      <ModalHeader closeButton closeLabel="Đóng">
        <ModalTitle as="h2" id="contract-docs-title">
          Văn bản — {name}
          {contract.contractNo ? ` số ${contract.contractNo}` : ''}
        </ModalTitle>
      </ModalHeader>
      <ModalBody>
        <ServerError message={error} />
        {notice && <Alert variant="success">{notice}</Alert>}
        {missing && (
          <Alert variant="warning">
            <p className="mb-1">Hồ sơ còn thiếu các trường sau; văn bản sẽ để trống những chỗ này:</p>
            <ul>
              {missing.map(f => (
                <li key={f}>{f}</li>
              ))}
            </ul>
            <div className="d-flex gap-2">
              <Button variant="warning" disabled={busy} onClick={() => generate(true)}>
                Vẫn tạo
              </Button>
              <Button variant="outline-secondary" onClick={() => setMissing(null)}>
                Bổ sung hồ sơ trước
              </Button>
            </div>
          </Alert>
        )}
        {pdfError && <Alert variant="warning">{pdfError}. File Word đã được lưu; bấm &quot;Tạo lại PDF&quot; sau.</Alert>}

        <div className="d-flex flex-wrap gap-2 mb-3">
          <Button variant="primary" disabled={busy} onClick={() => generate(false)}>
            Tạo văn bản
          </Button>
          {showRetryPdf && (
            <Button variant="outline-primary" disabled={busy} onClick={retryPdf}>
              Tạo lại PDF
            </Button>
          )}
          <label className="btn btn-outline-secondary mb-0" htmlFor={`signed-${contract.id}`}>
            Tải bản đã ký lên
          </label>
          <input
            ref={fileRef}
            id={`signed-${contract.id}`}
            type="file"
            accept="application/pdf,.pdf"
            className="visually-hidden"
            aria-label="Tải bản đã ký lên (PDF)"
            disabled={busy}
            onChange={uploadSigned}
          />
          {status === 'NHAP' && (
            <Button variant="outline-success" onClick={markSigned}>
              Đánh dấu đã ký
            </Button>
          )}
        </div>

        <div className="table-responsive">
          <table className="table doit-table">
            <thead>
              <tr>
                <th scope="col">Loại</th>
                <th scope="col">File</th>
                <th scope="col">Tạo lúc</th>
                <th scope="col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {docs.length === 0 && (
                <tr>
                  <td colSpan={4}>Chưa có văn bản</td>
                </tr>
              )}
              {docs.map(d => (
                <tr key={d.id}>
                  <td>{FILE_KIND_LABEL[d.kind]}</td>
                  <td>{d.fileName}</td>
                  <td className="text-nowrap">{instantToVn(d.createdAt, true)}</td>
                  <td>
                    <Button variant="link" className="doit-row-action" onClick={() => download(d)} aria-label={`Tải về ${d.fileName}`}>
                      Tải về
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ModalBody>
      <ModalFooter>
        <Button type="button" variant="outline-secondary" onClick={onClose}>
          Đóng
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default ContractDocumentsDialog;
