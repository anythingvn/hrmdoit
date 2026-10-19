import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useParams } from 'react-router-dom';
import { Button } from 'reactstrap';

import { CONTRACT_TYPE_LABEL, STATUS_LABEL } from 'app/shared/model/employee.model';

import { isoToVn } from '../vn-date';
import AssignmentsTab from './assignments-tab';
import ContractsTab from './contracts-tab';
import { ICurrentSalary, errorText, initials, useRoles } from './detail-api';
import DocumentsTab from './documents-tab';
import HistoryTab from './history-tab';
import InfoTab, { IEmployeeDetail } from './info-tab';
import TerminateDialog from './terminate-dialog';

import './employee-detail.scss';

type TabId = 'info' | 'contracts' | 'documents' | 'assignments' | 'history';

interface IDetail extends IEmployeeDetail {
  currentSalary?: ICurrentSalary | null;
}

/** Trang hồ sơ nhân viên: thẻ đầu + các tab. */
export const EmployeeDetail = () => {
  const { id } = useParams<'id'>();
  const employeeId = Number(id);
  const { canWrite } = useRoles();
  const [employee, setEmployee] = useState<IDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('info');
  const [terminateOpen, setTerminateOpen] = useState(false);
  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({});

  const currentId = useRef(employeeId);
  currentId.current = employeeId;

  const load = () => {
    const requested = employeeId;
    axios
      .get<IDetail>(`api/employees/${requested}`)
      .then(r => {
        // Bỏ phản hồi muộn của nhân viên trước khi đã chuyển sang người khác.
        if (currentId.current !== requested) return;
        setEmployee(r.data);
        setError(null);
      })
      .catch(err => {
        if (currentId.current !== requested) return;
        setError(err?.response?.status === 404 ? 'Không tìm thấy nhân viên' : errorText(err, 'Không tải được hồ sơ'));
      });
  };

  // Đổi nhân viên trên cùng trang: bỏ dữ liệu và trạng thái của người trước (tab, hộp thoại).
  useEffect(() => {
    setEmployee(null);
    setError(null);
    setTab('info');
    setTerminateOpen(false);
    load();
  }, [employeeId]);

  const tabs: Array<[TabId, string]> = [
    ['info', 'Thông tin'],
    ['contracts', 'Hợp đồng & lương'],
    ['documents', 'Hồ sơ scan'],
    ['assignments', 'Lịch sử công tác'],
  ];
  if (canWrite) tabs.push(['history', 'Lịch sử thay đổi']);

  // Mũi tên trái/phải, Home/End chuyển tab (mẫu tablist của WAI-ARIA).
  const onTabKey = (e: React.KeyboardEvent, index: number) => {
    let next = -1;
    if (e.key === 'ArrowRight') next = (index + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = tabs.length - 1;
    if (next < 0) return;
    e.preventDefault();
    setTab(tabs[next][0]);
    tabRefs.current[tabs[next][0]]?.focus();
  };

  if (error) {
    return (
      <div role="alert" className="alert alert-danger">
        {error}
      </div>
    );
  }
  if (!employee) return <p>Đang tải...</p>;

  const working = employee.status !== 'DA_NGHI';
  const subtitle = [employee.position, employee.department?.name, employee.code ? `Mã ${employee.code}` : null].filter(Boolean).join(' · ');

  return (
    <div className="employee-detail d-flex flex-column gap-4">
      <nav aria-label="Đường dẫn" className="small text-muted">
        <Link to="/nhan-vien">Nhân viên</Link> / {employee.fullName}
      </nav>

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
            <span>
              Nơi làm việc: <strong>{employee.currentSiteName ?? '—'}</strong>
            </span>
            {!working && (
              <span>
                Ngày nghỉ việc: <strong>{isoToVn(employee.terminationDate) || '—'}</strong>
              </span>
            )}
          </div>
        </div>
        {canWrite && (
          <div className="d-flex flex-wrap gap-2">
            <Link to={`/nhan-vien/${employeeId}/sua`} className="btn emp-btn-ghost">
              Sửa hồ sơ
            </Link>
            {working && (
              <Button type="button" className="emp-btn-accent" onClick={() => setTerminateOpen(true)}>
                Cho nghỉ việc
              </Button>
            )}
          </div>
        )}
      </section>

      <div role="tablist" aria-label="Mục hồ sơ" className="emp-tabs">
        {tabs.map(([tid, label], i) => (
          <button
            key={tid}
            ref={el => {
              tabRefs.current[tid] = el;
            }}
            type="button"
            role="tab"
            id={`tab-${tid}`}
            aria-selected={tab === tid}
            aria-controls={`panel-${tid}`}
            tabIndex={tab === tid ? 0 : -1}
            className={`emp-tab ${tab === tid ? 'emp-tab-on' : ''}`}
            onClick={() => setTab(tid)}
            onKeyDown={e => onTabKey(e, i)}
          >
            {label}
          </button>
        ))}
      </div>

      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} tabIndex={0}>
        {tab === 'info' && <InfoTab key={employee.id} employee={employee} />}
        {tab === 'contracts' && (
          <ContractsTab
            employeeId={employeeId}
            dependants={employee.dependants ?? 0}
            currentSalary={employee.currentSalary}
            onChanged={load}
          />
        )}
        {tab === 'documents' && <DocumentsTab employeeId={employeeId} />}
        {tab === 'assignments' && <AssignmentsTab employeeId={employeeId} onChanged={load} />}
        {tab === 'history' && canWrite && <HistoryTab employeeId={employeeId} />}
      </div>

      {terminateOpen && (
        <TerminateDialog
          employeeId={employeeId}
          employeeName={employee.fullName}
          isOpen
          toggle={() => setTerminateOpen(false)}
          onDone={load}
        />
      )}
    </div>
  );
};

export default EmployeeDetail;
