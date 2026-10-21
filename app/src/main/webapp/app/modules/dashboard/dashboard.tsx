import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Card } from 'react-bootstrap';

import { AUTHORITIES } from 'app/config/constants';
import { errorText, useRoles } from 'app/entities/employee/detail/detail-api';
import { money, periodText } from 'app/modules/payroll/payroll-api';
import { hasAnyAuthority } from 'app/shared/auth/private-route';

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

const Tile = ({ label, value }: { label: string; value: React.ReactNode }) => (
  <Card body>
    <div className="text-muted small">{label}</div>
    <div className="tile-value">{value}</div>
  </Card>
);

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
    <div>
      <h1 className="h3 mb-3">Tổng quan</h1>
      {error && <Alert variant="danger">{error}</Alert>}
      {data && (
        <div className="dashboard-tiles mb-4">
          <Tile label="Nhân viên đang làm" value={data.headcount} />
          <Tile label="Đang thử việc" value={data.onProbation} />
          <Tile label="HĐ hết hạn trong 30 ngày" value={data.contractsExpiring30} />
          {data.payrollCost && (
            <Tile label={`Chi phí lương kỳ ${periodText(data.payrollCost.period)}`} value={money(data.payrollCost.totalCost)} />
          )}
        </div>
      )}
      <ReminderList canAct={hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR])} />
      {data && (
        <section aria-labelledby="movement-title">
          <h2 className="h5" id="movement-title">
            Biến động nhân sự 12 tháng
          </h2>
          <MovementChart data={data.movement} />
        </section>
      )}
    </div>
  );
};

export default Dashboard;
