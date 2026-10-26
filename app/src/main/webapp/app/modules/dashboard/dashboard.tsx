import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { errorText, useRoles } from 'app/entities/employee/detail/detail-api';
import { PERIOD_STATUS_LABEL, PeriodStatus, money, periodText } from 'app/modules/payroll/payroll-api';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import { PageHeader, SectionCard, StatCard } from 'app/shared/ui';

import MovementChart, { IMovement } from './movement-chart';
import PendingApprovals from './pending-approvals';
import ReminderList from './reminder-list';
import TodayAttendance from './today-attendance';
import './dashboard.scss';

interface IDashboard {
  headcount: number;
  onProbation: number;
  contractsExpiring30: number;
  /** Chỉ Quản trị, Kế toán nhận được. */
  payrollCost?: { period: string; status: string; totalCost: number };
  movement: IMovement[];
}

/** "Chào buổi sáng" (trước 11h), "buổi chiều" (11h–18h), "buổi tối". */
export const greeting = (hour: number) => (hour < 11 ? 'Chào buổi sáng' : hour < 18 ? 'Chào buổi chiều' : 'Chào buổi tối');

const WEEKDAYS = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

/** "Thứ Hai, 19/10/2026". */
export const dateLine = (d: Date) =>
  `${WEEKDAYS[d.getDay()]}, ${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;

/** Tên gọi trong lời chào: chữ cuối của họ tên (tên riêng tiếng Việt), không có tên thì dùng tên đăng nhập. */
const callName = (account: { firstName?: string; lastName?: string; login?: string }) => {
  const parts = [account.firstName, account.lastName].filter(Boolean).join(' ').trim().split(/\s+/).filter(Boolean);
  return parts.length ? parts[parts.length - 1] : (account.login ?? '');
};

const SearchIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </svg>
);

/** Trang Tổng quan theo canvas: lời chào, chỉ số chính, nhắc việc, đơn chờ duyệt, biến động trong năm, chấm công hôm nay. */
export const Dashboard = () => {
  const { authorities } = useRoles();
  const account = useAppSelector(state => state.authentication.account);
  const navigate = useNavigate();
  const [data, setData] = useState<IDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const [reminderCount, setReminderCount] = useState<number | null>(null);
  const [pendingCount, setPendingCount] = useState<number | null>(null);
  const [attendanceKey, setAttendanceKey] = useState(0);

  const canManage = hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]);
  const approver = !!account?.isApprover || !!account?.entersAttendance;
  const [now] = useState(() => new Date());

  useEffect(() => {
    axios
      .get<IDashboard>('api/dashboard')
      .then(r => setData(r.data))
      .catch(err => setError(errorText(err, 'Không tải được số liệu tổng quan')));
  }, []);

  // Chỉ nói số việc khi đã tải đủ (nhắc việc, và đơn chờ duyệt nếu là người duyệt).
  const todo = reminderCount !== null && (!approver || pendingCount !== null) ? reminderCount + (approver ? (pendingCount ?? 0) : 0) : null;
  const todoText = todo === null ? '' : todo === 0 ? ' · Không có việc nào cần bạn xử lý' : ` · ${todo} việc cần bạn xử lý`;

  const search = (e: React.FormEvent) => {
    e.preventDefault();
    const text = q.trim();
    navigate(text ? `/nhan-vien?q=${encodeURIComponent(text)}` : '/nhan-vien');
  };

  const year = data?.movement.length ? data.movement[data.movement.length - 1].month.slice(0, 4) : String(now.getFullYear());
  const yearMovement = (data?.movement ?? []).filter(m => m.month.startsWith(year));
  const movementTitle = `Biến động nhân sự ${year}`;

  return (
    <div className="doit-page">
      <PageHeader
        title={`${greeting(now.getHours())}, ${callName(account ?? {})}`}
        subtitle={`${dateLine(now)}${todoText}`}
        actions={
          <>
            <form role="search" onSubmit={search} className="d-flex">
              <label className="dashboard-search" htmlFor="dashboard-search">
                <SearchIcon />
                <span className="visually-hidden">Tìm nhân viên</span>
                <input
                  id="dashboard-search"
                  type="search"
                  placeholder="Tìm nhân viên theo tên, mã..."
                  value={q}
                  onChange={e => setQ(e.target.value)}
                />
              </label>
            </form>
            {canManage && (
              <Link to="/nhan-vien/moi" className="btn btn-primary">
                + Thêm nhân viên
              </Link>
            )}
          </>
        }
      />
      {error && <Alert variant="danger">{error}</Alert>}
      {data && (
        <section className="doit-stats" aria-label="Chỉ số chính">
          <StatCard label="Tổng nhân sự" value={data.headcount} />
          <StatCard label="Đang thử việc" value={data.onProbation} />
          <StatCard label="HĐ hết hạn 30 ngày tới" value={data.contractsExpiring30} />
          {data.payrollCost && (
            <StatCard
              label={`Chi phí lương kỳ ${periodText(data.payrollCost.period)}`}
              value={money(data.payrollCost.totalCost)}
              sub={PERIOD_STATUS_LABEL[data.payrollCost.status as PeriodStatus] ?? undefined}
            />
          )}
        </section>
      )}
      <div className="doit-grid-2">
        <ReminderList canAct={canManage} onCount={setReminderCount} />
        {approver && <PendingApprovals onCount={setPendingCount} onDecided={() => setAttendanceKey(k => k + 1)} />}
        {data && (
          <SectionCard title={movementTitle} titleId="movement-title" actions={<Link to="/bao-cao">Báo cáo nhân sự</Link>}>
            <MovementChart data={yearMovement} title={movementTitle} />
          </SectionCard>
        )}
        {!!account?.entersAttendance && <TodayAttendance refreshKey={attendanceKey} />}
      </div>
    </div>
  );
};

export default Dashboard;
