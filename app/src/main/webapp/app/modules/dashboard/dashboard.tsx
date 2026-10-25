import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert } from 'react-bootstrap';

import { AUTHORITIES } from 'app/config/constants';
import { errorText, useRoles } from 'app/entities/employee/detail/detail-api';
import { money, periodText } from 'app/modules/payroll/payroll-api';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import { PageHeader, SectionCard, StatCard } from 'app/shared/ui';

import MovementChart, { IMovement } from './movement-chart';
import ReminderList from './reminder-list';
import './dashboard.scss';

interface IDashboard {
  headcount: number;
  onProbation: number;
  contractsExpiring30: number;
  /** Chỉ Quản trị, Kế toán nhận được. */
  payrollCost?: { period: string; status: string; totalCost: number };
  movement: IMovement[];
}

/** Trang Tổng quan: chỉ số chính, nhắc việc sắp tới, biến động 12 tháng. */
export const Dashboard = () => {
  const { authorities } = useRoles();
  const [data, setData] = useState<IDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    axios
      .get<IDashboard>('api/dashboard')
      .then(r => setData(r.data))
      .catch(err => setError(errorText(err, 'Không tải được số liệu tổng quan')));
  }, []);

  return (
    <div className="doit-page">
      <PageHeader title="Tổng quan" />
      {error && <Alert variant="danger">{error}</Alert>}
      {data && (
        <section className="doit-stats" aria-label="Chỉ số chính">
          <StatCard label="Nhân viên đang làm" value={data.headcount} />
          <StatCard label="Đang thử việc" value={data.onProbation} />
          <StatCard label="HĐ hết hạn trong 30 ngày" value={data.contractsExpiring30} />
          {data.payrollCost && (
            <StatCard label={`Chi phí lương kỳ ${periodText(data.payrollCost.period)}`} value={money(data.payrollCost.totalCost)} />
          )}
        </section>
      )}
      <div className="doit-grid-2">
        <ReminderList canAct={hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR])} />
        {data && (
          <SectionCard title="Biến động nhân sự 12 tháng" titleId="movement-title">
            <MovementChart data={data.movement} />
          </SectionCard>
        )}
      </div>
    </div>
  );
};

export default Dashboard;
