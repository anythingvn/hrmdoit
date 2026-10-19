import React, { useEffect, useState } from 'react';
import axios from 'axios';

import { IHistoryEntry, errorText } from './detail-api';
import { fieldLabel, formatValue, isHiddenField } from './history-format';

const ENTITY_LABEL: Record<string, string> = {
  Employee: 'Hồ sơ',
  EmploymentContract: 'Hợp đồng',
  SalaryChange: 'Điều chỉnh lương',
  Allowance: 'Phụ cấp',
  WorkAssignment: 'Vị trí làm việc',
  EmployeeDocument: 'Hồ sơ scan',
};

const dateTime = (instant: string) => {
  const d = new Date(instant);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

const visible = (e: IHistoryEntry) => e.changes.filter(c => !isHiddenField(c.field));

/** Nhật ký thay đổi (chỉ Quản trị và Nhân sự). */
export const HistoryTab = ({ employeeId }: { employeeId: number }) => {
  const [entries, setEntries] = useState<IHistoryEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    axios
      .get<IHistoryEntry[]>(`api/employees/${employeeId}/history`)
      .then(r => setEntries(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được nhật ký thay đổi')));
  }, [employeeId]);

  return (
    <section className="doit-section" aria-labelledby="h-history">
      <h2 id="h-history">Lịch sử thay đổi</h2>
      {error && (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      )}
      {entries === null && !error && <p className="mb-0">Đang tải...</p>}
      {entries?.length === 0 && <p className="mb-0">Chưa có thay đổi</p>}
      {entries && entries.length > 0 && (
        <ol className="doit-history">
          {entries.map((e, i) => (
            <li key={`${e.at}-${i}`}>
              <div className="fw-semibold">
                {dateTime(e.at)} · {ENTITY_LABEL[e.entity] ?? e.entity} · {e.action}
                {e.login && <span className="text-muted fw-normal"> · {e.login}</span>}
              </div>
              {visible(e).length > 0 && (
                <div className="table-responsive">
                  <table className="table table-sm doit-table mb-0">
                    <thead>
                      <tr>
                        <th scope="col">Trường</th>
                        <th scope="col">Trước</th>
                        <th scope="col">Sau</th>
                      </tr>
                    </thead>
                    <tbody>
                      {visible(e).map(c => (
                        <tr key={c.field}>
                          <td>{fieldLabel(e.entity, c.field)}</td>
                          <td>{formatValue(e.entity, c.field, c.before)}</td>
                          <td>{formatValue(e.entity, c.field, c.after)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
};

export default HistoryTab;
