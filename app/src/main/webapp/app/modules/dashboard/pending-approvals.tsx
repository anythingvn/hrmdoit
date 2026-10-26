import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Modal } from 'react-bootstrap';
import { Link } from 'react-router';

import { errorText, initials } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';
import { IPendingItem, attendanceApi } from 'app/modules/attendance/attendance-api';
import { ILeaveRequest, IOvertime, leaveApi, leaveErrorText } from 'app/modules/leave/leave-api';
import { LEAVE_TYPE_LABEL, num } from 'app/modules/leave/leave-labels';
import { rangeText } from 'app/modules/leave/status-chip';
import { SectionCard } from 'app/shared/ui';

/** Số dòng tối đa trên Tổng quan; còn lại xem ở "Duyệt đơn". */
const MAX_ROWS = 5;

/**
 * Một dòng đơn chờ duyệt. {@code approve}/{@code reject} chỉ có khi duyệt được bằng một nút (đơn nghỉ chờ duyệt, lần chấm, giải trình);
 * đơn xin hủy và làm thêm giờ (phải chọn Trả tiền / Nghỉ bù) xử lý ở trang "Duyệt đơn".
 */
interface Row {
  key: string;
  name: string;
  detail: string;
  approve?: () => Promise<unknown>;
  reject?: (note: string) => Promise<unknown>;
  errorOf: (err: unknown) => string;
}

const leaveRow = (r: ILeaveRequest): Row => {
  const what = `${LEAVE_TYPE_LABEL[r.type]} · ${rangeText(r)}${r.days != null ? ` · ${num(r.days)} ngày` : ''}`;
  const pending = r.status === 'CHO_DUYET';
  return {
    key: `leave-${r.id}`,
    name: r.employeeName ?? '',
    detail: pending ? what : `Xin hủy đơn đã duyệt · ${what}`,
    approve: pending ? () => leaveApi.act(r.id, 'approve') : undefined,
    reject: pending ? note => leaveApi.act(r.id, 'reject', note) : undefined,
    errorOf: err => leaveErrorText(err),
  };
};

const overtimeRow = (o: IOvertime): Row => ({
  key: `ot-${o.id}`,
  name: o.employeeName ?? '',
  detail: `Làm thêm giờ · ${isoToVn(o.workDate)} · ${num(o.status === 'CHO_XAC_NHAN_THEM' ? o.actualHours : o.plannedHours)} giờ`,
  errorOf: err => leaveErrorText(err),
});

const attendanceRow = (a: IPendingItem): Row => ({
  key: `att-${a.type}-${a.id}`,
  name: a.employeeName,
  detail: `${a.type === 'PUNCH' ? 'Lần chấm công' : 'Giải trình chấm công'} · ${isoToVn(a.day)}`,
  approve: () => (a.type === 'PUNCH' ? attendanceApi.approvePunch(a.id) : attendanceApi.approveExplanation(a.id)),
  reject: note => (a.type === 'PUNCH' ? attendanceApi.rejectPunch(a.id, note) : attendanceApi.rejectExplanation(a.id, note)),
  errorOf: err => errorText(err),
});

/** "Đơn chờ duyệt" trên Tổng quan: đơn nghỉ, làm thêm, chấm công chờ người đang đăng nhập duyệt (cùng API với trang "Duyệt đơn"). */
export const PendingApprovals = ({ onCount }: { onCount?: (n: number | null) => void }) => {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [rowError, setRowError] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [rejecting, setRejecting] = useState<Row | null>(null);
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);

  const load = () =>
    Promise.all([leaveApi.inbox(), leaveApi.overtimeInbox(), attendanceApi.pending()])
      .then(([l, o, a]) => {
        const all = [...l.map(leaveRow), ...o.map(overtimeRow), ...a.map(attendanceRow)];
        setRows(all);
        setError(null);
        onCount?.(all.length);
      })
      .catch(err => {
        setError(errorText(err, 'Không tải được đơn chờ duyệt'));
        onCount?.(null);
      });

  useEffect(() => {
    load();
  }, []);

  const approve = async (r: Row) => {
    if (!r.approve) return;
    setBusy(true);
    setNotice(null);
    setRowError(e => ({ ...e, [r.key]: '' }));
    try {
      await r.approve();
      setNotice(`Đã duyệt: ${r.name}.`);
      await load();
    } catch (err) {
      setRowError(e => ({ ...e, [r.key]: r.errorOf(err) }));
    } finally {
      setBusy(false);
    }
  };

  const openReject = (r: Row) => {
    setRejecting(r);
    setNote('');
    setNoteError(null);
  };

  const confirmReject = async () => {
    if (!rejecting?.reject) return;
    const text = note.trim();
    if (!text) {
      setNoteError('Phải ghi lý do từ chối');
      return;
    }
    setBusy(true);
    try {
      await rejecting.reject(text);
      setNotice(`Đã từ chối: ${rejecting.name}.`);
      setRejecting(null);
      await load();
    } catch (err) {
      setNoteError(rejecting.errorOf(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <SectionCard
      title={rows && rows.length > 0 ? `Đơn chờ duyệt (${rows.length})` : 'Đơn chờ duyệt'}
      titleId="pending-title"
      actions={<Link to="/duyet-don">Xem tất cả</Link>}
    >
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      {rows === null && !error && <p className="text-muted mb-0">Đang tải...</p>}
      {rows?.length === 0 && <p className="text-muted mb-0">Không có đơn nào chờ bạn duyệt.</p>}
      {rows && rows.length > 0 && (
        <ul className="list-unstyled pending-list mb-0">
          {rows.slice(0, MAX_ROWS).map(r => (
            <li key={r.key} className="pending-item">
              <span className="doit-avatar" aria-hidden="true">
                {initials(r.name)}
              </span>
              <div className="pending-body">
                <span className="pending-name">{r.name}</span>
                <span className="pending-detail">{r.detail}</span>
                {rowError[r.key] && (
                  <span className="text-danger" role="alert">
                    {rowError[r.key]}
                  </span>
                )}
              </div>
              <div className="pending-actions">
                {r.approve && r.reject ? (
                  <>
                    <Button size="sm" variant="outline-secondary" disabled={busy} onClick={() => openReject(r)}>
                      Từ chối
                    </Button>
                    <Button size="sm" variant="dark" disabled={busy} onClick={() => approve(r)}>
                      Duyệt
                    </Button>
                  </>
                ) : (
                  <Link className="btn btn-sm btn-outline-secondary" to="/duyet-don">
                    Xử lý
                  </Link>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <Modal show={!!rejecting} onHide={() => setRejecting(null)} centered>
        <Modal.Header closeButton closeLabel="Đóng">
          <Modal.Title as="h3" className="h5">
            Từ chối – {rejecting?.name}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {noteError && (
            <Alert variant="danger" role="alert">
              {noteError}
            </Alert>
          )}
          <Form.Group controlId="pending-reject-note">
            <Form.Label>Lý do từ chối</Form.Label>
            <Form.Control as="textarea" rows={3} maxLength={500} value={note} onChange={e => setNote(e.target.value)} />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={() => setRejecting(null)}>
            Hủy bỏ
          </Button>
          <Button variant="danger" disabled={busy} onClick={confirmReject}>
            Xác nhận từ chối
          </Button>
        </Modal.Footer>
      </Modal>
    </SectionCard>
  );
};

export default PendingApprovals;
