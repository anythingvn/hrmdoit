import './leave.scss';

import React, { useMemo, useState } from 'react';
import { Button, Modal } from 'react-bootstrap';

import { isoToVn } from 'app/entities/employee/vn-date';

import { ICalendarDay, ICalendarItem, daysBetween, isWeekendIso, shiftMonth } from './leave-api';
import { CALENDAR_COLOR, CALENDAR_TYPE_LABEL } from './leave-labels';

export interface MonthCalendarProps {
  /** "yyyy-MM". */
  month: string;
  /** Ngày trong tháng từ API (cuối tuần theo lịch công ty, tên ngày lễ); thiếu thì coi Thứ Bảy, Chủ nhật là ngày nghỉ. */
  days: ICalendarDay[];
  items: ICalendarItem[];
  onMonthChange?: (month: string) => void;
  /** Bấm "+N người" của một ngày: nhận ngày và mọi mục của ngày đó (lịch cũng tự mở danh sách). */
  onDayClick?: (date: string, items: ICalendarItem[]) => void;
  /** Số người hiện trong một ô trước khi gộp "+N người". */
  maxPerDay?: number;
}

const HEADERS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

const halfLabel = (part?: string | null) => (part === 'SANG' ? ' (sáng)' : part === 'CHIEU' ? ' (chiều)' : '');

/** "Tên · Loại", thêm "(sáng)/(chiều)" ở ngày nghỉ nửa ngày. */
export const entryLabel = (it: ICalendarItem, date: string) => {
  let half = '';
  if (date === it.from) half = halfLabel(it.firstPart);
  else if (date === it.to) half = halfLabel(it.lastPart);
  return `${it.employeeName} · ${CALENDAR_TYPE_LABEL[it.type] ?? it.type}${half}`;
};

const Entry = ({ item, date }: { item: ICalendarItem; date: string }) => {
  const c = CALENDAR_COLOR[item.type] ?? CALENDAR_COLOR.KHONG_LUONG;
  return (
    <div
      className={`mc-entry${item.pending ? ' is-pending' : ''}`}
      style={{ background: c.bg, color: c.fg, borderColor: c.border ?? c.bg }}
      title={item.pending ? 'Đang chờ duyệt' : undefined}
    >
      {entryLabel(item, date)}
    </div>
  );
};

/**
 * Lịch tháng kiểu A: lưới 7 cột Thứ Hai – Chủ nhật, mỗi ô ghi "Tên · Loại", ô đông người gộp "+N người"; đơn chờ duyệt hiện mờ; cuối
 * tuần và ngày lễ nền xám. Không hiện lý do nghỉ.
 */
export const MonthCalendar = ({ month, days, items, onMonthChange, onDayClick, maxPerDay = 3 }: MonthCalendarProps) => {
  const [open, setOpen] = useState<string | null>(null);
  const [y, m] = month.split('-').map(Number);

  const dayInfo = useMemo(() => new Map(days.map(d => [d.date, d])), [days]);

  const byDay = useMemo(() => {
    const map = new Map<string, ICalendarItem[]>();
    const first = `${month}-01`;
    const last = `${month}-${String(new Date(Date.UTC(y, m, 0)).getUTCDate()).padStart(2, '0')}`;
    items.forEach(it => {
      const from = it.from < first ? first : it.from;
      const to = it.to > last ? last : it.to;
      if (from > to) return;
      daysBetween(from, to).forEach(d => {
        const list = map.get(d) ?? [];
        list.push(it);
        map.set(d, list);
      });
    });
    return map;
  }, [items, month]);

  const weeks = useMemo(() => {
    const count = new Date(Date.UTC(y, m, 0)).getUTCDate();
    const lead = (new Date(Date.UTC(y, m - 1, 1)).getUTCDay() + 6) % 7;
    const cells: (string | null)[] = Array.from({ length: lead }, () => null);
    for (let d = 1; d <= count; d++) cells.push(`${month}-${String(d).padStart(2, '0')}`);
    while (cells.length % 7) cells.push(null);
    const out: (string | null)[][] = [];
    for (let i = 0; i < cells.length; i += 7) out.push(cells.slice(i, i + 7));
    return out;
  }, [month]);

  const showDay = (date: string) => {
    setOpen(date);
    onDayClick?.(date, byDay.get(date) ?? []);
  };

  const openItems = open ? (byDay.get(open) ?? []) : [];

  return (
    <div className="month-calendar">
      <div className="mc-head">
        {onMonthChange && (
          <Button
            variant="outline-secondary"
            className="mc-nav"
            aria-label="Tháng trước"
            onClick={() => onMonthChange(shiftMonth(month, -1))}
          >
            ‹
          </Button>
        )}
        <h3 className="mc-title">{`Tháng ${String(m).padStart(2, '0')}/${y}`}</h3>
        {onMonthChange && (
          <Button variant="outline-secondary" className="mc-nav" aria-label="Tháng sau" onClick={() => onMonthChange(shiftMonth(month, 1))}>
            ›
          </Button>
        )}
      </div>
      <div className="mc-scroll">
        <table className="mc-grid">
          <thead>
            <tr>
              {HEADERS.map(h => (
                <th key={h} scope="col">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {weeks.map((week, wi) => (
              <tr key={wi}>
                {week.map((date, di) => {
                  if (!date) return <td key={`x${di}`} className="mc-day is-blank" />;
                  const info = dayInfo.get(date);
                  const off = info ? info.weekend || !!info.holidayName : isWeekendIso(date);
                  const list = byDay.get(date) ?? [];
                  const extra = list.length - maxPerDay;
                  return (
                    <td key={date} data-testid={`day-${date}`} className={`mc-day${off ? ' is-off' : ''}`}>
                      <div className="mc-num">{Number(date.slice(8))}</div>
                      {info?.holidayName && <div className="mc-holiday">{info.holidayName}</div>}
                      {list.slice(0, extra > 0 ? maxPerDay : list.length).map((it, i) => (
                        <Entry key={`${it.employeeId}-${it.type}-${i}`} item={it} date={date} />
                      ))}
                      {extra > 0 && (
                        <button
                          type="button"
                          className="mc-more"
                          onClick={() => showDay(date)}
                          aria-label={`+${extra} người, ngày ${isoToVn(date)}`}
                        >
                          +{extra} người
                        </button>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Modal show={!!open} onHide={() => setOpen(null)} centered>
        <Modal.Header closeButton closeLabel="Đóng">
          <Modal.Title as="h3" className="h5">
            Ngày {open ? isoToVn(open) : ''}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {openItems.length === 0 && <p className="mb-0">Không có ai nghỉ.</p>}
          {openItems.map((it, i) => (
            <Entry key={`${it.employeeId}-${it.type}-${i}`} item={it} date={open ?? ''} />
          ))}
        </Modal.Body>
      </Modal>
    </div>
  );
};

export default MonthCalendar;
