import React, { useEffect, useState } from 'react';

import { formatMoney, parseMoney } from './money';

interface IMoneyInputProps {
  id: string;
  value: number | null | undefined;
  onChange: (value: number | null) => void;
  /** Báo cho form biết nội dung gõ có hợp lệ không (ô trống là hợp lệ). */
  onValidityChange?: (valid: boolean) => void;
  'aria-describedby'?: string;
  className?: string;
}

/** Ô nhập tiền: hiển thị 1.234.567, chấp nhận gõ 1234567 hoặc 1,234,567; gõ chữ thì báo lỗi. */
export const MoneyInput = ({ id, value, onChange, onValidityChange, className, ...rest }: IMoneyInputProps) => {
  const [text, setText] = useState(value == null ? '' : formatMoney(value));
  const [invalid, setInvalid] = useState(false);
  const errorId = `${id}-error`;

  useEffect(() => {
    if (value != null && parseMoney(text) !== value) {
      setText(formatMoney(value));
      setInvalid(false);
    }
  }, [value]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const next = e.target.value;
    setText(next);
    const parsed = parseMoney(next);
    const bad = next.trim() !== '' && parsed === null;
    setInvalid(bad);
    onValidityChange?.(!bad);
    onChange(parsed);
  };

  const handleBlur = () => {
    const parsed = parseMoney(text);
    if (parsed !== null) {
      setText(formatMoney(parsed));
    }
  };

  const describedBy = [rest['aria-describedby'], invalid ? errorId : null].filter(Boolean).join(' ') || undefined;

  return (
    <>
      <input
        id={id}
        type="text"
        inputMode="numeric"
        className={`form-control ${invalid ? 'is-invalid' : ''} ${className ?? ''}`}
        value={text}
        onChange={handleChange}
        onBlur={handleBlur}
        aria-invalid={invalid}
        aria-describedby={describedBy}
      />
      {invalid && (
        <div id={errorId} className="invalid-feedback d-block">
          Số tiền không hợp lệ
        </div>
      )}
    </>
  );
};

export default MoneyInput;
