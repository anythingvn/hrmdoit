import './leave.scss';

import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Modal } from 'react-bootstrap';

import { isoToVn } from 'app/entities/employee/vn-date';
import { downloadFile } from 'app/shared/util/download';

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
}

const personalLabel = (v?: string | null) => PERSONAL_REASONS.find(p => p.value === v)?.label;

/** "Duyệt đơn": đơn nghỉ và làm thêm giờ chờ người đăng nhập quyết định; xem số dư người xin; duyệt nhiều đơn nghỉ một lần. */
export const ApprovalInbox = () => {
  const [leaves, setLeaves] = useState<ILeaveRequest[] | null>(null);
  const [ots, setOts] = useState<IOvertime[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [rowError, setRowError] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<number[]>([]);
  const [compensation, setCompensation] = useState<Record<number, Compensation>>({});
  const [bulk, setBulk] = useState<IBulkResult[] | null>(null);
  const [ask, setAsk] = useState<NoteAsk | null>(null);
  const [askNote, setAskNote] = useState('');
  const [askError, setAskError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () =>
    Promise.all([leaveApi.inbox(), leaveApi.overtimeInbox()])
      .then(([l, o]) => {
        setLeaves(l);
        setOts(o);
        setSelected(sel => sel.filter(id => l.some(r => r.id === id && r.status === 'CHO_DUYET')));
      })
      .catch(err => setError(leaveErrorText(err, 'ngày', 'Không tải được đơn chờ duyệt')));

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
      await ask.run(note);
      setAsk(null);
      setNotice(ask.done);
      setBulk(null);
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

  const toggle = (id: number) => setSelected(s => (s.includes(id) ? s.filter(x => x !== id) : [...s, id]));
  const nameOf = (id: number) => leaves?.find(r => r.id === id)?.employeeName ?? `Đơn #${id}`;

  return (
    <div className="leave-page">
      <div>
        <h1>Duyệt đơn</h1>
        <p className="text-muted mb-0">Đơn nghỉ và làm thêm giờ đang chờ bạn quyết định. Xem số dư của người xin trước khi duyệt.</p>
      </div>
      {error && (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      )}
      {notice && <Alert variant="success">{notice}</Alert>}
      {bulk && bulk.length > 0 && (
        <Alert variant="warning">
          <div className="fw-bold mb-1">Các đơn chưa duyệt được:</div>
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
            <Button onClick={bulkApprove} disabled={busy}>
              Duyệt {selected.length} đơn đã chọn
            </Button>
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
                    <Form.Check
                      id={`sel-${r.id}`}
                      aria-label={`Chọn đơn của ${r.employeeName}`}
                      checked={selected.includes(r.id)}
                      onChange={() => toggle(r.id)}
                    />
                  )}
                  <strong>{r.employeeName}</strong>
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
                  <strong>{o.employeeName}</strong>
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

      <Modal show={!!ask} onHide={() => setAsk(null)} centered>
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
    </div>
  );
};

export default ApprovalInbox;
