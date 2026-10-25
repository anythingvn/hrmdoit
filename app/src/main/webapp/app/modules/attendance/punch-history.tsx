import React, { useState } from 'react';
import { Alert, Badge, Button } from 'react-bootstrap';

import { isoToVn } from 'app/entities/employee/vn-date';

import {
  AUTO_LABEL,
  AutoKind,
  IDayView,
  IPunchView,
  currentMonth,
  distanceText,
  isRedCell,
  monthVn,
  numberVn,
  shiftMonth,
  todayIso,
  weekdayVn,
} from './attendance-api';
import ExplanationForm from './explanation-form';
import { canExplainDay, isPendingDay, isWorkingNow, useMyMonth, waitingDays } from './my-month';

const TOUCH = { minHeight: 44, minWidth: 44 };
const METHOD: Record<string, string> = { IP: 'IP văn phòng', GPS: 'GPS', TU_XA: 'từ xa' };
/** Ô tự điền không phải nghỉ có đơn: ngày nghỉ hằng tuần, ngoài thời gian làm việc. */
const NOT_LEAVE: AutoKind[] = ['NGHI', 'NGOAI_HD'];

interface Tag {
  label: string;
  bg: string;
}

const worked = (v: IDayView) => !!(v.in || v.out);

/**
 * Nhãn của một ngày: loại nghỉ, làm ngày nghỉ, vắng, muộn, về sớm, đang làm (hôm nay đã vào, chưa ra), thiếu giờ, chưa chấm, chờ
 * duyệt, bị từ chối.
 */
const dayTags = (v: IDayView, waiting: Set<string>, today: string): Tag[] => {
  const tags: Tag[] = [];
  const kind = v.autoKind;
  if (kind && !NOT_LEAVE.includes(kind) && !(kind === 'LE' && worked(v))) {
    const part = v.leavePart ? ` buổi ${v.leavePart === 'SANG' ? 'sáng' : 'chiều'}` : '';
    tags.push({ label: AUTO_LABEL[kind] + part, bg: 'info' });
  }
  if ((kind === 'NGHI' || kind === 'LE') && worked(v)) tags.push({ label: 'Làm ngày nghỉ', bg: 'secondary' });
  if (v.markedAbsent) tags.push({ label: 'Vắng', bg: 'danger' });
  if (v.lateMinutes > 0) tags.push({ label: `Muộn ${v.lateMinutes}'`, bg: 'warning' });
  if (v.earlyMinutes > 0) tags.push({ label: `Về sớm ${v.earlyMinutes}'`, bg: 'warning' });
  if (isWorkingNow(v, today)) {
    tags.push({ label: 'Đang làm', bg: 'light' });
  } else if (v.status === 'THIEU_GIO') {
    tags.push({ label: v.in && !v.out ? 'Thiếu giờ ra' : !v.in && v.out ? 'Thiếu giờ vào' : 'Thiếu giờ', bg: 'danger' });
  }
  if (v.status === 'CHUA_NHAP') tags.push({ label: 'Chưa chấm', bg: 'danger' });
  if (isPendingDay(v, waiting)) tags.push({ label: 'Chờ duyệt', bg: 'warning' });
  if ((v.punches ?? []).some(p => p.status === 'TU_CHOI')) tags.push({ label: 'Bị từ chối', bg: 'danger' });
  return tags;
};

/** Nơi chấm và khoảng cách của một lần tự chấm, vd "Vào 09:50: Văn phòng DOIT · cách 35 m"; không có gì thì null. */
const placeText = (p: IPunchView): string | null => {
  const place = p.siteName ?? (p.method ? METHOD[p.method] : null);
  const distance = p.distanceM === null || p.distanceM === undefined ? null : `cách ${distanceText(p.distanceM)}`;
  if (!place && !distance) return null;
  return `${p.kind === 'VAO' ? 'Vào' : 'Ra'} ${p.time}: ${[place, distance].filter(Boolean).join(' · ')}`;
};

/** Ngày có dữ liệu để hiện: đã tới, có giờ / lần chấm / nghỉ có đơn / lễ / vắng, hoặc ngày đã qua còn thiếu giờ, chưa chấm. */
const hasData = (v: IDayView, today: string) => {
  if (v.day > today) return false;
  if (worked(v) || (v.punches ?? []).length > 0 || v.markedAbsent) return true;
  if (v.autoKind) return !NOT_LEAVE.includes(v.autoKind);
  return isRedCell(v) && v.day < today;
};

interface Props {
  /** Đổi giá trị để tải lại (sau khi chấm hoặc gửi giải trình ở màn chấm). */
  refresh?: number;
}

/**
 * Khung "Lịch sử" ở màn chấm công: chọn tháng (mặc định tháng hiện tại), dòng tổng (công thực tế / công chuẩn, đi muộn, thiếu giờ, chờ
 * duyệt) và mỗi ngày có dữ liệu một dòng. Số liệu là của "Bảng công của tôi" ({@code GET /api/attendance/my}); hiện công, không hiện giờ.
 */
export const PunchHistory = ({ refresh = 0 }: Props) => {
  const [month, setMonth] = useState<string>(() => currentMonth());
  const [tick, setTick] = useState(0);
  const { board, explanations, error } = useMyMonth(month, refresh + tick);
  const [explainDay, setExplainDay] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const changeMonth = (delta: number) => {
    setMonth(shiftMonth(month, delta));
    setNotice(null);
  };

  const waiting = waitingDays(explanations);
  const today = todayIso();
  const row = board?.rows?.[0];
  const t = row?.totals;
  const days = row ? row.days.filter(v => hasData(v, today)).reverse() : [];

  return (
    <section className="doit-card" aria-labelledby="punch-history-title">
      <div className="doit-card-head">
        <h3 className="doit-card-title" id="punch-history-title">
          Lịch sử
        </h3>
        <div className="d-flex align-items-center gap-1">
          <Button variant="outline-secondary" style={TOUCH} aria-label="Tháng trước" onClick={() => changeMonth(-1)}>
            ‹
          </Button>
          <strong className="px-1">Tháng {monthVn(month)}</strong>
          <Button variant="outline-secondary" style={TOUCH} aria-label="Tháng sau" onClick={() => changeMonth(1)}>
            ›
          </Button>
          {board?.status === 'DA_CHOT' && (
            <Badge bg="success" className="ms-1">
              Đã chốt
            </Badge>
          )}
        </div>
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && (
        <Alert variant="success" dismissible onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}
      {board && !row && <p className="text-muted mb-0">Bạn không có bảng công tháng {monthVn(board.month)}.</p>}
      {row && t && (
        <div data-testid="history-summary" className="d-flex flex-wrap gap-3 mb-2 pb-2 border-bottom">
          <span>
            Công thực tế{' '}
            <strong>
              {numberVn(t.actualDays)} / {numberVn(t.standardDays)}
            </strong>
          </span>
          <span>
            Đi muộn <strong>{t.lateCount ?? 0}</strong>
          </span>
          <span className={t.missingDays > 0 ? 'text-danger' : undefined}>
            Thiếu giờ <strong>{t.missingDays ?? 0}</strong>
          </span>
          <span>
            Chờ duyệt <strong>{row.pendingDays ?? 0}</strong>
          </span>
        </div>
      )}
      {row && days.length === 0 && <p className="text-muted mb-0">Chưa có ngày nào có dữ liệu trong tháng này.</p>}
      {days.length > 0 && (
        <ul className="list-unstyled mb-0">
          {days.map(v => {
            const places = (v.punches ?? [])
              .filter(p => p.source === 'APP')
              .map(placeText)
              .filter((x): x is string => !!x);
            const showCredit = !(v.autoKind && NOT_LEAVE.includes(v.autoKind)) || worked(v);
            const times = worked(v) ? `${v.in ?? '--:--'} → ${v.out ?? '--:--'}` : '';
            const credit = showCredit ? `${numberVn(v.actualCredit)} công` : '';
            const tags = dayTags(v, waiting, today);
            return (
              <li key={v.day} data-testid={`history-day-${v.day}`} className="d-flex align-items-start gap-3 py-2 border-bottom">
                <div className="text-center" style={{ minWidth: 56 }}>
                  <div className="fw-bold fs-5 lh-1">{v.day.slice(8, 10)}</div>
                  <div className="small text-muted">{weekdayVn(v.day)}</div>
                </div>
                <div className="flex-fill">
                  <div>{[times, credit].filter(Boolean).join(' · ')}</div>
                  {tags.length > 0 && (
                    <div className="d-flex flex-wrap gap-1 mt-1">
                      {tags.map(tag => (
                        <Badge key={tag.label} bg={tag.bg} text={tag.bg === 'warning' || tag.bg === 'light' ? 'dark' : undefined}>
                          {tag.label}
                        </Badge>
                      ))}
                    </div>
                  )}
                  {places.length > 0 && (
                    <div className="small text-muted mt-1 d-flex flex-column">
                      {places.map(text => (
                        <span key={text}>{text}</span>
                      ))}
                    </div>
                  )}
                </div>
                {canExplainDay(board?.status, v, waiting, today) && (
                  <Button
                    variant="link"
                    className="p-0 text-nowrap"
                    style={TOUCH}
                    aria-label={`Giải trình ngày ${isoToVn(v.day).slice(0, 5)}`}
                    onClick={() => setExplainDay(v.day)}
                  >
                    Giải trình
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {explainDay && (
        <ExplanationForm
          day={explainDay}
          onClose={() => setExplainDay(null)}
          onDone={() => {
            setExplainDay(null);
            setNotice('Đã gửi giải trình.');
            setTick(n => n + 1);
          }}
        />
      )}
    </section>
  );
};

export default PunchHistory;
