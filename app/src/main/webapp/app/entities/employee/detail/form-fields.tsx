import React from 'react';

import MoneyInput from '../money-input';

/** Ô nhập có nhãn, gợi ý và lỗi gắn bằng aria-describedby — dùng chung cho các hộp thoại trang chi tiết. */

interface IFieldBase {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  className?: string;
}

const describedBy = (id: string, error?: string, hint?: string) =>
  [error ? `${id}-error` : null, hint ? `${id}-hint` : null].filter(Boolean).join(' ') || undefined;

const Messages = ({ id, error, hint }: { id: string; error?: string; hint?: string }) => (
  <>
    {hint && (
      <div id={`${id}-hint`} className="form-text">
        {hint}
      </div>
    )}
    {error && (
      <div id={`${id}-error`} className="invalid-feedback d-block">
        {error}
      </div>
    )}
  </>
);

interface ITextFieldProps extends IFieldBase {
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
  required?: boolean;
  multiline?: boolean;
}

export const TextField = ({
  id,
  label,
  error,
  hint,
  className,
  value,
  onChange,
  type,
  placeholder,
  required,
  multiline,
}: ITextFieldProps) => {
  const props = {
    id,
    className: `form-control ${error ? 'is-invalid' : ''}`,
    value,
    placeholder,
    'aria-required': required || undefined,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': describedBy(id, error, hint),
  };
  return (
    <div className={className ?? 'col-12 col-md-6'}>
      <label htmlFor={id} className="form-label">
        {label}
      </label>
      {multiline ? (
        <textarea {...props} rows={3} onChange={e => onChange(e.target.value)} />
      ) : (
        <input {...props} type={type ?? 'text'} autoComplete="off" onChange={e => onChange(e.target.value)} />
      )}
      <Messages id={id} error={error} hint={hint} />
    </div>
  );
};

/** Ô ngày kiểu dd/MM/yyyy. */
export const DateField = (props: Omit<ITextFieldProps, 'type' | 'placeholder' | 'multiline'>) => (
  <TextField {...props} placeholder="dd/MM/yyyy" />
);

interface ISelectFieldProps extends IFieldBase {
  value: string;
  onChange: (value: string) => void;
  options: [string, string][];
  emptyLabel?: string;
}

export const SelectField = ({ id, label, error, hint, className, value, onChange, options, emptyLabel }: ISelectFieldProps) => (
  <div className={className ?? 'col-12 col-md-6'}>
    <label htmlFor={id} className="form-label">
      {label}
    </label>
    <select
      id={id}
      className={`form-select ${error ? 'is-invalid' : ''}`}
      value={value}
      onChange={e => onChange(e.target.value)}
      aria-invalid={error ? true : undefined}
      aria-describedby={describedBy(id, error, hint)}
    >
      {emptyLabel !== undefined && <option value="">{emptyLabel}</option>}
      {options.map(([v, l]) => (
        <option key={v} value={v}>
          {l}
        </option>
      ))}
    </select>
    <Messages id={id} error={error} hint={hint} />
  </div>
);

interface IMoneyFieldProps extends IFieldBase {
  value: number | null;
  onChange: (value: number | null) => void;
  onValidityChange?: (valid: boolean) => void;
}

export const MoneyField = ({ id, label, error, hint, className, value, onChange, onValidityChange }: IMoneyFieldProps) => (
  <div className={className ?? 'col-12 col-md-6'}>
    <label htmlFor={id} className="form-label">
      {label}
    </label>
    <MoneyInput
      id={id}
      value={value}
      onChange={onChange}
      onValidityChange={onValidityChange}
      aria-describedby={describedBy(id, error, hint)}
    />
    <Messages id={id} error={error} hint={hint} />
  </div>
);

/** Hộp báo lỗi từ server, đọc ngay cho trình đọc màn hình. */
export const ServerError = ({ message }: { message: string | null }) =>
  message ? (
    <div role="alert" className="alert alert-danger">
      {message}
    </div>
  ) : null;
