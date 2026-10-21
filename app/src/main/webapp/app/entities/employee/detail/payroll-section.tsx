import React, { useEffect, useState } from 'react';
import axios from 'axios';

import { PERIOD_STATUS_LABEL, PeriodStatus, money, periodText } from 'app/modules/payroll/payroll-api';

import { errorText } from './detail-api';

interface IEmployeePayroll {
  period: string;
  status: PeriodStatus;
  version: number;
  thucNhan: number;
  tongChiPhi: number;
  thueTncn: number;
  tongBhNld: number;
}

/** Khối "Bảng lương" trên hồ sơ: các kỳ của nhân viên (phiên bản chính thức). Chỉ Kế toán, Quản trị. */
export const PayrollSection = ({ employeeId, visible }: { employeeId: number; visible: boolean }) => {
  const [items, setItems] = useState<IEmployeePayroll[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;
    axios
      .get<IEmployeePayroll[]>(`api/employees/${employeeId}/payroll`)
      .then(r => setItems(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được bảng lương')));
  }, [employeeId, visible]);

  if (!visible) return null;

  return (
    <section className="doit-section mt-4" aria-labelledby="h-payroll">
      <div className="doit-section-head">
        <h2 id="h-payroll">Bảng lương</h2>
      </div>
      {error && (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      )}
      <div className="table-responsive">
        <table className="table doit-table">
          <thead>
            <tr>
              <th scope="col">Kỳ</th>
              <th scope="col">Trạng thái</th>
              <th scope="col" className="text-end">
                Thực nhận
              </th>
              <th scope="col" className="text-end">
                Tổng chi phí
              </th>
              <th scope="col" className="text-end">
                Thuế TNCN
              </th>
              <th scope="col" className="text-end">
                BH người lao động
              </th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 && (
              <tr>
                <td colSpan={6}>Chưa có kỳ lương nào</td>
              </tr>
            )}
            {items.map(i => (
              <tr key={i.period}>
                <td>{periodText(i.period)}</td>
                <td>{PERIOD_STATUS_LABEL[i.status]}</td>
                <td className="text-end">{money(i.thucNhan)}</td>
                <td className="text-end">{money(i.tongChiPhi)}</td>
                <td className="text-end">{money(i.thueTncn)}</td>
                <td className="text-end">{money(i.tongBhNld)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
};

export default PayrollSection;
