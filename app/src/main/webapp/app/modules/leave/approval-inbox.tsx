import './leave.scss';

import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Modal } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';
import { downloadFile } from 'app/shared/util/download';

import { IPendingItem, attendanceApi } from 'app/modules/attendance/attendance-api';
import { PageHeader } from 'app/shared/ui';

import EmployeeLeaveModal, { LeavePerson } from './employee-leave-modal';
import { IBulkResult, ILeaveRequest, IOvertime, leaveApi, leaveErrorText } from './leave-api';
import { StatusChip, rangeText } from './status-chip';
import {
  APPROVER_KIND_LABEL,
  COMPENSATION_LABEL,
  Compensation,
  LEAVE_STATUS_LABEL,
  LEAVE_TYPE_LABEL,
  OVERTIME_STATUS_LABEL,
  PERSONAL_REASONS,
  hhmm,
  num,
} from './leave-labels';

/** Hộp thoại ghi lý do (từ chối đơn, từ chối hủy, không duyệt phần giờ vượt). */
interface NoteAsk {
  title: string;
  confirm: string;
  required: boolean;
  run: (note: string) => Promise<unknown>;
  done: string;
  /** Từ chối nhiều đơn: {@code run} trả kết quả từng đơn. */
  bulk?: boolean;
}

/** Tên người xin: bấm để xem số dư, đơn và sổ phép của người đó. */
const PersonButton = ({ employeeId, name, onOpen }: { employeeId: number; name?: string; onOpen: (p: LeavePerson) => void }) => (
  <Button variant="link" className="leave-person" onClick={() => onOpen({ employeeId, name: name ?? '' })}>
    {name}
  </Button>
);

const personalLabel = (v?: string | null) => PERSONAL_REASONS.find(p => p.value === v)?.label;

/**
 * "Duyệt đơn": đơn nghỉ và làm thêm giờ chờ người đăng nhập quyết định; xem số dư người xin; duyệt hoặc từ chối (cùng lý do) nhiều
 * đơn nghỉ một lần.
 */
export const ApprovalInbox = () => {
  const [leaves, setLeaves] = useState<ILeaveRequest[] | null>(null);
  const [ots, setOts] = useState<IOvertime[] | null>(null);
  const [pending, setPending] = useState<IPendingItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attError, setAttError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [rowError, setRowError] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<number[]>([]);
  const [compensation, setCompensation] = useState<Record<number, Compensation>>({});
  const [bulk, setBulk] = useState<IBulkResult[] | null>(null);
  const [ask, setAsk] = useState<NoteAsk | null>(null);
  const [askNote, setAskNote] = useState('');
  const [askError, setAskError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [person, setPerson] = useState<LeavePerson | null>(null);

  // Đơn nghỉ + làm thêm giờ và chấm công tải riêng, mỗi phần có lỗi riêng: một phần lỗi không che phần kia.
  const loadLeaveAndOvertime = () =>
    Promise.all([leaveApi.inbox(), leaveApi.overtimeInbox()])
      .then(([l, o]) => {
        setLeaves(l);
        setOts(o);
        setSelected(sel => sel.filter(id => l.some(r => r.id === id && r.status === 'CHO_DUYET')));
      })
      .catch(err => setError(leaveErrorText(err, 'ngày', 'Không tải được đơn chờ duyệt')));

  const loadAttendance = () =>
    attendanceApi
      .pending()
      .then(a => {
        setAttError(null);
        setPending(a);
      })
      .catch(err => setAttError(errorText(err, 'Không tải được danh sách chấm công chờ duyệt')));

  const load = () => Promise.all([loadLeaveAndOvertime(), loadAttendance()]);

  useEffect(() => {
    load();
  }, []);

  const run = async (key: string, action: () => Promise<unknown>, ok: string) => {
    setError(null);
    setNotice(null);
    setBulk(null);
    setRowError(e => ({ ...e, [key]: '' }));
    setBusy(true);
    try {
      await action();
      setNotice(ok);
      await load();
    } catch (err) {
      setRowError(e => ({ ...e, [key]: leaveErrorText(err) }));
    } finally {
      setBusy(false);
    }
  };

  const openAsk = (a: NoteAsk) => {
    setAsk(a);
    setAskNote('');
    setAskError(null);
  };

  const confirmAsk = async () => {
    if (!ask) return;
    const note = askNote.trim();
    if (ask.required && !note) {
      setAskError('Phải ghi lý do từ chối');
      return;
    }
    setBusy(true);
    try {
      const res = await ask.run(note);
      setAsk(null);
      if (ask.bulk) {
        const results = (res as IBulkResult[]) ?? [];
        setNotice(`Đã từ chối ${results.filter(r => r.ok).length}/${results.length} đơn.`);
        setBulk(results.filter(r => !r.ok));
        setSelected([]);
      } else {
        setNotice(ask.done);
        setBulk(null);
      }
      await load();
    } catch (err) {
      setAskError(leaveErrorText(err));
    } finally {
      setBusy(false);
    }
  };

  const approveOt = (o: IOvertime) => {
    const c = compensation[o.id];
    if (!c) {
      setRowError(e => ({ ...e, [`ot-${o.id}`]: 'Chọn Trả tiền hoặc Nghỉ bù trước khi duyệt' }));
      return;
    }
    run(
      `ot-${o.id}`,
      () => leaveApi.actOvertime(o.id, 'approve', { compensation: c }),
      `Đã duyệt làm thêm của ${o.employeeName} (${COMPENSATION_LABEL[c]}).`,
    );
  };

  const bulkApprove = async () => {
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      const results = await leaveApi.bulkApprove(selected);
      const okCount = results.filter(r => r.ok).length;
      setNotice(`Đã duyệt ${okCount}/${results.length} đơn.`);
      setBulk(results.filter(r => !r.ok));
      setSelected([]);
      await load();
    } catch (err) {
      setError(leaveErrorText(err));
    } finally {
      setBusy(false);
    }
  };

  const bulkReject = () => {
    const ids = [...selected];
    openAsk({
      title: `Từ chối ${ids.length} đơn đã chọn`,
      confirm: 'Xác nhận từ chối',
      required: true,
      run: note => leaveApi.bulkReject(ids, note),
      done: '',
      bulk: true,
    });
  };

  const attKey = (a: IPendingItem) => `att-${a.type}-${a.id}`;
  const approveAtt = (a: IPendingItem) =>
    run(
      attKey(a),
      () => (a.type === 'PUNCH' ? attendanceApi.approvePunch(a.id) : attendanceApi.approveExplanation(a.id)),
      a.type === 'PUNCH' ? `Đã duyệt lần chấm của ${a.employeeName}.` : `Đã đồng ý giải trình của ${a.employeeName}.`,
    );
  const rejectAtt = (a: IPendingItem) =>
    openAsk({
      title: `Từ chối ${a.type === 'PUNCH' ? 'lần chấm' : 'giải trình'} của ${a.employeeName}`,
      confirm: 'Xác nhận từ chối',
      required: true,
      run: note => (a.type === 'PUNCH' ? attendanceApi.rejectPunch(a.id, note) : attendanceApi.rejectExplanation(a.id, note)),
      done: `Đã từ chối ${a.type === 'PUNCH' ? 'lần chấm' : 'giải trình'} của ${a.employeeName}.`,
    });

  const toggle = (id: number) => setSelected(s => (s.includes(id) ? s.filter(x => x !== id) : [...s, id]));
  const nameOf = (id: number) => leaves?.find(r => r.id === id)?.employeeName ?? `Đơn #${id}`;

  return (
    <div className="leave-page">
      <PageHeader
        title="Duyệt đơn"
        subtitle={<>Đơn nghỉ, làm thêm giờ và chấm công đang chờ bạn quyết định. Xem số dư của người xin trước khi duyệt.</>}
      />
      {error && (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      )}
      {notice && <Alert variant="success">{notice}</Alert>}
      {bulk && bulk.length > 0 && (
        <Alert variant="warning">
          <div className="fw-bold mb-1">Các đơn chưa xử lý được:</div>
          <ul className="mb-0">
            {bulk.map(r => (
              <li key={r.id}>
                {nameOf(r.id)}: {r.message || leaveErrorText({ response: { data: { message: `error.${r.errorKey}` } } })}
              </li>
            ))}
          </ul>
        </Alert>
      )}

      <section className="leave-section" aria-labelledby="inbox-leave">
        <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
          <h2 id="inbox-leave" className="mb-0 me-auto">
            Đơn nghỉ {leaves ? `(${leaves.length})` : ''}
          </h2>
          {selected.length > 0 && (
            <>
              <Button onClick={bulkApprove} disabled={busy}>
                Duyệt {selected.length} đơn đã chọn
              </Button>
              <Button variant="outline-danger" onClick={bulkReject} disabled={busy}>
                Từ chối các đơn đã chọn
              </Button>
            </>
          )}
        </div>
        {leaves === null && !error && <p>Đang tải...</p>}
        {leaves?.length === 0 && <p className="mb-0">Không có đơn nghỉ nào chờ bạn duyệt.</p>}
        <div className="d-flex flex-column gap-3">
          {leaves?.map(r => {
            const key = `leave-${r.id}`;
            const cancelAsk = r.status === 'CHO_HUY';
            return (
              <div key={r.id} className="inbox-card" data-testid={key}>
                <div className="d-flex flex-wrap align-items-center gap-2">
                  {!cancelAsk && (
                    // Cả ô 44px bấm được (không chỉ ô vuông nhỏ của checkbox).
                    <label className="inbox-select" htmlFor={`sel-${r.id}`}>
                      <input
                        type="checkbox"
                        className="form-check-input m-0"
                        id={`sel-${r.id}`}
                        aria-label={`Chọn đơn của ${r.employeeName}`}
                        checked={selected.includes(r.id)}
                        onChange={() => toggle(r.id)}
                      />
                    </label>
                  )}
                  <PersonButton employeeId={r.employeeId} name={r.employeeName} onOpen={setPerson} />
                  {r.employeeCode && <span className="text-muted">{r.employeeCode}</span>}
                  <StatusChip status={r.status} label={cancelAsk ? 'Xin hủy đơn đã duyệt' : LEAVE_STATUS_LABEL[r.status]} />
                </div>
                <div>
                  {LEAVE_TYPE_LABEL[r.type]}
                  {r.personalReason && ` – ${personalLabel(r.personalReason)}`} · {rangeText(r)} · <strong>{num(r.days)} ngày</strong>
                  {r.approverKind && <span className="text-muted"> · {APPROVER_KIND_LABEL[r.approverKind]} duyệt</span>}
                </div>
                {r.reasonText && <div className="text-muted">Ghi chú: {r.reasonText}</div>}
                {r.systemNote && <div className="text-muted">{r.systemNote}</div>}
                {r.balance && (
                  <div>
                    Số dư hiện tại: phép năm <strong>{num(r.balance.annualTotal)} ngày</strong> (được xin {num(r.balance.annualRequestable)}
                    ) · nghỉ bù <strong>{num(r.balance.compensatoryHours)} giờ</strong>
                  </div>
                )}
                {r.type === 'OM' && (
                  <div>
                    {r.sickDocId ? (
                      <Button
                        variant="link"
                        className="p-0"
                        onClick={() =>
                          downloadFile(leaveApi.sickDocumentUrl(r.id), `giay-nghi-om-${r.employeeCode ?? r.id}`).catch(err =>
                            setRowError(e => ({ ...e, [key]: leaveErrorText(err) })),
                          )
                        }
                      >
                        Xem giấy nghỉ ốm
                      </Button>
                    ) : (
                      <span className="text-muted">Chưa có giấy nghỉ ốm</span>
                    )}
                  </div>
                )}
                {rowError[key] && (
                  <div className="text-danger" role="alert">
                    {rowError[key]}
                  </div>
                )}
                <div className="inbox-actions">
                  {cancelAsk ? (
                    <>
                      <Button
                        disabled={busy}
                        onClick={() => run(key, () => leaveApi.act(r.id, 'cancel-approve'), `Đã đồng ý hủy đơn của ${r.employeeName}.`)}
                      >
                        Đồng ý hủy
                      </Button>
                      <Button
                        variant="outline-secondary"
                        disabled={busy}
                        onClick={() =>
                          openAsk({
                            title: `Không đồng ý hủy đơn của ${r.employeeName}`,
                            confirm: 'Xác nhận không hủy',
                            required: false,
                            run: note => leaveApi.act(r.id, 'cancel-reject', note),
                            done: `Đơn của ${r.employeeName} giữ nguyên đã duyệt.`,
                          })
                        }
                      >
                        Không đồng ý hủy
                      </Button>
                    </>
                  ) : (
                    <>
                      <Button
                        disabled={busy}
                        onClick={() => run(key, () => leaveApi.act(r.id, 'approve'), `Đã duyệt đơn của ${r.employeeName}.`)}
                      >
                        Duyệt
                      </Button>
                      <Button
                        variant="outline-danger"
                        disabled={busy}
                        onClick={() =>
                          openAsk({
                            title: `Từ chối đơn của ${r.employeeName}`,
                            confirm: 'Xác nhận từ chối',
                            required: true,
                            run: note => leaveApi.act(r.id, 'reject', note),
                            done: `Đã từ chối đơn của ${r.employeeName}.`,
                          })
                        }
                      >
                        Từ chối
                      </Button>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </section>

      <section className="leave-section" aria-labelledby="inbox-ot">
        <h2 id="inbox-ot">Làm thêm giờ {ots ? `(${ots.length})` : ''}</h2>
        {ots === null && !error && <p>Đang tải...</p>}
        {ots?.length === 0 && <p className="mb-0">Không có đơn làm thêm giờ nào chờ bạn duyệt.</p>}
        <div className="d-flex flex-column gap-3">
          {ots?.map(o => {
            const key = `ot-${o.id}`;
            const extra = o.status === 'CHO_XAC_NHAN_THEM';
            return (
              <div key={o.id} className="inbox-card" data-testid={key}>
                <div className="d-flex flex-wrap align-items-center gap-2">
                  <PersonButton employeeId={o.employeeId} name={o.employeeName} onOpen={setPerson} />
                  {o.employeeCode && <span className="text-muted">{o.employeeCode}</span>}
                  <StatusChip status={o.status} label={OVERTIME_STATUS_LABEL[o.status]} />
                </div>
                <div>
                  {isoToVn(o.workDate)} · {hhmm(o.startTime)} – {hhmm(o.endTime)} · <strong>{num(o.plannedHours)} giờ</strong> · hệ số{' '}
                  {o.rate}%{o.night && ' · có giờ đêm'}
                </div>
                {o.content && <div className="text-muted">Nội dung: {o.content}</div>}
                {extra && (
                  <div>
                    Giờ thực tế <strong>{num(o.actualHours)} giờ</strong>, vượt {num(Number(o.actualHours ?? 0) - Number(o.plannedHours))}{' '}
                    giờ so với đăng ký ({o.compensation ? COMPENSATION_LABEL[o.compensation] : ''}).
                  </div>
                )}
                {rowError[key] && (
                  <div className="text-danger" role="alert">
                    {rowError[key]}
                  </div>
                )}
                <div className="inbox-actions">
                  {extra ? (
                    <>
                      <Button
                        disabled={busy}
                        onClick={() =>
                          run(key, () => leaveApi.actOvertime(o.id, 'approve-extra'), `Đã duyệt phần giờ vượt của ${o.employeeName}.`)
                        }
                      >
                        Duyệt phần vượt
                      </Button>
                      <Button
                        variant="outline-secondary"
                        disabled={busy}
                        onClick={() =>
                          openAsk({
                            title: `Không duyệt phần giờ vượt của ${o.employeeName}`,
                            confirm: 'Xác nhận',
                            required: false,
                            run: note => leaveApi.actOvertime(o.id, 'reject-extra', note ? { note } : {}),
                            done: `Đơn của ${o.employeeName} hoàn tất với số giờ đăng ký.`,
                          })
                        }
                      >
                        Chỉ tính giờ đăng ký
                      </Button>
                    </>
                  ) : (
                    <>
                      <fieldset className="d-flex flex-wrap align-items-center">
                        <legend className="visually-hidden">Hình thức bù</legend>
                        {(['TRA_TIEN', 'NGHI_BU'] as Compensation[]).map(c => (
                          <Form.Check
                            key={c}
                            type="radio"
                            id={`comp-${o.id}-${c}`}
                            name={`comp-${o.id}`}
                            label={COMPENSATION_LABEL[c]}
                            checked={compensation[o.id] === c}
                            onChange={() => {
                              setCompensation(s => ({ ...s, [o.id]: c }));
                              setRowError(e => ({ ...e, [key]: '' }));
                            }}
                          />
                        ))}
                      </fieldset>
                      <Button disabled={busy} onClick={() => approveOt(o)}>
                        Duyệt
                      </Button>
                      <Button
                        variant="outline-danger"
                        disabled={busy}
                        onClick={() =>
                          openAsk({
                            title: `Từ chối làm thêm của ${o.employeeName}`,
                            confirm: 'Xác nhận từ chối',
                            required: true,
                            run: note => leaveApi.actOvertime(o.id, 'reject', { note }),
                            done: `Đã từ chối đơn làm thêm của ${o.employeeName}.`,
                          })
                        }
                      >
                        Từ chối
                      </Button>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </section>

      <section className="leave-section" aria-labelledby="inbox-att">
        <h2 id="inbox-att">Chấm công chờ duyệt {pending ? `(${pending.length})` : ''}</h2>
        {attError && (
          <Alert variant="danger" role="alert">
            {attError}
          </Alert>
        )}
        {pending === null && !attError && <p>Đang tải...</p>}
        {pending?.length === 0 && <p className="mb-0">Không có lần chấm hay giải trình nào chờ bạn duyệt.</p>}
        <div className="d-flex flex-column gap-3">
          {pending?.map(a => {
            const key = attKey(a);
            return (
              <div key={key} className="inbox-card" data-testid={key}>
                <div className="d-flex flex-wrap align-items-center gap-2">
                  <strong>{a.employeeName}</strong>
                  {a.employeeCode && <span className="text-muted">{a.employeeCode}</span>}
                  <span className="text-muted">{isoToVn(a.day)}</span>
                  <StatusChip status="CHO_DUYET" label={a.type === 'PUNCH' ? 'Lần chấm chờ duyệt' : 'Giải trình chờ duyệt'} />
                </div>
                <div>{a.summary}</div>
                {a.detail && <div className="text-muted">{a.detail}</div>}
                {rowError[key] && (
                  <div className="text-danger" role="alert">
                    {rowError[key]}
                  </div>
                )}
                <div className="inbox-actions">
                  <Button disabled={busy} onClick={() => approveAtt(a)}>
                    {a.type === 'PUNCH' ? 'Duyệt' : 'Đồng ý'}
                  </Button>
                  <Button variant="outline-danger" disabled={busy} onClick={() => rejectAtt(a)}>
                    Từ chối
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      <Modal show={!!ask} onHide={() => setAsk(null)} centered className="leave-modal">
        <Modal.Header closeButton closeLabel="Đóng">
          <Modal.Title as="h3" className="h5">
            {ask?.title}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {askError && (
            <Alert variant="danger" role="alert">
              {askError}
            </Alert>
          )}
          <Form.Group controlId="ask-note">
            <Form.Label>{ask?.required ? 'Lý do từ chối' : 'Ghi chú (không bắt buộc)'}</Form.Label>
            <Form.Control as="textarea" rows={3} maxLength={500} value={askNote} onChange={e => setAskNote(e.target.value)} />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={() => setAsk(null)}>
            Hủy bỏ
          </Button>
          <Button variant={ask?.required ? 'danger' : 'primary'} disabled={busy} onClick={confirmAsk}>
            {ask?.confirm}
          </Button>
        </Modal.Footer>
      </Modal>
      <EmployeeLeaveModal person={person} onClose={() => setPerson(null)} />
    </div>
  );
};

export default ApprovalInbox;
