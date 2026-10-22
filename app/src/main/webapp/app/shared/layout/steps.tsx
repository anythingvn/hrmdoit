import React from 'react';

/** Thanh bước của các wizard nhập file; `current` tính từ 1. */
export const Steps = ({ steps, current }: { steps: string[]; current: number }) => (
  <ol className="list-inline mb-3" aria-label="Các bước import">
    {steps.map((s, i) => (
      <li
        key={s}
        className={`list-inline-item ${i + 1 === current ? 'fw-bold' : 'text-muted'}`}
        aria-current={i + 1 === current ? 'step' : undefined}
      >
        {i + 1}. {s}
      </li>
    ))}
  </ol>
);

export default Steps;
