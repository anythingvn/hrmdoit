import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn, vnToIso } from 'app/entities/employee/vn-date';

import WorkHours from './work-hours';

const DAYS: [string, string][] = [
  ['MONDAY', 'Thứ Hai'],
  ['TUESDAY', 'Thứ Ba'],
  ['WEDNESDAY', 'Thứ Tư'],
  ['THURSDAY', 'Thứ Năm'],
  ['FRIDAY', 'Thứ Sáu'],
  ['SATURDAY', 'Thứ Bảy'],
  ['SUNDAY', 'Chủ nhật'],
];

interface IHoliday {
  id: number;
  day: string;
  name: string;
}

interface Props {
  /** Chỉ Quản trị sửa ngày làm việc trong tuần. */
  canEditWeek: boolean;
  /** Quản trị, HR sửa ngày lễ. */
  canEditHolidays: boolean;
  /** Quản trị, HR sửa bộ giờ làm (chấm công). */
  canEditHours?: boolean;
  initialYear?: number;
}

/** Lịch làm việc dùng cho nhắc việc: ngày làm việc trong tuần và ngày lễ theo năm. Đổi lịch chỉ ảnh hưởng các lần nhắc sau. */
export const WorkCalendar = ({ canEditWeek, canEditHolidays, canEditHours = false, initialYear }: Props) => {
  const [year, setYear] = useState(() => initialYear ?? new Date().getFullYear());
  const [days, setDays] = useState<string[]>([]);
  const [holidays, setHolidays] = useState<IHoliday[]>([]);
  const [form, setForm] = useState({ day: '', name: '' });
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadHolidays = () =>
    axios
      .get<IHoliday[]>('api/holidays', { params: { year } })
      .then(r => setHolidays(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được ngày lễ')));

  useEffect(() => {
    axios
      .get<{ workingDays: string[] }>('api/work-calendar')
      .then(r => setDays(r.data.workingDays ?? []))
      .catch(err => setError(errorText(err, 'Không tải được lịch làm việc')));
  }, []);

  useEffect(() => {
    loadHolidays();
  }, [year]);

  const run = async (action: () => Promise<unknown>, ok: string) => {
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(ok);
    } catch (err) {
      setError(errorText(err));
    }
  };

  const toggle = (d: string) => setDays(ds => (ds.includes(d) ? ds.filter(x => x !== d) : [...ds, d]));

  const saveWeek = () =>
    run(
      () => axios.put('api/work-calendar', { workingDays: DAYS.map(([d]) => d).filter(d => days.includes(d)) }),
      'Đã lưu ngày làm việc trong tuần.',
    );

  const addHoliday = (ev: React.FormEvent) => {
    ev.preventDefault();
    const iso = vnToIso(form.day);
    if (!iso || !form.name.trim()) {
      setError('Nhập ngày lễ dạng dd/MM/yyyy và tên ngày lễ');
      return;
    }
    run(async () => {
      await axios.post('api/holidays', { day: iso, name: form.name.trim() });
      setForm({ day: '', name: '' });
      await loadHolidays();
    }, 'Đã thêm ngày lễ.');
  };

  const remove = (h: IHoliday) =>
    run(async () => {
      await axios.delete(`api/holidays/${h.id}`);
      await loadHolidays();
    }, `Đã xóa ${h.name}.`);

  const copy = () =>
    run(
      async () => {
        await axios.post('api/holidays/copy', null, { params: { fromYear: year - 1, toYear: year } });
        await loadHolidays();
      },
      `Đã chép ngày lễ từ năm ${year - 1}.`,
    );

  return (
    <div>
      <p className="text-muted">
        Email nhắc việc chỉ gửi vào ngày làm việc; mốc nhắc rơi vào ngày nghỉ được dời về ngày làm việc liền trước. Đổi lịch chỉ ảnh hưởng
        các lần nhắc sau.
      </p>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}

      <WorkHours canEdit={canEditHours} />

      <section className="mb-4">
        <h2 className="h5">Ngày làm việc trong tuần</h2>
        <div className="d-flex flex-wrap gap-3 mb-2">
          {DAYS.map(([d, label]) => (
            <Form.Check
              key={d}
              id={`day-${d}`}
              label={label}
              checked={days.includes(d)}
              disabled={!canEditWeek}
              onChange={() => toggle(d)}
            />
          ))}
        </div>
        {canEditWeek && <Button onClick={saveWeek}>Lưu ngày làm việc</Button>}
      </section>

      <section>
        <div className="d-flex flex-wrap align-items-end gap-2 mb-2">
          <h2 className="h5 mb-0 me-3">Ngày lễ</h2>
          <Form.Group controlId="holiday-year" style={{ maxWidth: 120 }}>
            <Form.Label className="small mb-0">Năm</Form.Label>
            <Form.Control type="number" value={year} onChange={e => setYear(Number(e.target.value))} />
          </Form.Group>
          {canEditHolidays && (
            <Button variant="outline-secondary" onClick={copy}>
              Chép từ năm {year - 1}
            </Button>
          )}
        </div>
        <Table size="sm" responsive>
          <thead>
            <tr>
              <th scope="col">Ngày</th>
              <th scope="col">Tên</th>
              {canEditHolidays && <th scope="col" />}
            </tr>
          </thead>
          <tbody>
            {holidays.length === 0 && (
              <tr>
                <td colSpan={3}>Chưa khai báo ngày lễ cho năm {year}</td>
              </tr>
            )}
            {holidays.map(h => (
              <tr key={h.id}>
                <td>{isoToVn(h.day)}</td>
                <td>{h.name}</td>
                {canEditHolidays && (
                  <td className="text-end">
                    <Button variant="link" className="text-danger" onClick={() => remove(h)} aria-label={`Xóa ${h.name}`}>
                      Xóa
                    </Button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </Table>
        {canEditHolidays && (
          <Form onSubmit={addHoliday} className="row g-2 align-items-end">
            <Form.Group className="col-12 col-md-3" controlId="holiday-day">
              <Form.Label>Ngày lễ (dd/MM/yyyy)</Form.Label>
              <Form.Control value={form.day} onChange={e => setForm(f => ({ ...f, day: e.target.value }))} />
            </Form.Group>
            <Form.Group className="col-12 col-md-5" controlId="holiday-name">
              <Form.Label>Tên ngày lễ</Form.Label>
              <Form.Control value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
            </Form.Group>
            <div className="col-12 col-md-auto">
              <Button type="submit">Thêm ngày lễ</Button>
            </div>
          </Form>
        )}
      </section>
    </div>
  );
};

export default WorkCalendar;
