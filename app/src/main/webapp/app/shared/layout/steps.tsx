import React from 'react';

/** Thanh bước của các wizard nhập file (kiểu canvas Import bảng lương); `current` tính từ 1. */
export const Steps = ({ steps, current }: { steps: string[]; current: number }) => (
  <ol className="doit-steps" aria-label="Các bước import">
    {steps.map((s, i) => {
      const n = i + 1;
      const state = n < current ? 'done' : n === current ? 'current' : 'todo';
      return (
        <li key={s} className={`doit-step doit-step-${state}`} aria-current={n === current ? 'step' : undefined}>
          <span className="doit-step-num" aria-hidden="true">
            {state === 'done' ? '✓' : n}
          </span>
          <span className="doit-step-label">
            <span className="visually-hidden">{n}. </span>
            {s}
          </span>
        </li>
      );
    })}
  </ol>
);

export default Steps;
