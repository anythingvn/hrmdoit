import './leave.scss';

import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Table } from 'react-bootstrap';

import { isoToVn } from 'app/entities/employee/vn-date';
import { PageHeader } from 'app/shared/ui';

import {
  ICalendar,
  ILeaveBalance,
  ILeaveRequest,
  ILedgerEntry,
  IOvertime,
  currentMonth,
  leaveApi,
  leaveErrorText,
  todayIso,
} from './leave-api';
import {
  COMPENSATION_LABEL,
  LEAVE_STATUS_LABEL,
  LEAVE_TYPE_LABEL,
  OVERTIME_STATUS_LABEL,
  PERSONAL_REASONS,
  hhmm,
  num,
} from './leave-labels';
import { BalanceCards, ConfirmAsk, ConfirmModal, LedgerTable } from './leave-shared';
import LeaveForm from './leave-form';
import MonthCalendar from './month-calendar';
import OvertimeForm from './overtime-form';
import { StatusChip, rangeText } from './status-chip';

const CLOSED = ['TU_CHOI', 'DA_RUT', 'DA_HUY'];

/** "Nghỉ phép": số dư, tạo đơn nghỉ / đăng ký làm thêm, đơn đã gửi, lịch của tôi, sổ phép của tôi. */
export const MyLeave = () => {
  const [balance, setBalance] = useState<ILeaveBalance | null>(null);
  const [requests, setRequests] = useState<ILeaveRequest[]>([]);
  const [overtime, setOvertime] = useState<IOvertime[]>([]);
  const [ledger, setLedger] = useState<ILedgerEntry[]>([]);
  const [month, setMonth] = useState(currentMonth());
  const [calendar, setCalendar] = useState<ICalendar | null>(null);
  const [mode, setMode] = useState<'leave' | 'ot'>('leave');
  const [fatal, setFatal] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [confirmHours, setConfirmHours] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);
  const [ask, setAsk] = useState<ConfirmAsk | null>(null);
  const today = todayIso();

  const loadAll = async () => {
    try {
      const b = await leaveApi.myBalance();
      setBalance(b);
      const [r, o, l] = await Promise.all([leaveApi.mine(), leaveApi.myOvertime(), leaveApi.ledger(b.employeeId)]);
      setRequests(r);
      setOvertime(o);
      setLedger(l);
    } catch (err) {
      setFatal(leaveErrorText(err, 'ngày', 'Không tải được dữ liệu nghỉ phép'));
    }
  };

  useEffect(() => {
    loadAll();
  }, []);

  useEffect(() => {
    leaveApi
      .calendar(month)
      .then(setCalendar)
      .catch(err => setError(leaveErrorText(err, 'ngày', 'Không tải được lịch')));
  }, [month, requests]);

  const run = async (action: () => Promise<unknown>, ok: string) => {
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      await action();
      setNotice(ok);
      await loadAll();
    } catch (err) {
      setError(leaveErrorText(err));
    } finally {
      setBusy(false);
    }
  };

  const confirmOt = (o: IOvertime) => {
    const raw = (confirmHours[o.id] ?? String(o.plannedHours)).replace(',', '.');
    const hours = Number(raw);
    if (!raw || Number.isNaN(hours) || hours <= 0) {
      setError('Nhập số giờ thực tế lớn hơn 0');
      return;
    }
    run(() => leaveApi.confirmOvertime(o.id, hours), `Đã xác nhận ${num(hours)} giờ làm thêm ngày ${isoToVn(o.workDate)}.`);
  };

  if (fatal) {
    return (
      <div className="leave-page">
        <PageHeader title="Nghỉ phép" />
        <Alert variant="warning" role="alert">
          {fatal}
        </Alert>
      </div>
    );
  }
  if (!balance) return <p>Đang tải...</p>;

  const myItems = (calendar?.items ?? []).filter(it => it.employeeId === balance.employeeId);

  return (
    <div className="leave-page">
      <PageHeader title="Nghỉ phép" subtitle={<>Số ngày phép của bạn, đơn đã gửi và sổ phép.</>} />
      {error && (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      )}
      {notice && <Alert variant="success">{notice}</Alert>}

      <BalanceCards balance={balance} />

      <section className="leave-section" aria-labelledby="new-request">
        <h2 id="new-request">Tạo đơn mới</h2>
        <div className="leave-segment" role="group" aria-label="Loại đơn mới">
          <Button variant={mode === 'leave' ? 'dark' : 'light'} aria-pressed={mode === 'leave'} onClick={() => setMode('leave')}>
            Đơn nghỉ
          </Button>
          <Button variant={mode === 'ot' ? 'dark' : 'light'} aria-pressed={mode === 'ot'} onClick={() => setMode('ot')}>
            Làm thêm giờ
          </Button>
        </div>
        {mode === 'leave' ? (
          <LeaveForm balance={balance} onSubmitted={() => loadAll()} />
        ) : (
          <OvertimeForm balance={balance} onSubmitted={() => loadAll()} />
        )}
      </section>

      <section className="leave-section" aria-labelledby="my-requests">
        <h2 id="my-requests">Đơn nghỉ đã gửi</h2>
        {requests.length === 0 ? (
          <p className="mb-0">Bạn chưa gửi đơn nghỉ nào.</p>
        ) : (
          <Table responsive className="align-middle">
            <thead>
              <tr>
                <th scope="col">Loại</th>
                <th scope="col">Thời gian</th>
                <th scope="col">Số ngày</th>
                <th scope="col">Trạng thái</th>
                <th scope="col">Người duyệt</th>
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {requests.map(r => (
                <tr key={r.id} data-testid={`mine-${r.id}`}>
                  <td>
                    {LEAVE_TYPE_LABEL[r.type]}
                    {r.personalReason && (
                      <div className="small text-muted">{PERSONAL_REASONS.find(p => p.value === r.personalReason)?.label}</div>
                    )}
                  </td>
                  <td>{rangeText(r)}</td>
                  <td>{num(r.days)}</td>
                  <td>
                    <StatusChip status={r.status} label={LEAVE_STATUS_LABEL[r.status]} />
                    {r.decisionNote && <div className="small text-muted">{r.decisionNote}</div>}
                    {r.systemNote && <div className="small text-muted">{r.systemNote}</div>}
                  </td>
                  <td>{r.approverName ?? r.decidedBy ?? ''}</td>
                  <td className="text-end">
                    <div className="d-flex flex-wrap gap-2 justify-content-end">
                      {r.status === 'CHO_DUYET' && (
                        <Button
                          variant="outline-secondary"
                          disabled={busy}
                          onClick={() =>
                            setAsk({
                              title: 'Rút đơn nghỉ',
                              body: <p className="mb-0">Rút đơn {rangeText(r)}? Đơn sẽ không được duyệt nữa; muốn nghỉ thì gửi đơn mới.</p>,
                              confirmLabel: 'Xác nhận rút đơn',
                              danger: true,
                              run: () => leaveApi.act(r.id, 'withdraw'),
                              done: 'Đã rút đơn.',
                            })
                          }
                        >
                          Rút đơn
                        </Button>
                      )}
                      {r.status === 'DA_DUYET' && r.fromDate > today && (
                        <Button
                          variant="outline-secondary"
                          disabled={busy}
                          onClick={() =>
                            setAsk({
                              title: 'Xin hủy đơn đã duyệt',
                              body: (
                                <p className="mb-0">
                                  Gửi yêu cầu hủy đơn {rangeText(r)}? Người duyệt đồng ý thì đơn được hủy và số ngày được hoàn lại.
                                </p>
                              ),
                              confirmLabel: 'Gửi yêu cầu hủy',
                              run: () => leaveApi.act(r.id, 'cancel-request'),
                              done: 'Đã gửi yêu cầu hủy đơn, chờ người duyệt đồng ý.',
                            })
                          }
                        >
                          Xin hủy
                        </Button>
                      )}
                      {r.type === 'OM' && !r.sickDocId && !CLOSED.includes(r.status) && (
                        <Form.Label className="btn btn-outline-secondary mb-0" htmlFor={`sick-${r.id}`}>
                          Tải giấy nghỉ ốm
                          <Form.Control
                            id={`sick-${r.id}`}
                            type="file"
                            className="visually-hidden"
                            accept="application/pdf,image/jpeg,image/png"
                            onChange={e => {
                              const f = (e.target as HTMLInputElement).files?.[0];
                              if (f) run(() => leaveApi.uploadSickDocument(r.id, f), 'Đã tải giấy nghỉ ốm.');
                            }}
                          />
                        </Form.Label>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </section>

      <section className="leave-section" aria-labelledby="my-overtime">
        <h2 id="my-overtime">Làm thêm giờ đã đăng ký</h2>
        {overtime.length === 0 ? (
          <p className="mb-0">Bạn chưa đăng ký làm thêm giờ.</p>
        ) : (
          <Table responsive className="align-middle">
            <thead>
              <tr>
                <th scope="col">Ngày</th>
                <th scope="col">Giờ</th>
                <th scope="col">Số giờ</th>
                <th scope="col">Hình thức</th>
                <th scope="col">Trạng thái</th>
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {overtime.map(o => (
                <tr key={o.id}>
                  <td>{isoToVn(o.workDate)}</td>
                  <td>
                    {hhmm(o.startTime)} – {hhmm(o.endTime)}
                    <div className="small text-muted">
                      Hệ số {o.rate}%{o.night ? ' · có giờ đêm' : ''}
                    </div>
                  </td>
                  <td>
                    {num(o.actualHours ?? o.plannedHours)}
                    {o.actualHours != null && Number(o.actualHours) !== Number(o.plannedHours) && (
                      <div className="small text-muted">đăng ký {num(o.plannedHours)}</div>
                    )}
                  </td>
                  <td>{o.compensation ? COMPENSATION_LABEL[o.compensation] : '—'}</td>
                  <td>
                    <StatusChip status={o.status} label={OVERTIME_STATUS_LABEL[o.status]} />
                    {o.decisionNote && <div className="small text-muted">{o.decisionNote}</div>}
                  </td>
                  <td className="text-end">
                    <div className="d-flex flex-wrap gap-2 justify-content-end align-items-center">
                      {o.status === 'CHO_DUYET' && (
                        <Button
                          variant="outline-secondary"
                          disabled={busy}
                          onClick={() =>
                            setAsk({
                              title: 'Rút đơn làm thêm giờ',
                              body: (
                                <p className="mb-0">
                                  Rút đăng ký làm thêm ngày {isoToVn(o.workDate)}, {hhmm(o.startTime)} – {hhmm(o.endTime)}?
                                </p>
                              ),
                              confirmLabel: 'Xác nhận rút đơn',
                              danger: true,
                              run: () => leaveApi.actOvertime(o.id, 'withdraw'),
                              done: 'Đã rút đơn làm thêm.',
                            })
                          }
                        >
                          Rút đơn
                        </Button>
                      )}
                      {o.status === 'DA_DUYET' && o.workDate <= today && (
                        <>
                          <Form.Control
                            aria-label={`Giờ thực tế ngày ${isoToVn(o.workDate)}`}
                            style={{ width: 90 }}
                            inputMode="decimal"
                            value={confirmHours[o.id] ?? num(o.plannedHours)}
                            onChange={e => setConfirmHours(s => ({ ...s, [o.id]: e.target.value }))}
                          />
                          <Button disabled={busy} onClick={() => confirmOt(o)}>
                            Xác nhận giờ
                          </Button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </section>

      <section className="leave-section" aria-labelledby="my-calendar">
        <h2 id="my-calendar">Lịch của tôi</h2>
        <MonthCalendar month={month} days={calendar?.days ?? []} items={myItems} onMonthChange={setMonth} />
      </section>

      <ConfirmModal
        ask={ask}
        onClose={() => setAsk(null)}
        onDone={ok => {
          setError(null);
          setNotice(ok);
          loadAll();
        }}
      />

      <section className="leave-section" aria-labelledby="my-ledger">
        <h2 id="my-ledger">Sổ phép của tôi</h2>
        <p className="text-muted">Mọi lần cộng, trừ phép năm và giờ nghỉ bù. Dòng đã ghi không sửa; điều chỉnh được ghi thành dòng mới.</p>
        <LedgerTable ledger={ledger} />
      </section>
    </div>
  );
};

export default MyLeave;
