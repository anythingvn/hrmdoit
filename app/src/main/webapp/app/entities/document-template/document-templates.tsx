import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Badge, Button, Card, Form, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { instantToVn } from 'app/entities/employee/vn-date';
import { downloadFile } from 'app/shared/util/download';

export interface ITemplateVersion {
  id: number;
  kind: string;
  version: number;
  fileName: string;
  uploadedBy?: string | null;
  uploadedAt: string;
  active: boolean;
}

export interface ITemplateKind {
  kind: string;
  label: string;
  /** 0 = mẫu gốc; null = chưa có mẫu. */
  activeVersion: number | null;
  hasDefault: boolean;
  allowedTags: string[];
  versions: ITemplateVersion[];
}

/** Khớp giới hạn máy chủ (5 MB). */
export const MAX_TEMPLATE_BYTES = 5 * 1024 * 1024;

const activeText = (k: ITemplateKind) => {
  if (k.activeVersion == null) return 'Chưa có mẫu — tải mẫu Word lên trước khi tạo văn bản loại này';
  if (k.activeVersion === 0) return 'Đang dùng: mẫu gốc';
  const v = k.versions.find(x => x.version === k.activeVersion);
  return `Đang dùng: phiên bản ${k.activeVersion}${v ? ` — ${v.fileName}` : ''}`;
};

/** Mẫu văn bản: phiên bản đang dùng, tải về, tải mẫu mới (máy chủ kiểm tra thẻ), quay lại phiên bản cũ hoặc mẫu gốc. */
export const DocumentTemplates = () => {
  const [kinds, setKinds] = useState<ITemplateKind[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = () =>
    axios
      .get<ITemplateKind[]>('api/document-templates')
      .then(r => setKinds(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được danh sách mẫu')));

  useEffect(() => {
    load();
  }, []);

  const upload = async (k: ITemplateKind, input: HTMLInputElement) => {
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    setError(null);
    setNotice(null);
    if (!file.name.toLowerCase().endsWith('.docx')) {
      setError('Chỉ nhận file Word .docx (Word 2007 trở lên)');
      return;
    }
    if (file.size > MAX_TEMPLATE_BYTES) {
      setError('Mẫu tối đa 5 MB');
      return;
    }
    const data = new FormData();
    data.append('file', file);
    setBusy(k.kind);
    try {
      await axios.post(`api/document-templates/${k.kind}`, data);
      setNotice(`Đã nhận mẫu mới cho ${k.label}.`);
      await load();
    } catch (err) {
      setError(errorText(err, 'Không tải lên được mẫu'));
    } finally {
      setBusy(null);
    }
  };

  const activate = async (k: ITemplateKind, version: number) => {
    setError(null);
    setNotice(null);
    try {
      await axios.post(`api/document-templates/${k.kind}/versions/${version}/activate`);
      setNotice(version === 0 ? `${k.label}: đã quay lại mẫu gốc.` : `${k.label}: đang dùng phiên bản ${version}.`);
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  };

  const download = async (k: ITemplateKind, version: number, fileName: string) => {
    setError(null);
    try {
      await downloadFile(`api/document-templates/${k.kind}/download`, fileName, { version });
    } catch (err) {
      setError(errorText(err, 'Không tải được mẫu'));
    }
  };

  return (
    <div>
      <p className="text-muted">
        Sửa câu chữ trong Word rồi tải lên làm phiên bản mới. Giữ nguyên các thẻ dạng <code>{'{{hoTen}}'}</code>; nếu cần sửa một thẻ, xóa
        cả thẻ rồi gõ lại một lần, không định dạng riêng một phần của thẻ. Máy chủ từ chối mẫu có thẻ bị cắt hoặc thẻ lạ.
      </p>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      {kinds.map(k => (
        <Card key={k.kind} className="mb-3" data-testid={`template-${k.kind}`}>
          <Card.Body>
            <div className="d-flex flex-wrap justify-content-between gap-2 align-items-start">
              <div>
                <h2 className="h5 mb-1">{k.label}</h2>
                <div className={k.activeVersion == null ? 'text-danger' : 'text-muted'}>{activeText(k)}</div>
              </div>
              <div className="d-flex flex-wrap gap-2">
                {k.hasDefault && (
                  <Button variant="outline-secondary" onClick={() => download(k, 0, `${k.kind}-mau-goc.docx`)}>
                    Tải mẫu gốc
                  </Button>
                )}
                {k.hasDefault && k.activeVersion !== 0 && (
                  <Button variant="outline-secondary" onClick={() => activate(k, 0)}>
                    Dùng mẫu gốc
                  </Button>
                )}
                <Form.Label className="btn btn-primary mb-0" htmlFor={`upload-${k.kind}`}>
                  {busy === k.kind ? 'Đang kiểm tra…' : 'Tải mẫu mới'}
                </Form.Label>
                <input
                  id={`upload-${k.kind}`}
                  type="file"
                  accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  className="visually-hidden"
                  aria-label={`Tải mẫu mới ${k.label}`}
                  disabled={busy != null}
                  onChange={e => upload(k, e.target)}
                />
              </div>
            </div>
            {k.versions.length > 0 && (
              <Table responsive size="sm" className="mt-3 mb-0 align-middle">
                <thead>
                  <tr>
                    <th>Phiên bản</th>
                    <th>File</th>
                    <th>Người tải lên</th>
                    <th>Ngày</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {k.versions.map(v => (
                    <tr key={v.id}>
                      <td>
                        {v.version} {v.active && <Badge bg="success">Đang dùng</Badge>}
                      </td>
                      <td>{v.fileName}</td>
                      <td>{v.uploadedBy ?? ''}</td>
                      <td>{instantToVn(v.uploadedAt)}</td>
                      <td className="text-end text-nowrap">
                        <Button variant="link" onClick={() => download(k, v.version, v.fileName)}>
                          Tải về
                        </Button>
                        {!v.active && (
                          <Button variant="link" onClick={() => activate(k, v.version)}>
                            Dùng lại
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
            <details className="mt-2">
              <summary>Thẻ dùng được</summary>
              <div className="small">
                {k.allowedTags.map(t => (
                  <code key={t} className="me-2">{`{{${t}}}`}</code>
                ))}
              </div>
            </details>
          </Card.Body>
        </Card>
      ))}
    </div>
  );
};

export default DocumentTemplates;
