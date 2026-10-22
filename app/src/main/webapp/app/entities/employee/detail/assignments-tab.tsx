import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { IWorkSite } from 'app/shared/model/work-site.model';

import { isoToVn, vnToIso } from '../vn-date';
import { ISegment, LOCATION_TYPE_LABEL, errorText, useRoles } from './detail-api';
import DecisionsSection from './decisions-section';
import { DateField, SelectField, ServerError, TextField } from './form-fields';

interface IAssignmentsTabProps {
  employeeId: number;
  /** Gọi sau khi thêm để trang tải lại "Nơi làm việc" ở thẻ đầu. */
  onChanged?: () => void;
}

const BAD_DATE = 'Ngày không hợp lệ, nhập dd/MM/yyyy';
const OTHER = 'khac';
const NEW_SITE_LABEL = 'Khách hàng / đơn vị mới…';
const NEW_SITE_HINT = 'Lưu vào danh mục nơi làm việc để lần sau chọn lại được.';

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const period = (s: ISegment) => `${isoToVn(s.from)} – ${s.to ? isoToVn(s.to) : 'nay'}`;

const isCurrent = (s: ISegment, today: string) => s.from <= today && (!s.to || s.to >= today);

const emptyForm = { siteId: '', type: 'VAN_PHONG', unit: '', address: '', fromDate: '', toDate: '', decisionNo: '', note: '' };

export const AssignmentsTab = ({ employeeId, onChanged }: IAssignmentsTabProps) => {
  const { canWrite, canManage } = useRoles();
  const [segments, setSegments] = useState<ISegment[]>([]);
  const [sites, setSites] = useState<IWorkSite[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<ISegment | null>(null);
  const [toEnd, setToEnd] = useState<ISegment | null>(null);
  const [endValue, setEndValue] = useState('');
  const [endError, setEndError] = useState<string | undefined>(undefined);
  const [endServerError, setEndServerError] = useState<string | null>(null);

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
    // Tải lại mỗi lần mở: khách hàng mới nhập ở lần trước đã được lưu vào danh mục.
    axios
      .get<IWorkSite[]>('api/work-sites')
      .then(r => setSites((r.data ?? []).filter(s => s.active !== false)))
      .catch(() => undefined);
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

  const confirmDelete = async () => {
    if (!toDelete?.id) return;
    setActionError(null);
    try {
      await axios.delete(`api/employees/${employeeId}/assignments/${toDelete.id}`);
      load();
      onChanged?.();
    } catch (err) {
      setActionError(errorText(err, 'Không xóa được giai đoạn'));
    } finally {
      setToDelete(null);
    }
  };

  const openEnd = (s: ISegment) => {
    setEndValue(s.to ? isoToVn(s.to) : '');
    setEndError(undefined);
    setEndServerError(null);
    setToEnd(s);
  };

  const submitEnd = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!toEnd?.id) return;
    const iso = vnToIso(endValue);
    if (iso === undefined) {
      setEndError(BAD_DATE);
      return;
    }
    setEndError(undefined);
    setEndServerError(null);
    try {
      await axios.patch(`api/employees/${employeeId}/assignments/${toEnd.id}/end`, { toDate: iso ?? null });
      setToEnd(null);
      load();
      onChanged?.();
    } catch (err) {
      setEndServerError(errorText(err));
    }
  };

  const today = todayIso();
  const ordered = [...segments].sort((a, b) => b.from.localeCompare(a.from));
  // Một giai đoạn có thể bị cắt thành nhiều đoạn (vd. xen đợt công tác): chỉ đặt nút ở đoạn muộn nhất.
  const seen = new Set<number>();
  const withActions = new Set<ISegment>();
  ordered.forEach(s => {
    if (s.id != null && !seen.has(s.id)) {
      seen.add(s.id);
      withActions.add(s);
    }
  });

  return (
    <>
      <section className="doit-section" aria-labelledby="h-assignments">
        <div className="doit-section-head">
          <h2 id="h-assignments">Lịch sử công tác</h2>
          {canWrite && !formOpen && (
            <Button type="button" variant="link" onClick={openForm}>
              + Thêm điều động
            </Button>
          )}
        </div>

        {actionError && (
          <div role="alert" className="alert alert-danger">
            {actionError}
          </div>
        )}

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
                options={[...sites.map(s => [String(s.id), s.name ?? ''] as [string, string]), [OTHER, NEW_SITE_LABEL]]}
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
                  <TextField
                    id="assignment-unit"
                    label="Tên khách hàng / đơn vị"
                    value={form.unit}
                    onChange={set('unit')}
                    error={errors.unit}
                    hint={NEW_SITE_HINT}
                    required
                  />
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
              <Button type="button" variant="outline-secondary" onClick={() => setFormOpen(false)}>
                Hủy
              </Button>
              <Button type="submit" variant="primary" disabled={saving}>
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
                <li key={`${s.id ?? 'gap'}-${s.from}-${s.to ?? ''}-${place}`}>
                  <span className={`doit-dot ${current ? 'doit-dot-current' : ''}`} aria-hidden="true" />
                  <div>
                    <span className="doit-timeline-period">{period(s)}</span>
                    <span className="doit-timeline-place">
                      {place}
                      {current && <span className="doit-badge doit-badge-warn ms-2">Hiện tại</span>}
                    </span>
                    {s.type && <span className="doit-timeline-note">{LOCATION_TYPE_LABEL[s.type]}</span>}
                    {canWrite && withActions.has(s) && (
                      <span className="d-flex flex-wrap gap-2">
                        <Button
                          type="button"
                          variant="link"
                          className="doit-row-action"
                          onClick={() => openEnd(s)}
                          aria-label={`Đổi ngày kết thúc ${period(s)}`}
                        >
                          Đổi ngày kết thúc
                        </Button>
                        <Button
                          type="button"
                          variant="link"
                          className="text-danger doit-row-action"
                          onClick={() => setToDelete(s)}
                          aria-label={`Xóa giai đoạn ${period(s)}`}
                        >
                          Xóa
                        </Button>
                      </span>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
        )}

        <Modal
          className="doit-modal"
          show={toDelete !== null}
          onHide={() => setToDelete(null)}
          aria-labelledby="assignment-delete-title"
          animation={false}
        >
          <ModalHeader closeButton closeLabel="Đóng">
            <ModalTitle as="h2" id="assignment-delete-title">
              Xóa giai đoạn làm việc
            </ModalTitle>
          </ModalHeader>
          <ModalBody>
            <p className="mb-2">{toDelete ? `${period(toDelete)} · ${toDelete.unit || toDelete.label || ''}` : ''}</p>
            <p className="mb-0">Xóa giai đoạn này? Thao tác được ghi vào nhật ký.</p>
          </ModalBody>
          <ModalFooter>
            <Button type="button" variant="outline-secondary" onClick={() => setToDelete(null)}>
              Hủy
            </Button>
            <Button type="button" variant="danger" onClick={() => void confirmDelete()}>
              Xóa
            </Button>
          </ModalFooter>
        </Modal>

        <Modal
          className="doit-modal"
          show={toEnd !== null}
          onHide={() => setToEnd(null)}
          aria-labelledby="assignment-end-title"
          animation={false}
        >
          <form onSubmit={e => void submitEnd(e)} noValidate>
            <ModalHeader closeButton closeLabel="Đóng">
              <ModalTitle as="h2" id="assignment-end-title">
                Đổi ngày kết thúc
              </ModalTitle>
            </ModalHeader>
            <ModalBody>
              <ServerError message={endServerError} />
              <p>{toEnd ? `${period(toEnd)} · ${toEnd.unit || toEnd.label || ''}` : ''}</p>
              <div className="row g-3">
                <DateField
                  id="assignment-new-end"
                  label="Ngày kết thúc mới"
                  className="col-12"
                  value={endValue}
                  onChange={setEndValue}
                  error={endError}
                  hint="Để trống nếu chưa xác định (không áp dụng cho công tác)"
                />
              </div>
            </ModalBody>
            <ModalFooter>
              <Button type="button" variant="outline-secondary" onClick={() => setToEnd(null)}>
                Hủy
              </Button>
              <Button type="submit" variant="primary">
                Lưu ngày kết thúc
              </Button>
            </ModalFooter>
          </form>
        </Modal>
      </section>
      <DecisionsSection
        employeeId={employeeId}
        canWrite={canWrite}
        canView={canManage}
        onCreated={() => {
          load();
          onChanged?.();
        }}
      />
    </>
  );
};

export default AssignmentsTab;
