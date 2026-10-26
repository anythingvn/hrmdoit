import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn, vnToIso } from 'app/entities/employee/vn-date';
import { PageHeader, SectionCard, StatusChip } from 'app/shared/ui';

import { AUTO_LABEL, AutoKind, IDayBoard, IDayEntry, IDayGroup, IDayView, PAID_LEAVE, attendanceApi, isAutoFilled } from './attendance-api';
import { HHMM, ICreditResult, IRules, computeCredit } from './credit';
import PunchList from './punch-list';

interface IEdit {
  in: string;
  out: string;
  absent: boolean;
}

const TOUCH = { minHeight: 44 };

const localIso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const shiftIso = (iso: string, days: number) => {
  const [y, m, d] = iso.split('-').map(Number);
  return localIso(new Date(y, m - 1, d + days));
};

const fmt = (n: number) => String(n).replace('.', ',');

/** Ô nhập chỉ chứa giờ gõ tay (GO_TAY): giờ tự chấm đã tính hiện ở danh sách nguồn, không được chép thành lần gõ tay. */
const editOf = (v: IDayView): IEdit => ({ in: v.manualIn ?? '', out: v.manualOut ?? '', absent: !!v.markedAbsent });

/** Giờ của các lần tự chấm, giải trình đang được tính (không kể lần gõ tay), để xem trước công khi sửa ô nhập. */
const countedTimes = (v: IDayView, kind: 'VAO' | 'RA'): string[] =>
  (v.punches ?? [])
    .filter(
      p => p.kind === kind && p.source !== 'GO_TAY' && (p.source === 'GIAI_TRINH' || p.status === 'CHAP_NHAN' || p.status === 'DA_DUYET'),
    )
    .map(p => p.time);

/** Giờ vào sớm nhất / giờ ra muộn nhất giữa ô nhập (nếu đúng dạng) và các lần đã tính. */
const effective = (typed: string, others: string[], earliest: boolean): string => {
  const all = [...(HHMM.test(typed.trim()) ? [typed.trim()] : []), ...others].sort();
  if (all.length === 0) return '';
  return earliest ? all[0] : all[all.length - 1];
};

const timeOk = (t: string) => t.trim() === '' || HHMM.test(t.trim());

/** Ghi chú của một dòng: Đúng giờ / Muộn X' / → nửa công / Thiếu giờ / loại nghỉ nửa ngày. */
export const noteFor = (r: ICreditResult, v: IDayView, absent: boolean): string => {
  const leave = v.leavePart && v.autoKind ? `${AUTO_LABEL[v.autoKind]} buổi ${v.leavePart === 'SANG' ? 'sáng' : 'chiều'}` : '';
  const join = (s: string) => [leave, s].filter(Boolean).join(' · ');
  if (absent) return join('Vắng');
  if (r.status === 'THIEU_GIO') return join('Thiếu giờ');
  if (r.status === 'CHUA_NHAP') return join(v.status === 'CHUA_NHAP' ? 'Chưa nhập' : '');
  if (r.status === 'VANG') return join('Vắng (làm chưa đủ nửa ngày)');
  const parts: string[] = [];
  if (r.lateMinutes > 0) parts.push(`Muộn ${r.lateMinutes}'`);
  if (r.earlyMinutes > 0) parts.push(`Về sớm ${r.earlyMinutes}'`);
  if (parts.length === 0) return join('Đúng giờ');
  return join(parts.join(', ') + (r.credit === 0.5 && !v.leavePart ? ' → nửa công' : ''));
};

/**
 * "Chấm công" — gõ giờ theo ngày (bản phác A): khối "Văn phòng" (giờ công ty) và từng khối "Onsite · khách hàng" (giờ khách). Mỗi dòng
 * có ô Vào, ô Ra, công tính lại ngay (giống lõi, theo bộ giờ server trả về), ghi chú và nút Vắng; ô tự điền (nghỉ, lễ, công tác) không có
 * ô nhập. Người được gõ do server quyết định (trưởng nhóm, trưởng nhóm onsite, HR, Quản trị).
 */
export const DayEntry = () => {
  const [date, setDate] = useState<string>(() => localIso(new Date()));
  const [dateText, setDateText] = useState<string>(() => isoToVn(localIso(new Date())));
  const [board, setBoard] = useState<IDayBoard | null>(null);
  const [edits, setEdits] = useState<Record<number, IEdit>>({});
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const apply = (b: IDayBoard) => {
    setBoard(b);
    const e: Record<number, IEdit> = {};
    b.groups.forEach(g => g.rows.forEach(v => (e[v.employeeId] = editOf(v))));
    setEdits(e);
  };

  useEffect(() => {
    setError(null);
    setNotice(null);
    attendanceApi
      .day(date)
      .then(apply)
      .catch(err => {
        setBoard(null);
        setError(errorText(err, 'Không tải được danh sách chấm công'));
      });
  }, [date]);

  const go = (iso: string) => {
    setDate(iso);
    setDateText(isoToVn(iso));
  };

  const onDateText = (text: string) => {
    setDateText(text);
    const iso = vnToIso(text);
    if (iso) setDate(iso);
  };

  const rows = board ? board.groups.flatMap(g => g.rows) : [];
  const dirty = rows.filter(v => {
    const e = edits[v.employeeId];
    if (!e || isAutoFilled(v)) return false;
    const o = editOf(v);
    return e.in.trim() !== o.in || e.out.trim() !== o.out || e.absent !== o.absent;
  });

  const pendingTotal = rows.reduce((n, v) => n + (v.pendingCount ?? 0), 0);

  /** Duyệt, từ chối trả lại kết quả của ngày đó: thay dòng, giữ nguyên giờ đang gõ dở ở ô nhập. */
  const replaceRow = (nv: IDayView) =>
    setBoard(
      prev =>
        prev && { ...prev, groups: prev.groups.map(g => ({ ...g, rows: g.rows.map(v => (v.employeeId === nv.employeeId ? nv : v)) })) },
    );
  const approvePunch = (id: number) => attendanceApi.approvePunch(id).then(replaceRow);
  const rejectPunch = (id: number, note: string) => attendanceApi.rejectPunch(id, note).then(replaceRow);

  const setEdit = (id: number, patch: Partial<IEdit>) => setEdits(prev => ({ ...prev, [id]: { ...prev[id], ...patch } }));

  const save = () => {
    if (!board) return;
    setError(null);
    setNotice(null);
    if (dirty.some(v => !timeOk(edits[v.employeeId].in) || !timeOk(edits[v.employeeId].out))) {
      setError('Nhập giờ dạng HH:mm (vd 08:00)');
      return;
    }
    const entries: IDayEntry[] = dirty.map(v => {
      const e = edits[v.employeeId];
      return {
        employeeId: v.employeeId,
        in: e.absent ? null : e.in.trim() || null,
        out: e.absent ? null : e.out.trim() || null,
        absent: e.absent,
      };
    });
    setSaving(true);
    attendanceApi
      .saveDay(date, entries)
      .then(saved => {
        const byId = new Map(saved.map(v => [v.employeeId, v]));
        const next: IDayBoard = {
          ...board,
          groups: board.groups.map(g => ({ ...g, rows: g.rows.map(v => byId.get(v.employeeId) ?? v) })),
        };
        setBoard(next);
        setEdits(prev => {
          const out = { ...prev };
          saved.forEach(v => (out[v.employeeId] = editOf(v)));
          return out;
        });
        setNotice(`Đã lưu ${saved.length} người`);
      })
      .catch(err => setError(errorText(err)))
      .finally(() => setSaving(false));
  };

  const renderRow = (g: IDayGroup, v: IDayView, rules: IRules) => {
    if (isAutoFilled(v)) {
      const label = AUTO_LABEL[v.autoKind as AutoKind];
      return (
        <tr key={v.employeeId} data-testid={`row-${v.employeeId}`}>
          <td>{v.code}</td>
          <td>{v.name}</td>
          <td colSpan={2} className="text-muted">
            {label}
          </td>
          <td>{fmt(v.actualCredit)}</td>
          <td>{label}</td>
          <td />
        </tr>
      );
    }
    const e = edits[v.employeeId] ?? editOf(v);
    const inOk = timeOk(e.in);
    const outOk = timeOk(e.out);
    const effIn = e.absent ? '' : effective(e.in, countedTimes(v, 'VAO'), true);
    const effOut = e.absent ? '' : effective(e.out, countedTimes(v, 'RA'), false);
    const r = computeCredit(inOk ? effIn || null : null, outOk ? effOut || null : null, g.hours, rules, v.leavePart, e.absent);
    const leaveShare = v.leavePart && v.autoKind && PAID_LEAVE.includes(v.autoKind) ? 0.5 : 0;
    const credit = r.credit === null ? (leaveShare ? fmt(leaveShare) : '–') : fmt(r.credit + leaveShare);
    const hasIn = effIn !== '';
    const hasOut = effOut !== '';
    const missingAll = !e.absent && !hasIn && !hasOut && v.status === 'CHUA_NHAP';
    const inRed = !inOk || (!e.absent && !hasIn && hasOut) || missingAll;
    const outRed = !outOk || (!e.absent && hasIn && !hasOut) || missingAll;
    return (
      <tr key={v.employeeId} data-testid={`row-${v.employeeId}`} className={(v.pendingCount ?? 0) > 0 ? 'table-warning' : undefined}>
        <td>{v.code}</td>
        <td>
          {v.name} {v.remote && <StatusChip tone="info">Từ xa</StatusChip>}
        </td>
        <td>
          <Form.Control
            aria-label={`Giờ vào ${v.name}`}
            placeholder="HH:mm"
            value={e.in}
            disabled={e.absent}
            isInvalid={inRed}
            style={{ ...TOUCH, maxWidth: 100 }}
            onChange={ev => setEdit(v.employeeId, { in: ev.target.value })}
          />
        </td>
        <td>
          <Form.Control
            aria-label={`Giờ ra ${v.name}`}
            placeholder="HH:mm"
            value={e.out}
            disabled={e.absent}
            isInvalid={outRed}
            style={{ ...TOUCH, maxWidth: 100 }}
            onChange={ev => setEdit(v.employeeId, { out: ev.target.value })}
          />
        </td>
        <td>{credit}</td>
        <td className={r.status === 'THIEU_GIO' || missingAll ? 'text-danger' : undefined}>
          {noteFor(r, v, e.absent)}
          <PunchList punches={v.punches} canReview approve={approvePunch} reject={rejectPunch} />
        </td>
        <td>
          <Button
            variant={e.absent ? 'danger' : 'outline-secondary'}
            style={TOUCH}
            aria-pressed={e.absent}
            onClick={() => setEdit(v.employeeId, e.absent ? { absent: false } : { absent: true, in: '', out: '' })}
          >
            Vắng
          </Button>
        </td>
      </tr>
    );
  };

  return (
    <div className="doit-page">
      <PageHeader title="Chấm công theo ngày" />
      <div className="d-flex flex-wrap gap-2 align-items-end">
        <Button variant="outline-secondary" style={TOUCH} onClick={() => go(shiftIso(date, -1))}>
          ‹ Hôm trước
        </Button>
        <Form.Group>
          <Form.Label htmlFor="cham-cong-ngay">Ngày (dd/MM/yyyy)</Form.Label>
          <Form.Control
            id="cham-cong-ngay"
            value={dateText}
            style={{ ...TOUCH, maxWidth: 160 }}
            onChange={ev => onDateText(ev.target.value)}
          />
        </Form.Group>
        <Button variant="outline-secondary" style={TOUCH} onClick={() => go(shiftIso(date, 1))}>
          Hôm sau ›
        </Button>
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      {pendingTotal > 0 && (
        <Alert variant="warning" data-testid="pending-count">
          {pendingTotal} lần chấm chờ duyệt
        </Alert>
      )}
      {board && board.groups.length === 0 && <p className="text-muted mb-0">Không có ai để chấm công ngày {isoToVn(board.date)}.</p>}
      {board?.groups.map(g => (
        <SectionCard
          key={g.group}
          title={`${g.label} (${g.hours.start}–${g.hours.end}, nghỉ trưa ${g.hours.breakStart}–${g.hours.breakEnd})`}
        >
          <Table responsive size="sm" className="align-middle">
            <thead>
              <tr>
                <th>Mã</th>
                <th>Họ tên</th>
                <th>Vào</th>
                <th>Ra</th>
                <th>Công</th>
                <th>Ghi chú</th>
                <th />
              </tr>
            </thead>
            <tbody>{g.rows.map(v => renderRow(g, v, board))}</tbody>
          </Table>
        </SectionCard>
      ))}
      {board && board.groups.length > 0 && (
        <Button variant="primary" style={TOUCH} className="align-self-start" disabled={saving || dirty.length === 0} onClick={save}>
          Lưu
        </Button>
      )}
    </div>
  );
};

export default DayEntry;
