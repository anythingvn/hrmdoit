import './leave.scss';

import React, { useEffect, useState } from 'react';
import { Alert, Button, Table } from 'react-bootstrap';

import { downloadFile } from 'app/shared/util/download';

import { IAccounting, currentMonth, leaveApi, leaveErrorText, shiftMonth } from './leave-api';
import { num } from './leave-labels';

/** Cách xử lý số phép năm chốt khi nghỉ việc (dương: trả tiền; âm: trừ lương). */
export const settlementText = (days?: number | null): string | null => {
  if (days == null || Number(days) === 0) return null;
  return Number(days) > 0 ? 'Trả tiền phép chưa nghỉ' : 'Trừ lương phép ứng';
};

const monthTitle = (month: string) => {
  const [y, m] = month.split('-');
  return `Tháng ${m}/${y}`;
};

/** Số 0 hiện mờ để dễ nhìn ra người có số liệu. */
const Cell = ({ v }: { v?: number | null }) => (
  <td className={`text-end${!v || Number(v) === 0 ? ' text-muted' : ''}`}>{v == null ? '' : num(v)}</td>
);

/**
 * "Tổng hợp nghỉ & làm thêm" (Kế toán, HR, Quản trị): mỗi người một dòng trong tháng — ngày nghỉ theo loại, giờ làm thêm trả tiền theo
 * hệ số (kèm số lần có giờ đêm), số phép chốt khi nghỉ việc. Tải Excel cùng số liệu.
 */
export const AccountingSummary = () => {
  const [month, setMonth] = useState(currentMonth());
  const [data, setData] = useState<IAccounting | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    setData(null);
    setError(null);
    leaveApi
      .accounting(month)
      .then(d => live && setData(d))
      .catch(err => live && setError(leaveErrorText(err, 'ngày', 'Không tải được số liệu tổng hợp')));
    return () => {
      live = false;
    };
  }, [month]);

  const exportExcel = () =>
    downloadFile(leaveApi.accountingExportUrl, `tong-hop-nghi-lam-them-${month}.xlsx`, { month }).catch(err =>
      setError(leaveErrorText(err, 'ngày', 'Không tải được file Excel')),
    );

  return (
    <div className="leave-page">
      <div>
        <h1>Tổng hợp nghỉ &amp; làm thêm</h1>
        <p className="text-muted mb-0">
          Ngày nghỉ đã duyệt theo loại, giờ làm thêm được trả tiền theo hệ số (theo tháng của ngày làm) và phép năm chốt khi nghỉ việc, để
          đối chiếu bảng lương.
        </p>
      </div>
      {error && (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      )}
      <section className="leave-section" aria-label={`Số liệu ${monthTitle(month)}`}>
        <div className="leave-table-tools align-items-center">
          <Button
            variant="outline-secondary"
            className="leave-nav"
            aria-label="Tháng trước"
            onClick={() => setMonth(m => shiftMonth(m, -1))}
          >
            ‹
          </Button>
          <h2 className="mb-0">{monthTitle(month)}</h2>
          <Button variant="outline-secondary" className="leave-nav" aria-label="Tháng sau" onClick={() => setMonth(m => shiftMonth(m, 1))}>
            ›
          </Button>
          <Button className="ms-auto" variant="outline-primary" onClick={() => void exportExcel()}>
            Tải Excel
          </Button>
        </div>
        {!data && !error && <p>Đang tải...</p>}
        {data && data.rows.length === 0 && <p className="mb-0">Không có nhân viên nào trong tháng này.</p>}
        {data && data.rows.length > 0 && (
          <Table responsive className="align-middle" size="sm">
            <thead>
              <tr>
                <th scope="col" rowSpan={2}>
                  Nhân viên
                </th>
                <th scope="colgroup" colSpan={5} className="text-center">
                  Ngày nghỉ
                </th>
                <th scope="colgroup" colSpan={4} className="text-center">
                  Làm thêm trả tiền (giờ)
                </th>
                <th scope="colgroup" colSpan={2} className="text-center">
                  Chốt khi nghỉ việc
                </th>
              </tr>
              <tr>
                <th scope="col" className="text-end">
                  Phép năm
                </th>
                <th scope="col" className="text-end">
                  Việc riêng
                </th>
                <th scope="col" className="text-end">
                  Ốm
                </th>
                <th scope="col" className="text-end">
                  Không lương
                </th>
                <th scope="col" className="text-end">
                  Nghỉ bù
                </th>
                <th scope="col" className="text-end">
                  150%
                </th>
                <th scope="col" className="text-end">
                  200%
                </th>
                <th scope="col" className="text-end">
                  300%
                </th>
                <th scope="col" className="text-end">
                  Có giờ đêm
                </th>
                <th scope="col">Phép năm</th>
                <th scope="col" className="text-end">
                  Giờ nghỉ bù
                </th>
              </tr>
            </thead>
            <tbody>
              {data.rows.map(r => {
                const action = settlementText(r.settlementDays);
                return (
                  <tr key={r.employeeId} data-testid={`acc-${r.code ?? r.employeeId}`}>
                    <th scope="row" className="fw-normal">
                      <strong>{r.fullName}</strong>
                      <div className="small text-muted">
                        {[r.code, r.departmentName, r.status === 'Đã nghỉ' ? 'Đã nghỉ' : null].filter(Boolean).join(' · ')}
                      </div>
                    </th>
                    <Cell v={r.annualDays} />
                    <Cell v={r.personalDays} />
                    <Cell v={r.sickDays} />
                    <Cell v={r.unpaidDays} />
                    <Cell v={r.compensatoryDays} />
                    <Cell v={r.ot150Hours} />
                    <Cell v={r.ot200Hours} />
                    <Cell v={r.ot300Hours} />
                    <Cell v={r.nightCount} />
                    <td className={Number(r.settlementDays) < 0 ? 'leave-negative' : undefined}>
                      {action ? `${num(r.settlementDays)} ngày · ${action}` : ''}
                    </td>
                    <td className="text-end">{r.settlementCompensatoryHours == null ? '' : num(r.settlementCompensatoryHours)}</td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </section>
    </div>
  );
};

export default AccountingSummary;
