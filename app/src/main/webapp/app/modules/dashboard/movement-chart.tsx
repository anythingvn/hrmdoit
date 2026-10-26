import React, { useState } from 'react';
import { Table } from 'react-bootstrap';

export interface IMovement {
  month: string;
  hires: number;
  leavers: number;
  closing: number;
}

const W = 720;
const H = 240;
const M = { top: 12, right: 8, bottom: 28, left: 32 };
const PLOT_W = W - M.left - M.right;
const PLOT_H = H - M.top - M.bottom;
const BAR = 16;
const GAP = 2;
const RADIUS = 4;

const SERIES = [
  { key: 'hires', label: 'Vào làm', color: 'var(--viz-series-1)' },
  { key: 'leavers', label: 'Nghỉ việc', color: 'var(--viz-series-2)' },
] as const;

const monthLabel = (m: string) => `T${Number(m.slice(5, 7))}`;
const monthLong = (m: string) => `${m.slice(5, 7)}/${m.slice(0, 4)}`;

/** Trục tung: số nguyên "tròn" ≥ giá trị lớn nhất, chia 4 vạch. */
const niceMax = (v: number) => {
  if (v <= 4) return 4;
  const step = Math.ceil(v / 4);
  return step * 4;
};

/** Cột bo 4px ở đầu số liệu, vuông ở đường gốc. */
const columnPath = (x: number, y: number, w: number, h: number) => {
  const r = Math.min(RADIUS, h, w / 2);
  const base = y + h;
  return `M${x},${base}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${base}Z`;
};

/** Vào làm (navy) và nghỉ việc (cam) theo tháng (cột đôi, một trục); quân số cuối tháng ở chú giải khi rê chuột và trong bảng. */
export const MovementChart = ({ data, title }: { data: IMovement[]; title: string }) => {
  const [hover, setHover] = useState<number | null>(null);
  const max = niceMax(Math.max(0, ...data.flatMap(d => [d.hires, d.leavers])));
  const band = PLOT_W / Math.max(data.length, 1);
  const y = (v: number) => M.top + PLOT_H - (v / max) * PLOT_H;
  const ticks = [0, 1, 2, 3, 4].map(i => (max / 4) * i);
  const summary = `${title}: tổng vào làm ${data.reduce((s, d) => s + d.hires, 0)}, tổng nghỉ việc ${data.reduce(
    (s, d) => s + d.leavers,
    0,
  )}, quân số cuối kỳ ${data.length ? data[data.length - 1].closing : 0}.`;
  const h = hover === null ? null : data[hover];

  return (
    <figure className="viz-root movement-chart mb-0">
      <div className="viz-legend d-flex gap-3 mb-1" aria-hidden="true">
        {SERIES.map(s => (
          <span key={s.key} className="d-inline-flex align-items-center gap-1">
            <span className="viz-swatch" style={{ background: s.color }} />
            {s.label}
          </span>
        ))}
      </div>
      <div className="position-relative">
        <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label={summary} onMouseLeave={() => setHover(null)}>
          {ticks.map(t => (
            <g key={t}>
              <line x1={M.left} x2={W - M.right} y1={y(t)} y2={y(t)} className="viz-grid" />
              <text x={M.left - 6} y={y(t)} dy="0.32em" textAnchor="end" className="viz-axis">
                {t}
              </text>
            </g>
          ))}
          {data.map((d, i) => {
            const x0 = M.left + i * band + (band - (BAR * 2 + GAP)) / 2;
            return (
              <g key={d.month}>
                {hover === i && <rect x={M.left + i * band} y={M.top} width={band} height={PLOT_H} className="viz-hover-band" />}
                {SERIES.map((s, k) => {
                  const v = d[s.key];
                  return v > 0 ? <path key={s.key} d={columnPath(x0 + k * (BAR + GAP), y(v), BAR, y(0) - y(v))} fill={s.color} /> : null;
                })}
                <text x={M.left + i * band + band / 2} y={H - 8} textAnchor="middle" className="viz-axis">
                  {monthLabel(d.month)}
                </text>
                <rect
                  x={M.left + i * band}
                  y={M.top}
                  width={band}
                  height={PLOT_H + M.bottom}
                  fill="transparent"
                  onMouseEnter={() => setHover(i)}
                  onFocus={() => setHover(i)}
                />
              </g>
            );
          })}
        </svg>
        {h && (
          <div className="viz-tooltip" style={{ left: `${((((hover ?? 0) + 0.5) * band + M.left) / W) * 100}%` }} role="status">
            <div className="fw-semibold">Tháng {monthLong(h.month)}</div>
            <div>Vào làm: {h.hires}</div>
            <div>Nghỉ việc: {h.leavers}</div>
            <div>Cuối tháng: {h.closing} người</div>
          </div>
        )}
      </div>
      <details className="mt-2">
        <summary>Xem dạng bảng</summary>
        <Table size="sm" className="mt-2 mb-0">
          <thead>
            <tr>
              <th scope="col">Tháng</th>
              <th scope="col" className="text-end">
                Vào làm
              </th>
              <th scope="col" className="text-end">
                Nghỉ việc
              </th>
              <th scope="col" className="text-end">
                Cuối tháng
              </th>
            </tr>
          </thead>
          <tbody>
            {data.map(d => (
              <tr key={d.month}>
                <td>{monthLong(d.month)}</td>
                <td className="text-end">{d.hires}</td>
                <td className="text-end">{d.leavers}</td>
                <td className="text-end">{d.closing}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      </details>
    </figure>
  );
};

export default MovementChart;
