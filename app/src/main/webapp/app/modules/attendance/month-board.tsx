import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Badge, Button, Form, Modal, Table } from 'react-bootstrap';

import { errorText, useRoles } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';
import { downloadFile } from 'app/shared/util/download';

import {
  IBoardRow,
  IDayView,
  IMonthBoard,
  IUnresolved,
  attendanceApi,
  cellShort,
  cellTitle,
  isAutoFilled,
  isRedCell,
  monthVn,
  previousMonth,
  shiftMonth,
  todayIso,
} from './attendance-api';
import { HHMM } from './credit';
import PunchList from './punch-list';

const TOUCH = { minHeight: 44 };
const WEEKDAY = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

const fmt = (n: number | null | undefined) => (n === null || n === undefined ? '' : String(n).replace('.', ','));

const weekday = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return WEEKDAY[new Date(y, m - 1, d).getDay()];
};

const ddmm = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

interface IEditing {
  row: IBoardRow;
  day: IDayView;
  in: string;
  out: string;
  absent: boolean;
}

/** Lựa chọn lọc (giá trị, nhãn) lấy từ các dòng của bảng. */
const options = (pairs: [string, string][]) =>
  Array.from(new Map(pairs).entries())
    .map(([value, label]) => ({ value, label }))
    .sort((a, b) => a.label.localeCompare(b.label, 'vi'));

/**
 * "Bảng công tháng" (bản phác B): mỗi người một dòng, mỗi ngày một ô (giờ vào–ra hoặc nhãn tự điền), các cột tổng. Lọc theo phòng,
 * nhóm, khách hàng. Ô đỏ: thiếu giờ, chưa nhập; dấu "Đổi sau chốt" cho dòng có số tính lại khác ảnh chụp. Bấm ô để sửa giờ (tháng
 * đang mở) khi ô thuộc {@code editableDays} của dòng (máy chủ tính theo quyền gõ: HR, Quản trị, trưởng nhóm, trưởng nhóm onsite),
 * không phải ngày chưa tới và không phải dòng của chính mình. HR, Quản trị: Chốt (còn ô đỏ thì hiện danh sách và nút "Vẫn chốt"), Mở
 * lại chốt (cần lý do). Mọi người xem được tải Excel.
 */
export const MonthBoard = () => {
  const { canManage } = useRoles();
  const [month, setMonth] = useState<string>(() => previousMonth());
  const [board, setBoard] = useState<IMonthBoard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [unresolved, setUnresolved] = useState<IUnresolved[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [dept, setDept] = useState('');
  const [team, setTeam] = useState('');
  const [site, setSite] = useState('');
  const [editing, setEditing] = useState<IEditing | null>(null);
  const [editError, setEditError] = useState<string | null>(null);
  const [reopening, setReopening] = useState(false);
  const [reason, setReason] = useState('');

  const load = (m: string) =>
    attendanceApi
      .month(m)
      .then(b => setBoard(b))
      .catch(err => {
        setBoard(null);
        setError(errorText(err, 'Không tải được bảng công tháng'));
      });

  useEffect(() => {
    setError(null);
    setNotice(null);
    setUnresolved(null);
    load(month);
  }, [month]);

  const rows = board?.rows ?? [];
  const deptOptions = useMemo(
    () => options(rows.filter(r => r.employee.departmentId).map(r => [String(r.employee.departmentId), r.employee.departmentName ?? ''])),
    [board],
  );
  const teamOptions = useMemo(
    () => options(rows.filter(r => r.employee.managerId).map(r => [String(r.employee.managerId), r.employee.managerName ?? ''])),
    [board],
  );
  const siteOptions = useMemo(
    () => options(rows.flatMap(r => r.days.filter(d => d.group.startsWith('SITE:')).map(d => [d.group, d.groupLabel] as [string, string]))),
    [board],
  );
  const shown = rows.filter(
    r =>
      (!dept || String(r.employee.departmentId) === dept) &&
      (!team || String(r.employee.managerId) === team) &&
      (!site || r.days.some(d => d.group === site)),
  );
  const days = rows.length > 0 ? rows[0].days.map(d => d.day) : [];
  const open = board?.status === 'DANG_MO';

  const close = (force: boolean) => {
    setError(null);
    setNotice(null);
    setBusy(true);
    attendanceApi
      .close(month, force)
      .then(() => {
        setUnresolved(null);
        setNotice(`Đã chốt bảng công tháng ${monthVn(month)}`);
        return load(month);
      })
      .catch(err => {
        const data = err?.response?.data;
        if (data?.message === 'error.attendanceunresolved' && Array.isArray(data.unresolved)) {
          setUnresolved(data.unresolved);
        } else {
          setError(errorText(err, 'Không chốt được bảng công'));
        }
      })
      .finally(() => setBusy(false));
  };

  const reopen = () => {
    setError(null);
    setNotice(null);
    setBusy(true);
    attendanceApi
      .reopen(month, reason.trim())
      .then(() => {
        setReopening(false);
        setReason('');
        setNotice(`Đã mở lại bảng công tháng ${monthVn(month)}`);
        return load(month);
      })
      .catch(err => {
        setReopening(false);
        setError(errorText(err, 'Không mở lại được bảng công'));
      })
      .finally(() => setBusy(false));
  };

  const download = () => {
    setError(null);
    downloadFile(`api/attendance/month/${month}/export.xlsx`, `bang-cong-${month}.xlsx`).catch(err =>
      setError(errorText(err, 'Không tải được file Excel')),
    );
  };

  const startEdit = (row: IBoardRow, day: IDayView) => {
    setEditError(null);
    setEditing({ row, day, in: day.manualIn ?? '', out: day.manualOut ?? '', absent: !!day.markedAbsent });
  };

  const saveEdit = () => {
    if (!editing) return;
    const inT = editing.in.trim();
    const outT = editing.out.trim();
    if ((inT && !HHMM.test(inT)) || (outT && !HHMM.test(outT))) {
      setEditError('Nhập giờ dạng HH:mm (vd 08:00)');
      return;
    }
    setBusy(true);
    attendanceApi
      .saveDay(editing.day.day, [
        {
          employeeId: editing.row.employee.id,
          in: editing.absent ? null : inT || null,
          out: editing.absent ? null : outT || null,
          absent: editing.absent,
        },
      ])
      .then(() => {
        setEditing(null);
        return load(month);
      })
      .catch(err => setEditError(errorText(err)))
      .finally(() => setBusy(false));
  };

  const today = todayIso();
  const canEditCell = (row: IBoardRow, v: IDayView) =>
    open && !row.self && v.day <= today && !isAutoFilled(v) && (row.editableDays ? row.editableDays.includes(v.day) : canManage);

  const renderCell = (row: IBoardRow, v: IDayView) => {
    const pending = (v.pendingCount ?? 0) > 0;
    // Ô có lần chờ duyệt tô vàng (việc cần làm trước), không tô đỏ dù chưa đủ giờ.
    const red = isRedCell(v) && !pending;
    const editable = canEditCell(row, v);
    const text = cellShort(v);
    return (
      <td
        key={v.day}
        data-testid={`cell-${row.employee.id}-${v.day}`}
        title={cellTitle(v)}
        className={`text-nowrap small${red ? ' table-danger text-danger' : ''}${pending ? ' table-warning' : ''}${isAutoFilled(v) && !text ? ' table-light' : ''}`}
        style={{ padding: 0 }}
      >
        {editable ? (
          <button
            type="button"
            className="btn btn-link btn-sm text-reset text-decoration-none w-100"
            style={{ ...TOUCH, fontSize: '0.75rem', padding: '0 4px' }}
            aria-label={`Sửa giờ ${row.employee.name} ngày ${isoToVn(v.day)}`}
            onClick={() => startEdit(row, v)}
          >
            {text || '–'}
          </button>
        ) : (
          <span className="d-inline-block px-1" style={{ fontSize: '0.75rem' }}>
            {text}
          </span>
        )}
      </td>
    );
  };

  return (
    <div>
      <h2>Bảng công tháng</h2>
      <div className="d-flex flex-wrap gap-2 align-items-center mb-3">
        <Button variant="outline-secondary" style={TOUCH} onClick={() => setMonth(shiftMonth(month, -1))}>
          ‹ Tháng trước
        </Button>
        <strong>Tháng {monthVn(month)}</strong>
        <Button variant="outline-secondary" style={TOUCH} onClick={() => setMonth(shiftMonth(month, 1))}>
          Tháng sau ›
        </Button>
        {board && (
          <Badge bg={open ? 'secondary' : 'success'} data-testid="month-status">
            {open ? 'Đang mở' : 'Đã chốt'}
          </Badge>
        )}
        <div className="ms-auto d-flex flex-wrap gap-2">
          {board && canManage && open && (
            <Button variant="primary" style={TOUCH} disabled={busy} onClick={() => close(false)}>
              Chốt bảng công
            </Button>
          )}
          {board && canManage && !open && (
            <Button variant="outline-danger" style={TOUCH} disabled={busy} onClick={() => setReopening(true)}>
              Mở lại chốt
            </Button>
          )}
          {board && (
            <Button variant="outline-secondary" style={TOUCH} onClick={download}>
              Tải Excel
            </Button>
          )}
        </div>
      </div>

      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      {unresolved && (
        <Alert variant="warning" data-testid="unresolved">
          <p className="mb-2">Còn ô thiếu giờ / chưa nhập. Nếu vẫn chốt, các ô này tính 0 công. Sửa trước hoặc bấm &quot;Vẫn chốt&quot;.</p>
          <ul className="mb-2">
            {unresolved.map(u => (
              <li key={u.employeeId}>
                {u.code} {u.name}: {u.dates.map(ddmm).join(', ')}
              </li>
            ))}
          </ul>
          <div className="d-flex gap-2">
            <Button variant="warning" style={TOUCH} disabled={busy} onClick={() => close(true)}>
              Vẫn chốt
            </Button>
            <Button variant="outline-secondary" style={TOUCH} onClick={() => setUnresolved(null)}>
              Để sau
            </Button>
          </div>
        </Alert>
      )}

      {board && (
        <div className="d-flex flex-wrap gap-2 mb-3">
          <Form.Select aria-label="Lọc theo phòng" style={{ ...TOUCH, maxWidth: 240 }} value={dept} onChange={e => setDept(e.target.value)}>
            <option value="">Tất cả phòng</option>
            {deptOptions.map(o => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Form.Select>
          <Form.Select aria-label="Lọc theo nhóm" style={{ ...TOUCH, maxWidth: 240 }} value={team} onChange={e => setTeam(e.target.value)}>
            <option value="">Tất cả nhóm</option>
            {teamOptions.map(o => (
              <option key={o.value} value={o.value}>
                Nhóm {o.label}
              </option>
            ))}
          </Form.Select>
          <Form.Select
            aria-label="Lọc theo khách hàng"
            style={{ ...TOUCH, maxWidth: 260 }}
            value={site}
            onChange={e => setSite(e.target.value)}
          >
            <option value="">Tất cả nơi làm</option>
            {siteOptions.map(o => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Form.Select>
        </div>
      )}

      {board && shown.length === 0 && <p className="text-muted">Không có ai trong bảng công tháng {monthVn(board.month)}.</p>}
      {board && shown.length > 0 && (
        <Table responsive bordered size="sm" className="align-middle">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Họ tên</th>
              {days.map(d => (
                <th key={d} className="text-center small">
                  {Number(d.slice(8, 10))}
                  <br />
                  <span className="text-muted">{weekday(d)}</span>
                </th>
              ))}
              <th title="Số ngày công chuẩn trong tháng">Chuẩn</th>
              <th title="Ngày công thực tế trong tháng">Thực tế</th>
              <th title="Số lần / số phút đi muộn">Muộn</th>
              <th>Nửa công</th>
              <th>Vắng</th>
              <th title="Ô thiếu giờ / chưa nhập">Thiếu</th>
            </tr>
          </thead>
          <tbody>
            {shown.map(r => (
              <tr key={r.employee.id} data-testid={`board-row-${r.employee.id}`}>
                <td>{r.employee.code}</td>
                <td className="text-nowrap">
                  {r.employee.name}{' '}
                  {r.changedAfterClose && (
                    <Badge bg="warning" text="dark" title="Số tính lại khác ảnh chụp lúc chốt">
                      Đổi sau chốt
                    </Badge>
                  )}
                </td>
                {r.days.map(v => renderCell(r, v))}
                <td>{fmt(r.totals.standardDays)}</td>
                <td>
                  <strong>{fmt(r.totals.actualDays)}</strong>
                </td>
                <td className="text-nowrap">{r.totals.lateCount > 0 ? `${r.totals.lateCount} / ${r.totals.lateMinutes}'` : ''}</td>
                <td>{r.totals.halfDays || ''}</td>
                <td>{r.totals.absentDays || ''}</td>
                <td className={r.totals.missingDays > 0 ? 'text-danger' : undefined}>{r.totals.missingDays || ''}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <Modal show={!!editing} onHide={() => setEditing(null)}>
        <Modal.Header closeButton>
          <Modal.Title>
            {editing?.row.employee.name} · {editing && isoToVn(editing.day.day)}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {editError && <Alert variant="danger">{editError}</Alert>}
          {editing?.day.leavePart && <p className="text-muted">{cellTitle(editing.day)}</p>}
          {editing && (
            <div className="mb-3">
              <PunchList
                punches={editing.day.punches}
                canReview
                approve={id =>
                  attendanceApi.approvePunch(id).then(() => {
                    setEditing(null);
                    return load(month);
                  })
                }
                reject={(id, note) =>
                  attendanceApi.rejectPunch(id, note).then(() => {
                    setEditing(null);
                    return load(month);
                  })
                }
              />
            </div>
          )}
          <div className="d-flex gap-2">
            <Form.Group>
              <Form.Label htmlFor="bang-cong-vao">Vào</Form.Label>
              <Form.Control
                id="bang-cong-vao"
                placeholder="HH:mm"
                style={{ ...TOUCH, maxWidth: 120 }}
                value={editing?.in ?? ''}
                disabled={editing?.absent}
                onChange={e => editing && setEditing({ ...editing, in: e.target.value })}
              />
            </Form.Group>
            <Form.Group>
              <Form.Label htmlFor="bang-cong-ra">Ra</Form.Label>
              <Form.Control
                id="bang-cong-ra"
                placeholder="HH:mm"
                style={{ ...TOUCH, maxWidth: 120 }}
                value={editing?.out ?? ''}
                disabled={editing?.absent}
                onChange={e => editing && setEditing({ ...editing, out: e.target.value })}
              />
            </Form.Group>
          </div>
          <Form.Check
            className="mt-3"
            id="bang-cong-vang"
            label="Vắng"
            checked={!!editing?.absent}
            onChange={e => editing && setEditing({ ...editing, absent: e.target.checked, in: '', out: '' })}
          />
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" style={TOUCH} onClick={() => setEditing(null)}>
            Hủy
          </Button>
          <Button variant="primary" style={TOUCH} disabled={busy} onClick={saveEdit}>
            Lưu
          </Button>
        </Modal.Footer>
      </Modal>

      <Modal show={reopening} onHide={() => setReopening(false)}>
        <Modal.Header closeButton>
          <Modal.Title>Mở lại bảng công tháng {monthVn(month)}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <Form.Group>
            <Form.Label htmlFor="bang-cong-ly-do">Lý do mở lại</Form.Label>
            <Form.Control
              id="bang-cong-ly-do"
              as="textarea"
              rows={3}
              maxLength={500}
              value={reason}
              onChange={e => setReason(e.target.value)}
            />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" style={TOUCH} onClick={() => setReopening(false)}>
            Hủy
          </Button>
          <Button variant="danger" style={TOUCH} disabled={busy || reason.trim() === ''} onClick={reopen}>
            Mở lại
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
};

export default MonthBoard;
