/** "yyyy-MM-dd" thành "dd/MM/yyyy". */
export const isoToVn = (iso?: string | null): string => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '');
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '';
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
