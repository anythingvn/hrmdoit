import 'app/modules/leave/leave.scss';

import React, { useEffect, useRef, useState } from 'react';
import { Table } from 'react-bootstrap';

import { useAppSelector } from 'app/config/store';
import { IEmployeeLeave, leaveApi } from 'app/modules/leave/leave-api';
import { LEAVE_STATUS_LABEL, LEAVE_TYPE_LABEL, PERSONAL_REASONS, num } from 'app/modules/leave/leave-labels';
import { BalanceCards, LedgerTable } from 'app/modules/leave/leave-shared';
import { StatusChip, rangeText } from 'app/modules/leave/status-chip';

/**
 * Dữ liệu tab "Nghỉ phép" của hồ sơ đang xem, hoặc null khi không có tab: chỉ người NHAN_VIEN; chỉ hỏi server khi người xem là HR / Quản
 * trị ({@code canWrite}) hoặc có cờ isApprover (có thể là trưởng nhóm / trưởng phòng). Server kiểm tra quyền thật: 403 thì không có tab.
 */
export const useEmployeeLeave = (
  employee: { id?: number | null; personType?: string | null } | null,
  employeeId: number,
  canWrite: boolean,
) => {
  const isApprover: boolean = useAppSelector(state => !!state.authentication.account?.isApprover);
  const [leave, setLeave] = useState<IEmployeeLeave | null>(null);
  const currentRef = useRef(employeeId);
  currentRef.current = employeeId;
  const candidate = !!employee && employee.id === employeeId && employee.personType === 'NHAN_VIEN' && (canWrite || isApprover);

  useEffect(() => {
    setLeave(null);
    if (!candidate) return;
    const requested = employeeId;
    leaveApi
      .employeeLeave(requested)
      .then(d => currentRef.current === requested && setLeave(d))
      .catch(() => currentRef.current === requested && setLeave(null));
  }, [candidate, employeeId]);

  return leave;
};

/**
 * Tab "Nghỉ phép" trên hồ sơ (HR, Quản trị, trưởng nhóm, trưởng phòng của người đó): số dư, các đơn nghỉ, sổ phép. Lý do nghỉ chỉ có khi
 * server cho xem (người duyệt của đơn, HR, Quản trị).
 */
/** Mục tab cho danh sách tab của hồ sơ: có dữ liệu thì có tab. */
export const leaveTabs = <T extends string>(data: IEmployeeLeave | null, id: T): [T, string][] => (data ? [[id, 'Nghỉ phép']] : []);

export const LeaveTab = ({ data, active = true }: { data: IEmployeeLeave | null; active?: boolean }) =>
  active &&
  data && (
    <div className="leave-page">
      <BalanceCards balance={data.balance} />

      <section className="leave-section" aria-labelledby="emp-leave-requests">
        <h2 id="emp-leave-requests">Đơn nghỉ</h2>
        {data.requests.length === 0 ? (
          <p className="mb-0">Chưa có đơn nghỉ nào.</p>
        ) : (
          <Table responsive className="align-middle">
            <thead>
              <tr>
                <th scope="col">Loại</th>
                <th scope="col">Thời gian</th>
                <th scope="col" className="text-end">
                  Số ngày
                </th>
                <th scope="col">Trạng thái</th>
                <th scope="col">Người duyệt</th>
              </tr>
            </thead>
            <tbody>
              {data.requests.map(r => (
                <tr key={r.id}>
                  <td>
                    {LEAVE_TYPE_LABEL[r.type]}
                    {r.personalReason && (
                      <div className="small text-muted">{PERSONAL_REASONS.find(p => p.value === r.personalReason)?.label}</div>
                    )}
                    {r.reasonText && <div className="small text-muted">Ghi chú: {r.reasonText}</div>}
                  </td>
                  <td>{rangeText(r)}</td>
                  <td className="text-end">{num(r.days)}</td>
                  <td>
                    <StatusChip status={r.status} label={LEAVE_STATUS_LABEL[r.status]} />
                    {r.systemNote && <div className="small text-muted">{r.systemNote}</div>}
                  </td>
                  <td>{r.approverName ?? r.decidedBy ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </section>

      <section className="leave-section" aria-labelledby="emp-leave-ledger">
        <h2 id="emp-leave-ledger">Sổ phép</h2>
        <LedgerTable ledger={data.ledger} />
      </section>
    </div>
  );

export default LeaveTab;
