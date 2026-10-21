import { vi } from 'vitest';
import React, { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';

import MonthInput, { isoMonthToVn, vnMonthToIso } from './month-input';

describe('vnMonthToIso', () => {
  it('nhận MM/yyyy, M/yyyy và yyyy-MM', () => {
    expect(vnMonthToIso('06/2026')).toBe('2026-06');
    expect(vnMonthToIso('6/2026')).toBe('2026-06');
    expect(vnMonthToIso(' 2026-06 ')).toBe('2026-06');
    expect(vnMonthToIso('13/2026')).toBeNull();
    expect(vnMonthToIso('')).toBeNull();
    expect(isoMonthToVn('2026-06')).toBe('06/2026');
    expect(isoMonthToVn('')).toBe('');
  });
});

describe('MonthInput', () => {
  const Host = ({ initial, spy }: { initial: string; spy: (v: string) => void }) => {
    const [v, setV] = useState(initial);
    return (
      <label>
        Kỳ
        <MonthInput
          value={v}
          onChange={x => {
            setV(x);
            spy(x);
          }}
        />
      </label>
    );
  };

  it('hiện MM/yyyy, gõ tháng hợp lệ thì trả yyyy-MM, gõ dở thì trả chuỗi đang gõ', () => {
    const spy = vi.fn();
    render(<Host initial="2026-05" spy={spy} />);
    const input = screen.getByLabelText('Kỳ') as HTMLInputElement;
    expect(input.value).toBe('05/2026');
    expect(input.type).toBe('text');
    fireEvent.change(input, { target: { value: '6/2026' } });
    expect(spy).toHaveBeenLastCalledWith('2026-06');
    fireEvent.change(input, { target: { value: '06/20' } });
    expect(spy).toHaveBeenLastCalledWith('06/20');
    expect(input.value).toBe('06/20');
  });
});
