/** "yyyy-MM-dd" thành "dd/MM/yyyy". */
export const isoToVn = (iso?: string | null): string => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '');
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '';
};

const VN_PARTS = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** Thời điểm ISO (có múi giờ) thành "dd/MM/yyyy", hoặc "dd/MM/yyyy HH:mm" khi {@code withTime}, theo giờ Việt Nam. */
export const instantToVn = (instant?: string | null, withTime = false): string => {
  if (!instant) return '';
  const p = Object.fromEntries(VN_PARTS.formatToParts(new Date(instant)).map(x => [x.type, x.value]));
  const date = `${p.day}/${p.month}/${p.year}`;
  return withTime ? `${date} ${p.hour}:${p.minute}` : date;
};

/** "dd/MM/yyyy" thành "yyyy-MM-dd"; chuỗi rỗng ra null, ngày không có thật ra undefined. */
export const vnToIso = (vn: string): string | null | undefined => {
  const t = (vn ?? '').trim();
  if (!t) {
    return null;
  }
  const m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(t);
  if (!m) {
    return undefined;
  }
  const [d, mo, y] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const dt = new Date(Date.UTC(y, mo - 1, d));
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== mo - 1 || dt.getUTCDate() !== d) {
    return undefined;
  }
  return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
};

/** Nhãn lịch nghỉ việc đã hẹn: "Sắp nghỉ (dd/MM/yyyy)" theo ngày làm việc cuối. */
export const scheduledTerminationLabel = (iso?: string | null): string => `Sắp nghỉ (${isoToVn(iso)})`;

/** Nhãn lịch nghỉ việc quá hạn mà job chưa áp dụng được. */
export const overdueTerminationLabel = (reason?: string | null): string =>
  `Quá hạn nghỉ việc – chưa áp dụng được${reason ? `: ${reason}` : ''}`;

interface IScheduled {
  scheduledTermination?: string | null;
  scheduledTerminationOverdue?: boolean | null;
  scheduledTerminationError?: string | null;
}

/** Nhãn và kiểu nhãn cho lịch nghỉ việc (null nếu không có lịch). */
export const scheduledBadge = (e: IScheduled): { text: string; className: string } | null => {
  if (!e.scheduledTermination) return null;
  if (e.scheduledTerminationOverdue) {
    return { text: overdueTerminationLabel(e.scheduledTerminationError), className: 'doit-badge doit-badge-danger' };
  }
  return { text: scheduledTerminationLabel(e.scheduledTermination), className: 'doit-badge doit-badge-warn' };
};
