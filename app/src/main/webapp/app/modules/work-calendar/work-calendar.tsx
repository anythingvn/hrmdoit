import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn, vnToIso } from 'app/entities/employee/vn-date';
import { MAX_UPLOAD_BYTES, downloadFile } from 'app/shared/util/download';

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

interface IImportPreview {
  years: number[];
  added: { day: string; name: string }[];
  renamed: { day: string; oldName: string; name: string }[];
  removed: { day: string; name: string }[];
  unchanged: { day: string; name: string }[];
  errors: { row: number; key?: string; message: string }[];
}

const TOUCH = { minHeight: 44 };

interface Props {
  /** Chỉ Quản trị sửa ngày làm việc trong tuần. */
  canEditWeek: boolean;
  /** Quản trị, HR sửa ngày lễ. */
  canEditHolidays: boolean;
  /** Quản trị, HR sửa bộ giờ làm (chấm công). */
  canEditHours?: boolean;
  initialYear?: number;
}

/** Một nhóm trong bảng xem trước nhập Excel (thêm mới, đổi tên, sẽ xóa, không đổi). */
const PreviewGroup = ({ title, rows, collapsed = false }: { title: string; rows: string[][]; collapsed?: boolean }) => (
  <div className="mb-2">
    <h3 className="h6 mb-1">{title}</h3>
    {!collapsed && rows.length > 0 && (
      <Table size="sm" className="mb-1">
        <tbody>
          {rows.map(([day, text]) => (
            <tr key={day}>
              <td style={{ width: 120 }}>{isoToVn(day)}</td>
              <td>{text}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    )}
  </div>
);

/** Lịch làm việc dùng cho nhắc việc: ngày làm việc trong tuần và ngày lễ theo năm. Đổi lịch chỉ ảnh hưởng các lần nhắc sau. */
export const WorkCalendar = ({ canEditWeek, canEditHolidays, canEditHours = false, initialYear }: Props) => {
  const [year, setYear] = useState(() => initialYear ?? new Date().getFullYear());
  const [days, setDays] = useState<string[]>([]);
  const [holidays, setHolidays] = useState<IHoliday[]>([]);
  const [form, setForm] = useState({ day: '', name: '' });
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importPreview, setImportPreview] = useState<IImportPreview | null>(null);

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

  const exportExcel = () =>
    downloadFile('api/holidays/export.xlsx', `ngay-le-${year}.xlsx`, { year }).catch(err =>
      setError(errorText(err, 'Không tải được file')),
    );

  const body = (f: File) => {
    const data = new FormData();
    data.append('file', f);
    return data;
  };

  const onPickFile = async (f: File | null) => {
    setError(null);
    setNotice(null);
    setImportFile(null);
    setImportPreview(null);
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.xlsx')) {
      setError('Chỉ nhận file Excel .xlsx');
      return;
    }
    if (f.size > MAX_UPLOAD_BYTES) {
      setError('File vượt quá dung lượng cho phép (20 MB)');
      return;
    }
    try {
      const res = await axios.post<IImportPreview>('api/holidays/import/preview', body(f));
      setImportFile(f);
      setImportPreview(res.data);
    } catch (err) {
      setError(errorText(err, 'Không đọc được file'));
    }
  };

  const applyImport = () => {
    if (!importFile) return;
    const f = importFile;
    return run(async () => {
      await axios.post('api/holidays/import/apply', body(f));
      setImportFile(null);
      setImportPreview(null);
      await loadHolidays();
    }, 'Đã áp dụng ngày lễ từ file Excel.');
  };

  const changeCount = importPreview ? importPreview.added.length + importPreview.renamed.length + importPreview.removed.length : 0;

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
            <Button variant="outline-secondary" style={TOUCH} onClick={copy}>
              Chép từ năm {year - 1}
            </Button>
          )}
          <Button variant="outline-secondary" style={TOUCH} onClick={() => void exportExcel()}>
            Xuất Excel
          </Button>
        </div>
        {canEditHolidays && (
          <div className="mb-3">
            <Form.Group controlId="holiday-import-file">
              <Form.Label>Nhập từ Excel (cột A: Ngày dd/MM/yyyy, cột B: Tên ngày lễ)</Form.Label>
              <Form.Control
                type="file"
                accept=".xlsx"
                aria-label="Chọn file Excel ngày lễ"
                style={TOUCH}
                onChange={e => {
                  const input = e.target as HTMLInputElement;
                  const picked = input.files?.[0] ?? null;
                  // Xóa giá trị để chọn lại đúng file đó (sau khi sửa file) vẫn đọc lại.
                  input.value = '';
                  void onPickFile(picked);
                }}
              />
            </Form.Group>
            {importPreview && (
              <div className="mt-2">
                <p className="mb-1">
                  Năm trong file: {importPreview.years.join(', ')}. Chỉ ngày lễ của các năm này bị đổi; ngày không có trong file sẽ bị xóa.
                </p>
                {importPreview.errors.length > 0 && (
                  <Alert variant="danger">
                    <ul className="mb-0">
                      {importPreview.errors.map((e, i) => (
                        <li key={`${e.row}-${i}`}>{e.row > 0 ? `Dòng ${e.row}: ${e.message}` : e.message}</li>
                      ))}
                    </ul>
                  </Alert>
                )}
                <PreviewGroup title={`Thêm mới (${importPreview.added.length})`} rows={importPreview.added.map(r => [r.day, r.name])} />
                <PreviewGroup
                  title={`Đổi tên (${importPreview.renamed.length})`}
                  rows={importPreview.renamed.map(r => [r.day, `${r.oldName} → ${r.name}`])}
                />
                <PreviewGroup title={`Sẽ xóa (${importPreview.removed.length})`} rows={importPreview.removed.map(r => [r.day, r.name])} />
                <PreviewGroup
                  title={`Không đổi (${importPreview.unchanged.length})`}
                  rows={importPreview.unchanged.map(r => [r.day, r.name])}
                  collapsed
                />
                <Button style={TOUCH} disabled={importPreview.errors.length > 0 || changeCount === 0} onClick={() => void applyImport()}>
                  Áp dụng
                </Button>
              </div>
            )}
          </div>
        )}
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
