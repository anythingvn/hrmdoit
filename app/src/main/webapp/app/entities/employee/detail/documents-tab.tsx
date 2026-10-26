import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { MAX_UPLOAD_BYTES } from 'app/shared/util/download';

import { instantToVn } from '../vn-date';
import { IDocument, errorText, useRoles } from './detail-api';

const sizeText = (bytes?: number) => {
  if (bytes == null) return '';
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
};

const kind = (doc: IDocument) => {
  const ext = doc.fileName.split('.').pop()?.toUpperCase() ?? '';
  return ext === 'JPEG' ? 'JPG' : ext;
};

/** Dung lượng tối đa mỗi file hồ sơ scan. */
export const MAX_DOCUMENT_BYTES = MAX_UPLOAD_BYTES;

/** Hồ sơ scan: danh sách file, tải lên (PDF/JPEG/PNG ≤ 20 MB), tải về, xóa. {@code title}: tiêu đề thẻ (tab Thông tin dùng "Hồ sơ scan"). */
export const DocumentsTab = ({ employeeId, title = 'Hồ sơ scan' }: { employeeId: number; title?: string }) => {
  const { canWrite } = useRoles();
  const [docs, setDocs] = useState<IDocument[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [toDelete, setToDelete] = useState<IDocument | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = () => {
    axios
      .get<IDocument[]>(`api/employees/${employeeId}/documents`)
      .then(r => setDocs(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được danh sách hồ sơ')));
  };

  useEffect(load, [employeeId]);

  const upload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError(null);
    if (file.size > MAX_DOCUMENT_BYTES) {
      // Chặn ngay ở trình duyệt, không gửi file lớn lên máy chủ rồi mới bị từ chối.
      setError(`File "${file.name}" có dung lượng ${sizeText(file.size)}, vượt quá 20 MB. Vui lòng chọn file nhỏ hơn.`);
      if (fileRef.current) fileRef.current.value = '';
      return;
    }
    setUploading(true);
    const data = new FormData();
    data.append('file', file);
    try {
      await axios.post(`api/employees/${employeeId}/documents`, data);
      load();
    } catch (err) {
      setError(errorText(err, 'Không tải lên được file'));
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const download = async (doc: IDocument) => {
    setError(null);
    try {
      const res = await axios.get<Blob>(`api/employee-documents/${doc.id}/download`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = doc.fileName;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      // Phản hồi dạng blob nên không đọc được detail; 404 = file không còn trên máy chủ.
      setError(
        (err as { response?: { status?: number } })?.response?.status === 404
          ? 'Không tìm thấy file trên máy chủ'
          : errorText(err, 'Không tải về được file'),
      );
    }
  };

  const confirmDelete = async () => {
    if (!toDelete) return;
    setError(null);
    try {
      await axios.delete(`api/employee-documents/${toDelete.id}`);
      load();
    } catch (err) {
      setError(errorText(err, 'Không xóa được file'));
    } finally {
      setToDelete(null);
    }
  };

  return (
    <section className="doit-section" aria-labelledby="h-docs">
      <div className="doit-section-head">
        <h2 id="h-docs">{title}</h2>
        {canWrite && (
          <div>
            <input
              ref={fileRef}
              id="document-file"
              type="file"
              className="visually-hidden"
              accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
              onChange={e => void upload(e)}
              disabled={uploading}
              aria-describedby="document-file-hint"
            />
            <label htmlFor="document-file" className="btn btn-outline-secondary doit-upload">
              {uploading ? 'Đang tải lên…' : 'Tải lên'}
            </label>
          </div>
        )}
      </div>
      {canWrite && (
        <p id="document-file-hint" className="text-muted mb-0">
          PDF, JPEG hoặc PNG, tối đa 20 MB.
        </p>
      )}
      {error && (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      )}
      {docs.length === 0 ? (
        <p className="mb-0">Chưa có hồ sơ</p>
      ) : (
        <ul className="doit-docs">
          {docs.map(d => (
            <li key={d.id}>
              <span className="doit-doc-kind" aria-hidden="true">
                {kind(d)}
              </span>
              <div className="doit-doc-body">
                <Button type="button" variant="link" className="doit-doc-name" onClick={() => void download(d)}>
                  {d.fileName}
                </Button>
                <span className="text-muted small">{[sizeText(d.sizeBytes), instantToVn(d.uploadedAt)].filter(Boolean).join(' · ')}</span>
              </div>
              {canWrite && (
                <Button
                  type="button"
                  variant="link"
                  className="text-danger doit-row-action"
                  onClick={() => setToDelete(d)}
                  aria-label={`Xóa ${d.fileName}`}
                >
                  Xóa
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      <Modal
        className="doit-modal"
        show={toDelete !== null}
        onHide={() => setToDelete(null)}
        aria-labelledby="doc-delete-title"
        animation={false}
      >
        <ModalHeader closeButton closeLabel="Đóng">
          <ModalTitle as="h2" id="doc-delete-title">
            Xóa hồ sơ
          </ModalTitle>
        </ModalHeader>
        <ModalBody>Xóa file “{toDelete?.fileName}”? File sẽ bị gỡ khỏi hồ sơ nhân viên.</ModalBody>
        <ModalFooter>
          <Button type="button" variant="outline-secondary" onClick={() => setToDelete(null)}>
            Hủy
          </Button>
          <Button type="button" variant="danger" onClick={() => void confirmDelete()}>
            Xóa
          </Button>
        </ModalFooter>
      </Modal>
    </section>
  );
};

export default DocumentsTab;
