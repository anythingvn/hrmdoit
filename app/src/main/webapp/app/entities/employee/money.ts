/** Đọc số tiền kiểu Việt Nam: "15.000.000", "15000000", "15,000,000" đều ra 15000000. Sai định dạng thì null. */
export const parseMoney = (s: string): number | null => {
  const t = (s ?? '').trim();
  if (!t) {
    return null;
  }
  if (/^\d+$/.test(t)) {
    return Number(t);
  }
  if (/^\d{1,3}([.,]\d{3})+$/.test(t)) {
    return Number(t.replace(/[.,]/g, ''));
  }
  return null;
};

/** 15000000 thành "15.000.000". */
export const formatMoney = (n: number): string => String(Math.trunc(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
