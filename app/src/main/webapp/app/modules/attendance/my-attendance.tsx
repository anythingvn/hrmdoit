import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Button, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';

import { ConfirmAsk, ConfirmModal } from '../leave/leave-shared';
import {
  IDayView,
  IExplanation,
  IMonthBoard,
  IPunchView,
  attendanceApi,
  cellTitle,
  isAutoFilled,
  isRedCell,
  monthVn,
  previousMonth,
  punchReasonText,
  shiftMonth,
  todayIso,
} from './attendance-api';
import ExplanationForm from './explanation-form';

const TOUCH = { minHeight: 44 };
const WEEKDAY = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

const SOURCE: Record<string, string> = { GO_TAY: 'Gõ tay', APP: 'Tự chấm', GIAI_TRINH: 'Giải trình' };
const EXPLANATION_STATUS: Record<string, { label: string; bg: string }> = {
  DANG_CHO: { label: 'Đang chờ', bg: 'warning' },
  DA_DUYET: { label: 'Đã duyệt', bg: 'success' },
  TU_CHOI: { label: 'Từ chối', bg: 'danger' },
  DA_RUT: { label: 'Đã rút', bg: 'secondary' },
};

/** Trạng thái hiển thị của một lần chấm; lần tự chấm chưa có trạng thái thì không tính công. */
const punchStatus = (p: IPunchView): { label: string; bg: string } => {
  switch (p.status) {
    case 'CHAP_NHAN':
      return { label: 'Được tính', bg: 'success' };
    case 'DA_DUYET':
      return { label: 'Được tính (đã duyệt)', bg: 'success' };
    case 'CHO_DUYET':
      return { label: 'Chờ duyệt', bg: 'warning' };
    case 'TU_CHOI':
      return { label: 'Từ chối', bg: 'danger' };
    default:
      return p.source === 'APP' ? { label: 'Không tính', bg: 'secondary' } : { label: 'Được tính', bg: 'success' };
  }
};

const METHOD: Record<string, string> = { IP: 'IP văn phòng', GPS: 'GPS', TU_XA: 'từ xa' };

const punchLine = (p: IPunchView) => {
  const parts = [`${p.kind === 'VAO' ? 'Vào' : 'Ra'} ${p.time}`, SOURCE[p.source] ?? p.source];
  if (p.method)
    parts.push(METHOD[p.method] + (p.method === 'GPS' && p.distanceM !== null && p.distanceM !== undefined ? ` ${p.distanceM} m` : ''));
  return parts.join(' · ');
};

const fmt = (n: number | null | undefined) => (n === null || n === undefined ? '' : String(n).replace('.', ','));

const weekday = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return WEEKDAY[new Date(y, m - 1, d).getDay()];
};

/** "Bảng công của tôi": bảng tháng của chính mình, chỉ đọc. Tháng đã chốt thì số tổng là số đã chốt. */
export const MyAttendance = () => {
  const [month, setMonth] = useState<string>(() => previousMonth());
  const [board, setBoard] = useState<IMonthBoard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [explanations, setExplanations] = useState<IExplanation[]>([]);
  const [open, setOpen] = useState<string | null>(null);
  const [explainDay, setExplainDay] = useState<string | null>(null);
  const [ask, setAsk] = useState<ConfirmAsk | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    attendanceApi
      .my(month)
      .then(setBoard)
      .catch(err => {
        setBoard(null);
        setError(errorText(err, 'Không tải được bảng công'));
      });
    attendanceApi
      .myExplanations(month)
      .then(list => setExplanations(Array.isArray(list) ? list : []))
      .catch(() => setExplanations([]));
  }, [month]);

  useEffect(load, [load]);

  const waiting = new Set(explanations.filter(e => e.status === 'DANG_CHO').map(e => e.workDay));
  const today = todayIso();
  const canExplain = (v: IDayView) =>
    board?.status === 'DANG_MO' &&
    v.day <= today &&
    !waiting.has(v.day) &&
    (isRedCell(v) || (v.punches ?? []).some(p => p.status === 'TU_CHOI'));
  const isPending = (v: IDayView) => (v.pendingCount ?? 0) > 0 || waiting.has(v.day);
  const dayMonth = (iso: string) => isoToVn(iso).slice(0, 5);

  const row = board?.rows[0];
  const t = row?.totals;

  return (
    <div>
      <h2>Bảng công của tôi</h2>
      <div className="d-flex flex-wrap gap-2 align-items-center mb-3">
        <Button variant="outline-secondary" style={TOUCH} onClick={() => setMonth(shiftMonth(month, -1))}>
          ‹ Tháng trước
        </Button>
        <strong>Tháng {monthVn(month)}</strong>
        <Button variant="outline-secondary" style={TOUCH} onClick={() => setMonth(shiftMonth(month, 1))}>
          Tháng sau ›
        </Button>
        {board && (
          <Badge bg={board.status === 'DA_CHOT' ? 'success' : 'secondary'}>{board.status === 'DA_CHOT' ? 'Đã chốt' : 'Đang mở'}</Badge>
        )}
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && (
        <Alert variant="success" dismissible onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}
      {board && !row && <p className="text-muted">Bạn không có bảng công tháng {monthVn(board.month)}.</p>}
      {row && t && (
        <>
          {row.changedAfterClose && (
            <Alert variant="warning">Dữ liệu tháng này đã thay đổi sau khi chốt; số tổng dưới đây là số đã chốt.</Alert>
          )}
          <dl className="d-flex flex-wrap gap-4 mb-3" data-testid="my-totals">
            <div>
              <dt>Công chuẩn</dt>
              <dd>{fmt(t.standardDays)}</dd>
            </div>
            <div>
              <dt>Công thực tế</dt>
              <dd>
                <strong>{fmt(t.actualDays)}</strong>
              </dd>
            </div>
            <div>
              <dt>Đi muộn</dt>
              <dd>{t.lateCount > 0 ? `${t.lateCount} lần, ${t.lateMinutes} phút` : 'Không'}</dd>
            </div>
            <div>
              <dt>Ngày nửa công</dt>
              <dd>{t.halfDays}</dd>
            </div>
            <div>
              <dt>Ngày vắng</dt>
              <dd>{t.absentDays}</dd>
            </div>
            <div>
              <dt>Thiếu giờ / chưa nhập</dt>
              <dd className={t.missingDays > 0 ? 'text-danger' : undefined}>{t.missingDays}</dd>
            </div>
          </dl>
          <Table responsive size="sm" className="align-middle">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Vào</th>
                <th>Ra</th>
                <th>Công</th>
                <th>Ghi chú</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {row.days.map(v => (
                <React.Fragment key={v.day}>
                  <tr
                    data-testid={`my-day-${v.day}`}
                    className={isPending(v) ? 'table-warning' : isRedCell(v) ? 'table-danger' : undefined}
                  >
                    <td className="text-nowrap">
                      <Button
                        variant="link"
                        className="p-0 align-baseline"
                        aria-expanded={open === v.day}
                        onClick={() => setOpen(open === v.day ? null : v.day)}
                      >
                        {isoToVn(v.day)}
                      </Button>{' '}
                      <span className="text-muted">{weekday(v.day)}</span>
                    </td>
                    <td>{isAutoFilled(v) ? '' : (v.in ?? '')}</td>
                    <td>{isAutoFilled(v) ? '' : (v.out ?? '')}</td>
                    <td>{v.autoKind === 'NGHI' || v.autoKind === 'NGOAI_HD' ? '' : fmt(v.actualCredit)}</td>
                    <td>
                      {cellTitle(v)}
                      {isPending(v) && (
                        <Badge bg="warning" text="dark" className="ms-1">
                          {(v.pendingCount ?? 0) > 0 ? 'Chờ duyệt' : 'Chờ giải trình'}
                        </Badge>
                      )}
                    </td>
                    <td>
                      {canExplain(v) && (
                        <Button variant="primary" size="sm" style={TOUCH} onClick={() => setExplainDay(v.day)}>
                          Gửi giải trình cho ngày {dayMonth(v.day)}
                        </Button>
                      )}
                    </td>
                  </tr>
                  {open === v.day && (
                    <tr data-testid={`my-detail-${v.day}`}>
                      <td colSpan={6}>
                        {(v.punches ?? []).length === 0 ? (
                          <span className="text-muted">Chưa có lần chấm nào.</span>
                        ) : (
                          <ul className="list-unstyled mb-0">
                            {(v.punches ?? []).map(p => {
                              const ps = punchStatus(p);
                              return (
                                <li key={p.id} className="d-flex flex-wrap gap-2 align-items-center py-1">
                                  <span>{punchLine(p)}</span>
                                  <Badge bg={ps.bg} text={ps.bg === 'warning' ? 'dark' : undefined}>
                                    {ps.label}
                                  </Badge>
                                  {(punchReasonText(p) ?? p.reviewReason) && (
                                    <span className="text-muted small">{punchReasonText(p) ?? p.reviewReason}</span>
                                  )}
                                </li>
                              );
                            })}
                          </ul>
                        )}
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </Table>
        </>
      )}
      <h3 className="h5 mt-4">Giải trình của tôi</h3>
      {explanations.length === 0 ? (
        <p className="text-muted">Tháng {monthVn(month)} bạn chưa gửi giải trình nào.</p>
      ) : (
        <Table responsive size="sm" className="align-middle">
          <thead>
            <tr>
              <th>Ngày</th>
              <th>Giờ vào</th>
              <th>Giờ ra</th>
              <th>Lý do</th>
              <th>Trạng thái</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {explanations.map(e => {
              const es = EXPLANATION_STATUS[e.status] ?? { label: e.status, bg: 'secondary' };
              return (
                <tr key={e.id} data-testid={`my-explanation-${e.id}`}>
                  <td className="text-nowrap">{isoToVn(e.workDay)}</td>
                  <td>{e.proposedIn ?? ''}</td>
                  <td>{e.proposedOut ?? ''}</td>
                  <td>{e.reason}</td>
                  <td>
                    <Badge bg={es.bg} text={es.bg === 'warning' ? 'dark' : undefined}>
                      {es.label}
                    </Badge>
                    {e.decisionNote && <div className="text-muted small">{e.decisionNote}</div>}
                  </td>
                  <td>
                    {e.status === 'DANG_CHO' && (
                      <Button
                        variant="outline-secondary"
                        style={TOUCH}
                        aria-label={`Rút giải trình ngày ${isoToVn(e.workDay)}`}
                        onClick={() =>
                          setAsk({
                            title: 'Rút giải trình',
                            body: (
                              <p className="mb-0">Rút giải trình ngày {isoToVn(e.workDay)}? Muốn giải trình lại thì gửi giải trình mới.</p>
                            ),
                            confirmLabel: 'Xác nhận rút',
                            danger: true,
                            run: () => attendanceApi.withdrawExplanation(e.id),
                            done: 'Đã rút giải trình.',
                          })
                        }
                      >
                        Rút
                      </Button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </Table>
      )}
      <ConfirmModal
        ask={ask}
        onClose={() => setAsk(null)}
        onDone={ok => {
          setNotice(ok);
          load();
        }}
      />
      {explainDay && (
        <ExplanationForm
          day={explainDay}
          onClose={() => setExplainDay(null)}
          onDone={() => {
            setExplainDay(null);
            setNotice('Đã gửi giải trình.');
            load();
          }}
        />
      )}
    </div>
  );
};

export default MyAttendance;
