import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import DocumentNumbers, { DOCUMENT_TYPE_LABEL, IDocumentSequence } from 'app/modules/administration/document-numbers/document-numbers';
import { SectionCard } from 'app/shared/ui';

import { INumbering, INumberingValues, settingsApi } from './settings-api';
import { setNumberingCache } from './use-numbering';

const ONLY_NEW = 'Chỉ áp dụng cho văn bản và nhân viên tạo mới; số và mã đã cấp giữ nguyên.';
const DOC_TYPES = Object.keys(DOCUMENT_TYPE_LABEL);
const STAFF_FIELDS = [
  {
    key: 'staffCodePrefix',
    label: 'Tiền tố mã nhân viên',
    example: (p: string) => `Nhân viên vào làm năm ${new Date().getFullYear()}: ${p}${String(new Date().getFullYear()).slice(2)}xx`,
  },
  {
    key: 'internCodePrefix',
    label: 'Tiền tố mã thực tập',
    example: (p: string) => `Thực tập: ${p}${String(new Date().getFullYear()).slice(2)}xx`,
  },
  { key: 'serviceCodePrefix', label: 'Tiền tố mã HĐ dịch vụ', example: (p: string) => `HĐ dịch vụ: ${p} + số thứ tự` },
] as const;
type StaffKey = (typeof STAFF_FIELDS)[number]['key'];

interface IDrafts {
  documentPrefix: string;
  documentCodes: Record<string, string>;
  staffCodePrefix: string;
  internCodePrefix: string;
  serviceCodePrefix: string;
}

const toDrafts = (n: INumbering): IDrafts => ({
  documentPrefix: n.documentPrefix ?? '',
  documentCodes: Object.fromEntries(DOC_TYPES.map(t => [t, n.documentCodes?.[t] ?? ''])),
  staffCodePrefix: n.staffCodePrefix ?? '',
  internCodePrefix: n.internCodePrefix ?? '',
  serviceCodePrefix: n.serviceCodePrefix ?? '',
});

/** Rỗng hoặc bằng mặc định thì gửi null để luôn đi theo mặc định. */
const orNull = (value: string, def: string | null | undefined): string | null => {
  const v = value.trim();
  return v === '' || v === (def ?? '') ? null : v;
};

/** Giá trị đang hiệu lực ở máy chủ (đã lưu), dùng cho thẻ không được bấm Lưu. */
const payloadOf = (drafts: IDrafts, n: INumbering, part: 'doc' | 'staff'): INumberingValues => {
  const base = toDrafts(n);
  const d =
    part === 'doc'
      ? {
          ...drafts,
          staffCodePrefix: base.staffCodePrefix,
          internCodePrefix: base.internCodePrefix,
          serviceCodePrefix: base.serviceCodePrefix,
        }
      : {
          ...base,
          staffCodePrefix: drafts.staffCodePrefix,
          internCodePrefix: drafts.internCodePrefix,
          serviceCodePrefix: drafts.serviceCodePrefix,
        };
  const def = n.defaults;
  return {
    documentPrefix: orNull(d.documentPrefix, def.documentPrefix),
    documentCodes: Object.fromEntries(DOC_TYPES.map(t => [t, orNull(d.documentCodes[t], def.documentCodes?.[t])])),
    staffCodePrefix: orNull(d.staffCodePrefix, def.staffCodePrefix),
    internCodePrefix: orNull(d.internCodePrefix, def.internCodePrefix),
    serviceCodePrefix: orNull(d.serviceCodePrefix, def.serviceCodePrefix),
  };
};

/** Tab "Mã & số văn bản": tiền tố và mã loại văn bản, tiền tố mã nhân viên; bảng đặt số bắt đầu theo năm nằm bên dưới. */
export const NumberingTab = ({ canEdit }: { canEdit: boolean }) => {
  const [server, setServer] = useState<INumbering | null>(null);
  const [drafts, setDrafts] = useState<IDrafts | null>(null);
  const [sequences, setSequences] = useState<IDocumentSequence[]>([]);
  const [tableVersion, setTableVersion] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [year] = useState(() => new Date().getFullYear());

  useEffect(() => {
    settingsApi
      .numbering()
      .then(n => {
        setServer(n);
        setDrafts(toDrafts(n));
      })
      .catch(err => setError(errorText(err, 'Không tải được cấu hình mã và số văn bản')));
  }, []);

  useEffect(() => {
    axios
      .get<IDocumentSequence[]>('api/admin/document-sequences', { params: { year } })
      .then(r => setSequences(Array.isArray(r?.data) ? r.data : []))
      .catch(() => setSequences([]));
  }, [year, tableVersion]);

  const save = async (part: 'doc' | 'staff') => {
    if (!server || !drafts) return;
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      const res = await settingsApi.saveNumbering(payloadOf(drafts, server, part));
      setServer(res);
      setNumberingCache(res);
      setDrafts(d => {
        const fresh = toDrafts(res);
        // Thẻ còn lại giữ nguyên phần đang gõ dở.
        return part === 'doc'
          ? {
              ...fresh,
              staffCodePrefix: d?.staffCodePrefix ?? fresh.staffCodePrefix,
              internCodePrefix: d?.internCodePrefix ?? fresh.internCodePrefix,
              serviceCodePrefix: d?.serviceCodePrefix ?? fresh.serviceCodePrefix,
            }
          : {
              ...(d ?? fresh),
              staffCodePrefix: fresh.staffCodePrefix,
              internCodePrefix: fresh.internCodePrefix,
              serviceCodePrefix: fresh.serviceCodePrefix,
            };
      });
      setTableVersion(v => v + 1);
      setNotice('Đã lưu. ' + ONLY_NEW);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const def = server?.defaults;
  const effective = (value: string, d?: string | null) => value.trim() || d || '';
  const nextValue = sequences.find(s => s.type === 'HDLD')?.nextValue ?? 1;

  return (
    <div className="d-flex flex-column gap-3">
      {error && (
        <Alert variant="danger" role="alert" className="mb-0">
          {error}
        </Alert>
      )}
      {notice && (
        <Alert variant="success" className="mb-0" onClose={() => setNotice(null)} dismissible>
          {notice}
        </Alert>
      )}
      {drafts && def && (
        <>
          <SectionCard title="Số văn bản" titleId="numbering-doc-title">
            <p className="text-muted mb-0">
              {ONLY_NEW}
              {!canEdit && ' Chỉ Quản trị sửa được.'}
            </p>
            <div className="settings-grid">
              <Form.Group controlId="numbering-doc-prefix">
                <Form.Label>Tiền tố số văn bản</Form.Label>
                <Form.Control
                  style={{ minHeight: 44 }}
                  value={drafts.documentPrefix}
                  maxLength={10}
                  disabled={!canEdit}
                  onChange={e => setDrafts(d => d && { ...d, documentPrefix: e.target.value })}
                />
                <Form.Text>mặc định: {def.documentPrefix}</Form.Text>
              </Form.Group>
              {DOC_TYPES.map(t => (
                <Form.Group key={t} controlId={`numbering-doc-${t}`}>
                  <Form.Label>{`Mã ${DOCUMENT_TYPE_LABEL[t]}`}</Form.Label>
                  <Form.Control
                    style={{ minHeight: 44 }}
                    value={drafts.documentCodes[t]}
                    maxLength={10}
                    disabled={!canEdit}
                    onChange={e => setDrafts(d => d && { ...d, documentCodes: { ...d.documentCodes, [t]: e.target.value } })}
                  />
                  <Form.Text>mặc định: {def.documentCodes?.[t]}</Form.Text>
                </Form.Group>
              ))}
            </div>
            <p className="mb-0">
              {`Số tiếp theo: ${effective(drafts.documentPrefix, def.documentPrefix)}/${year}/${effective(drafts.documentCodes.HDLD, def.documentCodes?.HDLD)}/${nextValue} (dự kiến)`}
            </p>
            {canEdit && (
              <div>
                <Button style={{ minHeight: 44 }} disabled={busy} onClick={() => save('doc')}>
                  Lưu số văn bản
                </Button>
              </div>
            )}
          </SectionCard>
          <SectionCard title="Mã nhân viên" titleId="numbering-staff-title">
            <p className="text-muted mb-0">
              {ONLY_NEW}
              {!canEdit && ' Chỉ Quản trị sửa được.'}
            </p>
            <div className="settings-grid">
              {STAFF_FIELDS.map(f => (
                <Form.Group key={f.key} controlId={`numbering-${f.key}`}>
                  <Form.Label>{f.label}</Form.Label>
                  <Form.Control
                    style={{ minHeight: 44 }}
                    value={drafts[f.key as StaffKey]}
                    maxLength={10}
                    disabled={!canEdit}
                    onChange={e => setDrafts(d => d && { ...d, [f.key]: e.target.value })}
                  />
                  <Form.Text>mặc định: {def[f.key as StaffKey]}</Form.Text>
                  <Form.Text className="d-block">{f.example(effective(drafts[f.key as StaffKey], def[f.key as StaffKey]))}</Form.Text>
                </Form.Group>
              ))}
            </div>
            {canEdit && (
              <div>
                <Button style={{ minHeight: 44 }} disabled={busy} onClick={() => save('staff')}>
                  Lưu mã nhân viên
                </Button>
              </div>
            )}
          </SectionCard>
        </>
      )}
      <DocumentNumbers key={tableVersion} canEdit={canEdit} />
    </div>
  );
};

export default NumberingTab;
