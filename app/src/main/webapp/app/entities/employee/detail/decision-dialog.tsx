import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { IWorkSite } from 'app/shared/model/work-site.model';

import { vnToIso } from '../vn-date';
import { errorText } from './detail-api';
import { DateField, SelectField, ServerError, TextField } from './form-fields';

interface Props {
  employeeId: number;
  onClose: () => void;
  /** Gọi sau khi tạo, kèm câu thông báo (số quyết định, lỗi PDF nếu có). */
  onSaved: (message: string) => void;
}

const OTHER = 'khac';
const BAD_DATE = 'Ngày không hợp lệ, nhập dd/MM/yyyy';

const todayVn = () => {
  const d = new Date();
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
};

const TYPES: [string, string][] = [
  ['KHACH_HANG', 'Làm việc tại khách hàng'],
  ['CONG_TAC', 'Đi công tác'],
];

/**
 * Quyết định cử nhân sự đi công tác / làm việc tại khách hàng: máy chủ ghi giai đoạn vị trí, cấp số QĐCNS và sinh văn bản
 * trong cùng một lần lưu (giai đoạn chồng lấn thì không tạo gì).
 */
export const DecisionDialog = ({ employeeId, onClose, onSaved }: Props) => {
  const [form, setForm] = useState({
    type: 'KHACH_HANG',
    siteId: '',
    unit: '',
    address: '',
    fromDate: '',
    toDate: '',
    task: '',
    benefits: '',
    signDate: todayVn(),
    signerName: '',
    signerTitle: '',
  });
  const [sites, setSites] = useState<IWorkSite[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    axios
      .get<{ signerName: string; signerTitle: string }>('api/assignment-decisions/defaults')
      .then(r => setForm(f => ({ ...f, signerName: f.signerName || r.data.signerName, signerTitle: f.signerTitle || r.data.signerTitle })))
      .catch(() => undefined);
    axios
      .get<IWorkSite[]>('api/work-sites')
      .then(r => setSites((r.data ?? []).filter(s => s.active !== false)))
      .catch(() => undefined);
  }, []);

  const set = (key: keyof typeof form) => (value: string) => setForm(f => ({ ...f, [key]: value }));

  const siteOptions: [string, string][] = [
    ...sites.filter(s => s.type === form.type).map(s => [String(s.id), s.name] as [string, string]),
    [OTHER, 'Khác (nhập tay)'],
  ];

  const submit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    const e: Record<string, string> = {};
    const required = (key: keyof typeof form) => {
      if (!form[key].trim()) e[key] = 'Bắt buộc nhập';
    };
    required('siteId');
    if (form.siteId === OTHER) required('unit');
    required('fromDate');
    required('task');
    required('benefits');
    required('signerName');
    required('signerTitle');
    for (const key of ['fromDate', 'toDate', 'signDate'] as const) {
      if (!e[key] && form[key].trim() && !vnToIso(form[key])) e[key] = BAD_DATE;
    }
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const custom = form.siteId === OTHER;
    const payload = {
      type: form.type,
      ...(custom ? { unit: form.unit.trim(), address: form.address.trim() || null } : { siteId: Number(form.siteId) }),
      fromDate: vnToIso(form.fromDate),
      toDate: vnToIso(form.toDate) ?? null,
      task: form.task.trim(),
      benefits: form.benefits.trim(),
      signDate: vnToIso(form.signDate) ?? null,
      signerName: form.signerName.trim(),
      signerTitle: form.signerTitle.trim(),
    };
    setSaving(true);
    try {
      const res = await axios.post(`api/employees/${employeeId}/assignment-decisions`, payload);
      const no = res.data?.decision?.decisionNo;
      const pdfError = res.data?.pdfError;
      onSaved(`Đã tạo quyết định số ${no}.${pdfError ? ` ${pdfError}; bấm "Tạo lại PDF" sau.` : ''}`);
      onClose();
    } catch (err) {
      // Lỗi chồng lấn lịch sử vị trí: hiện nguyên câu của máy chủ (detail).
      setServerError(errorText(err, 'Không tạo được quyết định'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal className="doit-modal" show onHide={onClose} size="lg" aria-labelledby="decision-title" animation={false}>
      <form onSubmit={submit} noValidate>
        <ModalHeader closeButton closeLabel="Đóng">
          <ModalTitle as="h2" id="decision-title">
            Cử đi công tác / làm tại khách hàng
          </ModalTitle>
        </ModalHeader>
        <ModalBody>
          <ServerError message={serverError} />
          <div className="row g-3">
            <SelectField
              id="decision-type"
              label="Hình thức"
              value={form.type}
              onChange={v => setForm(f => ({ ...f, type: v, siteId: '' }))}
              options={TYPES}
            />
            <SelectField
              id="decision-site"
              label="Nơi đến"
              value={form.siteId}
              onChange={set('siteId')}
              options={siteOptions}
              emptyLabel="— Chọn —"
              error={errors.siteId}
            />
            {form.siteId === OTHER && (
              <>
                <TextField
                  id="decision-unit"
                  label="Đơn vị / khách hàng"
                  value={form.unit}
                  onChange={set('unit')}
                  error={errors.unit}
                  required
                />
                <TextField id="decision-address" label="Địa chỉ" value={form.address} onChange={set('address')} />
              </>
            )}
            <DateField
              id="decision-from"
              label="Từ ngày"
              value={form.fromDate}
              onChange={set('fromDate')}
              error={errors.fromDate}
              required
            />
            <DateField
              id="decision-to"
              label="Đến ngày"
              value={form.toDate}
              onChange={set('toDate')}
              error={errors.toDate}
              hint="Để trống: đến khi có quyết định khác"
            />
            <TextField
              id="decision-task"
              label="Nội dung công việc"
              value={form.task}
              onChange={set('task')}
              error={errors.task}
              multiline
              required
              className="col-12"
            />
            <TextField
              id="decision-benefits"
              label="Chế độ"
              value={form.benefits}
              onChange={set('benefits')}
              error={errors.benefits}
              multiline
              required
              className="col-12"
            />
            <DateField id="decision-sign-date" label="Ngày ký" value={form.signDate} onChange={set('signDate')} error={errors.signDate} />
            <TextField
              id="decision-signer"
              label="Người ký"
              value={form.signerName}
              onChange={set('signerName')}
              error={errors.signerName}
              required
            />
            <TextField
              id="decision-signer-title"
              label="Chức vụ người ký"
              value={form.signerTitle}
              onChange={set('signerTitle')}
              error={errors.signerTitle}
              required
            />
          </div>
        </ModalBody>
        <ModalFooter>
          <Button type="button" variant="outline-secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={saving}>
            Tạo quyết định
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

export default DecisionDialog;
