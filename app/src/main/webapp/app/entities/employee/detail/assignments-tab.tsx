import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button } from 'reactstrap';

import { IWorkSite } from 'app/shared/model/work-site.model';

import { isoToVn, vnToIso } from '../vn-date';
import { ISegment, LOCATION_TYPE_LABEL, WorkLocationType, errorText, useRoles } from './detail-api';
import { DateField, SelectField, ServerError, TextField } from './form-fields';

interface IAssignmentsTabProps {
  employeeId: number;
  /** Gọi sau khi thêm để trang tải lại "Nơi làm việc" ở thẻ đầu. */
  onChanged?: () => void;
}

const BAD_DATE = 'Ngày không hợp lệ, nhập dd/MM/yyyy';
const OTHER = 'khac';

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const period = (s: ISegment) => `${isoToVn(s.from)} – ${s.to ? isoToVn(s.to) : 'nay'}`;

const isCurrent = (s: ISegment, today: string) => s.from <= today && (!s.to || s.to >= today);

const emptyForm = { siteId: '', type: 'VAN_PHONG', unit: '', address: '', fromDate: '', toDate: '', decisionNo: '', note: '' };

export const AssignmentsTab = ({ employeeId, onChanged }: IAssignmentsTabProps) => {
  const { canWrite } = useRoles();
  const [segments, setSegments] = useState<ISegment[]>([]);
  const [sites, setSites] = useState<IWorkSite[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const load = () => {
    axios
      .get<ISegment[]>(`api/employees/${employeeId}/assignments/timeline`)
      .then(r => setSegments(r.data ?? []))
      .catch(err => setLoadError(errorText(err, 'Không tải được lịch sử công tác')));
  };

  useEffect(load, [employeeId]);

  const openForm = () => {
    setForm(emptyForm);
    setErrors({});
    setServerError(null);
    setFormOpen(true);
    if (sites.length === 0) {
      axios
        .get<IWorkSite[]>('api/work-sites')
        .then(r => setSites((r.data ?? []).filter(s => s.active !== false)))
        .catch(() => undefined);
    }
  };

  const set = (key: keyof typeof emptyForm) => (value: string) => setForm(f => ({ ...f, [key]: value }));

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    const e: Record<string, string> = {};
    if (!form.siteId) e.siteId = 'Chọn nơi làm việc';
    if (form.siteId === OTHER && !form.unit.trim()) e.unit = 'Bắt buộc nhập';
    if (!form.fromDate.trim()) e.fromDate = 'Bắt buộc nhập';
    else if (!vnToIso(form.fromDate)) e.fromDate = BAD_DATE;
    if (vnToIso(form.toDate) === undefined) e.toDate = BAD_DATE;
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const custom = form.siteId === OTHER;
    const payload = {
      ...(custom
        ? { type: form.type, unit: form.unit.trim(), address: form.address.trim() || null }
        : { site: { id: Number(form.siteId) } }),
      fromDate: vnToIso(form.fromDate),
      toDate: vnToIso(form.toDate) ?? null,
      decisionNo: form.decisionNo.trim() || null,
      note: form.note.trim() || null,
    };
    setSaving(true);
    try {
      await axios.post(`api/employees/${employeeId}/assignments`, payload);
      setFormOpen(false);
      load();
      onChanged?.();
    } catch (err) {
      // Lỗi chồng lấn: hiện nguyên câu báo của server (detail).
      setServerError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const today = todayIso();
  const ordered = [...segments].sort((a, b) => b.from.localeCompare(a.from));

  return (
    <section className="doit-section" aria-labelledby="h-assignments">
      <div className="doit-section-head">
        <h2 id="h-assignments">Lịch sử công tác</h2>
        {canWrite && !formOpen && (
          <Button type="button" color="link" onClick={openForm}>
            + Thêm điều động
          </Button>
        )}
      </div>

      {loadError && (
        <div role="alert" className="alert alert-danger">
          {loadError}
        </div>
      )}

      {formOpen && (
        <form className="doit-subform" onSubmit={e => void handleSubmit(e)} noValidate aria-labelledby="h-assignment-form">
          <h3 id="h-assignment-form" className="h6">
            Thêm điều động / vị trí làm việc
          </h3>
          <ServerError message={serverError} />
          <div className="row g-3">
            <SelectField
              id="assignment-site"
              label="Nơi làm việc"
              value={form.siteId}
              onChange={set('siteId')}
              emptyLabel="Chọn nơi làm việc"
              options={[...sites.map(s => [String(s.id), s.name ?? ''] as [string, string]), [OTHER, 'Khác (nhập tay)']]}
              error={errors.siteId}
            />
            {form.siteId === OTHER && (
              <>
                <SelectField
                  id="assignment-type"
                  label="Loại"
                  value={form.type}
                  onChange={set('type')}
                  options={Object.entries(LOCATION_TYPE_LABEL)}
                />
                <TextField id="assignment-unit" label="Đơn vị" value={form.unit} onChange={set('unit')} error={errors.unit} required />
                <TextField id="assignment-address" label="Địa chỉ" value={form.address} onChange={set('address')} />
              </>
            )}
            <DateField
              id="assignment-from"
              label="Từ ngày"
              value={form.fromDate}
              onChange={set('fromDate')}
              error={errors.fromDate}
              required
            />
            <DateField
              id="assignment-to"
              label="Đến ngày"
              value={form.toDate}
              onChange={set('toDate')}
              error={errors.toDate}
              hint="Để trống nếu chưa xác định"
            />
            <TextField id="assignment-decision" label="Số quyết định" value={form.decisionNo} onChange={set('decisionNo')} />
            <TextField id="assignment-note" label="Ghi chú" value={form.note} onChange={set('note')} />
          </div>
          <div className="d-flex gap-2 justify-content-end mt-3">
            <Button type="button" color="secondary" outline onClick={() => setFormOpen(false)}>
              Hủy
            </Button>
            <Button type="submit" color="primary" disabled={saving}>
              Lưu
            </Button>
          </div>
        </form>
      )}

      {ordered.length === 0 ? (
        <p className="mb-0">Chưa có lịch sử công tác</p>
      ) : (
        <ol className="doit-timeline">
          {ordered.map(s => {
            const current = isCurrent(s, today);
            const place = s.unit || s.label || (s.type ? LOCATION_TYPE_LABEL[s.type] : '');
            return (
              <li key={`${s.from}-${s.to ?? ''}-${place}`}>
                <span className={`doit-dot ${current ? 'doit-dot-current' : ''}`} aria-hidden="true" />
                <div>
                  <span className="doit-timeline-period">{period(s)}</span>
                  <span className="doit-timeline-place">
                    {place}
                    {current && <span className="doit-badge doit-badge-warn ms-2">Hiện tại</span>}
                  </span>
                  {s.type && <span className="doit-timeline-note">{LOCATION_TYPE_LABEL[s.type]}</span>}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
};

export default AssignmentsTab;
