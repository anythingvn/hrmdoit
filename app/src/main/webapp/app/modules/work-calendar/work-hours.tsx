import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn, vnToIso } from 'app/entities/employee/vn-date';

export interface ISchedule {
  id: number;
  effectiveFrom: string;
  startTime: string;
  endTime: string;
  breakStart: string;
  breakEnd: string;
  graceMinutes?: number | null;
  halfDayThresholdMinutes?: number | null;
  workSiteId?: number | null;
}

interface IForm {
  effectiveFrom: string;
  startTime: string;
  endTime: string;
  breakStart: string;
  breakEnd: string;
  graceMinutes: string;
  halfDayThresholdMinutes: string;
}

const EMPTY: IForm = {
  effectiveFrom: '',
  startTime: '08:00',
  endTime: '17:00',
  breakStart: '12:00',
  breakEnd: '13:00',
  graceMinutes: '5',
  halfDayThresholdMinutes: '90',
};

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

/** "HH:mm" hoặc "HH:mm:ss" thành "HH:mm". */
const hhmm = (t?: string | null) => (t ?? '').slice(0, 5);

/** Lỗi nhập của form bộ giờ (null khi hợp lệ). Giờ so sánh được dạng chuỗi vì luôn đủ hai chữ số. */
export const scheduleFormError = (f: IForm, company: boolean): string | null => {
  if (!vnToIso(f.effectiveFrom)) return 'Nhập ngày hiệu lực dạng dd/MM/yyyy';
  if (![f.startTime, f.endTime, f.breakStart, f.breakEnd].every(t => HHMM.test(t.trim()))) return 'Nhập giờ dạng HH:mm (vd 08:00)';
  if (f.endTime.trim() <= f.startTime.trim()) return 'Giờ ra phải sau giờ vào';
  if (f.breakEnd.trim() <= f.breakStart.trim()) return 'Giờ kết thúc nghỉ trưa phải sau giờ bắt đầu nghỉ trưa';
  if (f.breakStart.trim() < f.startTime.trim() || f.breakEnd.trim() > f.endTime.trim()) return 'Giờ nghỉ trưa phải nằm trong giờ làm';
  if (company) {
    const grace = Number(f.graceMinutes);
    const half = Number(f.halfDayThresholdMinutes);
    if (!/^\d+$/.test(f.graceMinutes.trim()) || !/^\d+$/.test(f.halfDayThresholdMinutes.trim()))
      return 'Nhập số phút muộn cho phép và mốc nửa ngày (số nguyên)';
    if (half <= grace) return 'Mốc nửa ngày phải lớn hơn số phút muộn cho phép';
  }
  return null;
};

interface Props {
  /** Không có: bộ giờ của công ty; có: bộ giờ riêng của khách hàng. */
  siteId?: number;
  /** Quản trị, HR sửa. */
  canEdit: boolean;
}

/**
 * Bộ giờ làm theo ngày hiệu lực: ngày D dùng bộ giờ có ngày hiệu lực gần nhất không sau D. Khách hàng chỉ có giờ vào, giờ ra, nghỉ
 * trưa; phút muộn cho phép và mốc nửa ngày luôn theo công ty.
 */
export const WorkHours = ({ siteId, canEdit }: Props) => {
  const company = siteId === undefined;
  const [rows, setRows] = useState<ISchedule[]>([]);
  const [form, setForm] = useState<IForm>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = () =>
    axios
      .get<ISchedule[]>('api/attendance/schedules', { params: company ? {} : { siteId } })
      .then(r => setRows(Array.isArray(r.data) ? r.data : []))
      .catch(err => setError(errorText(err, 'Không tải được bộ giờ làm')));

  useEffect(() => {
    load();
  }, [siteId]);

  const set = (k: keyof IForm) => (e: React.ChangeEvent<HTMLInputElement>) => setForm(f => ({ ...f, [k]: e.target.value }));

  // Hôm nay theo giờ Việt Nam (yyyy-MM-dd): chỉ bộ giờ chưa tới ngày hiệu lực mới xóa được.
  const [today] = useState(() => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date()));

  const submit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setError(null);
    setNotice(null);
    const invalid = scheduleFormError(form, company);
    if (invalid) {
      setError(invalid);
      return;
    }
    try {
      await axios.post('api/attendance/schedules', {
        effectiveFrom: vnToIso(form.effectiveFrom),
        startTime: form.startTime.trim(),
        endTime: form.endTime.trim(),
        breakStart: form.breakStart.trim(),
        breakEnd: form.breakEnd.trim(),
        graceMinutes: company ? Number(form.graceMinutes) : null,
        halfDayThresholdMinutes: company ? Number(form.halfDayThresholdMinutes) : null,
        workSiteId: company ? null : siteId,
      });
      setNotice(`Đã lưu bộ giờ hiệu lực từ ${form.effectiveFrom.trim()}.`);
      setForm(EMPTY);
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  };

  const remove = async (s: ISchedule) => {
    setError(null);
    setNotice(null);
    try {
      await axios.delete(`api/attendance/schedules/${s.id}`);
      setNotice(`Đã xóa bộ giờ hiệu lực từ ${isoToVn(s.effectiveFrom)}.`);
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  };

  return (
    <section className={company ? 'doit-card' : 'doit-subsection'}>
      <h2 className="h5">{company ? 'Giờ làm' : 'Giờ làm của khách hàng'}</h2>
      <p className="text-muted small">
        {company
          ? 'Mỗi ngày dùng bộ giờ có ngày hiệu lực gần nhất không sau ngày đó. Không đổi được bộ giờ có hiệu lực trong tháng đã chốt công.'
          : 'Chưa có bộ giờ riêng thì dùng giờ của công ty. Phút muộn cho phép và mốc nửa ngày luôn theo công ty.'}
      </p>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      <Table size="sm" responsive>
        <thead>
          <tr>
            <th scope="col">Hiệu lực từ</th>
            <th scope="col">Giờ vào</th>
            <th scope="col">Giờ ra</th>
            <th scope="col">Nghỉ trưa</th>
            {company && <th scope="col">Muộn cho phép</th>}
            {company && <th scope="col">Mốc nửa ngày</th>}
            {canEdit && <th scope="col" />}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && (
            <tr>
              <td colSpan={7}>{company ? 'Chưa có bộ giờ làm' : 'Chưa có bộ giờ riêng, đang dùng giờ của công ty'}</td>
            </tr>
          )}
          {rows.map(s => (
            <tr key={s.id}>
              <td>{isoToVn(s.effectiveFrom)}</td>
              <td>{hhmm(s.startTime)}</td>
              <td>{hhmm(s.endTime)}</td>
              <td>
                {hhmm(s.breakStart)}–{hhmm(s.breakEnd)}
              </td>
              {company && <td>{s.graceMinutes} phút</td>}
              {company && <td>{s.halfDayThresholdMinutes} phút</td>}
              {canEdit && (
                <td className="text-end">
                  {s.effectiveFrom > today && (
                    <Button
                      variant="link"
                      className="text-danger"
                      style={{ minHeight: 44 }}
                      onClick={() => remove(s)}
                      aria-label={`Xóa bộ giờ từ ${isoToVn(s.effectiveFrom)}`}
                    >
                      Xóa
                    </Button>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </Table>
      {canEdit && (
        <Form onSubmit={submit} className="row g-2 align-items-end" noValidate>
          <Form.Group className="col-12 col-md-3" controlId={`hours-from-${siteId ?? 'cty'}`}>
            <Form.Label>Hiệu lực từ (dd/MM/yyyy)</Form.Label>
            <Form.Control value={form.effectiveFrom} onChange={set('effectiveFrom')} />
          </Form.Group>
          <Form.Group className="col-6 col-md-2" controlId={`hours-start-${siteId ?? 'cty'}`}>
            <Form.Label>Giờ vào</Form.Label>
            <Form.Control value={form.startTime} onChange={set('startTime')} placeholder="HH:mm" inputMode="numeric" />
          </Form.Group>
          <Form.Group className="col-6 col-md-2" controlId={`hours-end-${siteId ?? 'cty'}`}>
            <Form.Label>Giờ ra</Form.Label>
            <Form.Control value={form.endTime} onChange={set('endTime')} placeholder="HH:mm" inputMode="numeric" />
          </Form.Group>
          <Form.Group className="col-6 col-md-2" controlId={`hours-break-start-${siteId ?? 'cty'}`}>
            <Form.Label>Nghỉ trưa từ</Form.Label>
            <Form.Control value={form.breakStart} onChange={set('breakStart')} placeholder="HH:mm" inputMode="numeric" />
          </Form.Group>
          <Form.Group className="col-6 col-md-2" controlId={`hours-break-end-${siteId ?? 'cty'}`}>
            <Form.Label>Nghỉ trưa đến</Form.Label>
            <Form.Control value={form.breakEnd} onChange={set('breakEnd')} placeholder="HH:mm" inputMode="numeric" />
          </Form.Group>
          {company && (
            <>
              <Form.Group className="col-6 col-md-3" controlId="hours-grace">
                <Form.Label>Muộn cho phép (phút)</Form.Label>
                <Form.Control value={form.graceMinutes} onChange={set('graceMinutes')} inputMode="numeric" />
              </Form.Group>
              <Form.Group className="col-6 col-md-3" controlId="hours-half-day">
                <Form.Label>Mốc nửa ngày (phút)</Form.Label>
                <Form.Control value={form.halfDayThresholdMinutes} onChange={set('halfDayThresholdMinutes')} inputMode="numeric" />
              </Form.Group>
            </>
          )}
          <div className="col-12 col-md-auto">
            <Button type="submit" style={{ minHeight: 44 }}>
              Lưu bộ giờ
            </Button>
          </div>
        </Form>
      )}
    </section>
  );
};

export default WorkHours;
