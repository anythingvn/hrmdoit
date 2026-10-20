import React, { useEffect, useState } from 'react';
import axios from 'axios';

import { CONTRACT_TYPE_LABEL, STATUS_LABEL } from 'app/shared/model/employee.model';
import { errorText, initials } from 'app/entities/employee/detail/detail-api';
import InfoTab, { IEmployeeDetail } from 'app/entities/employee/detail/info-tab';
import { isoToVn } from 'app/entities/employee/vn-date';

import 'app/entities/employee/detail/employee-detail.scss';

/** "Hồ sơ của tôi": hồ sơ gắn với tài khoản đang đăng nhập, chỉ đọc. */
export const MyProfile = () => {
  const [employee, setEmployee] = useState<IEmployeeDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    axios
      .get<IEmployeeDetail>('api/account/employee')
      .then(r => setEmployee(r.data))
      .catch(err =>
        setError(
          err?.response?.status === 404
            ? 'Tài khoản của bạn chưa được gắn với hồ sơ nhân viên. Hãy liên hệ phòng Nhân sự.'
            : errorText(err, 'Không tải được hồ sơ'),
        ),
      );
  }, []);

  if (error) {
    return (
      <div role="alert" className="alert alert-warning">
        {error}
      </div>
    );
  }
  if (!employee) return <p>Đang tải...</p>;

  const working = employee.status !== 'DA_NGHI';
  const subtitle = [employee.position, employee.department?.name, employee.code ? `Mã ${employee.code}` : null].filter(Boolean).join(' · ');

  return (
    <div className="employee-detail d-flex flex-column gap-4">
      <section className="emp-header" aria-labelledby="emp-name">
        <div className="emp-avatar" aria-hidden="true">
          {initials(employee.fullName)}
        </div>
        <div className="emp-main">
          <div className="d-flex flex-wrap gap-2 align-items-center">
            <h1 id="emp-name">{employee.fullName}</h1>
            {employee.status && (
              <span className={`doit-badge ${working ? 'doit-badge-ok' : 'doit-badge-old'}`}>{STATUS_LABEL[employee.status]}</span>
            )}
            {employee.currentContractType && (
              <span className="doit-badge doit-badge-old">{CONTRACT_TYPE_LABEL[employee.currentContractType]}</span>
            )}
          </div>
          {subtitle && <span className="emp-subtitle">{subtitle}</span>}
          <div className="emp-meta">
            <span>
              Ngày vào: <strong>{isoToVn(employee.joinDate) || '—'}</strong>
            </span>
            <span>
              Quản lý: <strong>{employee.manager?.fullName ?? '—'}</strong>
            </span>
          </div>
        </div>
      </section>
      <InfoTab employee={employee} self />
    </div>
  );
};

export default MyProfile;
