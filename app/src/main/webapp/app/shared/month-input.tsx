import React, { useEffect, useState } from 'react';
import { Form } from 'react-bootstrap';

/** "MM/yyyy", "M/yyyy" hoặc "yyyy-MM" thành "yyyy-MM"; không hợp lệ ra null. */
export const vnMonthToIso = (text: string): string | null => {
  const t = (text ?? '').trim();
  let m = /^(\d{1,2})\/(\d{4})$/.exec(t);
  let month: number;
  let year: number;
  if (m) {
    month = Number(m[1]);
    year = Number(m[2]);
  } else {
    m = /^(\d{4})-(\d{2})$/.exec(t);
    if (!m) return null;
    year = Number(m[1]);
    month = Number(m[2]);
  }
  return month >= 1 && month <= 12 ? `${year}-${String(month).padStart(2, '0')}` : null;
};

/** "yyyy-MM" thành "MM/yyyy"; chuỗi khác giữ nguyên. */
export const isoMonthToVn = (iso: string): string => {
  const m = /^(\d{4})-(\d{2})$/.exec(iso ?? '');
  return m ? `${m[2]}/${m[1]}` : (iso ?? '');
};

/** Tháng của hôm nay lùi {@code back} tháng, dạng "yyyy-MM". */
export const monthsAgo = (back: number): string => {
  const now = new Date();
  const d = new Date(now.getFullYear(), now.getMonth() - back, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
};

type Props = Omit<React.ComponentProps<typeof Form.Control>, 'value' | 'onChange' | 'type'> & {
  /** "yyyy-MM" khi hợp lệ, hoặc chuỗi đang gõ dở. */
  value: string;
  onChange: (value: string) => void;
};

/**
 * Ô nhập tháng dạng MM/yyyy (ô chữ, chạy trên mọi trình duyệt; Safari không hỗ trợ ô kiểu tháng).
 * Gõ tháng hợp lệ thì trả "yyyy-MM"; đang gõ dở thì trả nguyên chuỗi để người gọi báo lỗi.
 */
export const MonthInput = ({ value, onChange, ...rest }: Props) => {
  const [text, setText] = useState(() => isoMonthToVn(value));

  // Giá trị đổi từ ngoài (vd tải dữ liệu xong): hiện lại theo MM/yyyy.
  useEffect(() => {
    if (vnMonthToIso(text) !== value && text !== value) setText(isoMonthToVn(value));
  }, [value]);

  return (
    <Form.Control
      {...rest}
      type="text"
      inputMode="numeric"
      placeholder="MM/yyyy"
      autoComplete="off"
      value={text}
      onChange={e => {
        setText(e.target.value);
        onChange(vnMonthToIso(e.target.value) ?? e.target.value);
      }}
    />
  );
};

export default MonthInput;
