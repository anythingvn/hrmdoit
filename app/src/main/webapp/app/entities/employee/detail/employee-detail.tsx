import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useParams } from 'react-router';
import { Button } from 'react-bootstrap';

import { CONTRACT_TYPE_LABEL, STATUS_LABEL } from 'app/shared/model/employee.model';

import { isoToVn, scheduledBadge } from '../vn-date';
import AccountTab from './account-tab';
import ConfirmDialog from './confirm-dialog';
import AssignmentsTab from './assignments-tab';
import ContractsTab from './contracts-tab';
import PayrollSection from './payroll-section';
import { ICurrentSalary, errorText, initials, useRoles } from './detail-api';
import DocumentsTab from './documents-tab';
import HistoryTab from './history-tab';
import InfoTab, { IEmployeeDetail } from './info-tab';
import TerminateDialog from './terminate-dialog';

import './employee-detail.scss';

type TabId = 'info' | 'contracts' | 'documents' | 'assignments' | 'account' | 'history';

interface IDetail extends IEmployeeDetail {
  currentSalary?: ICurrentSalary | null;
  /** Ghi chú trả về sau khi hoàn tác (vd không mở lại tài khoản đã bỏ gắn). */
  terminationNotice?: string | null;
}

/** Trang hồ sơ nhân viên: thẻ đầu + các tab. */
export const EmployeeDetail = () => {
  const { id } = useParams<'id'>();
  const employeeId = Number(id);
  const { canWrite, canSeePayroll } = useRoles();
  const [employee, setEmployee] = useState<IDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('info');
  const [terminateOpen, setTerminateOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState<'cancel' | 'undo' | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  /** Mở hộp văn bản của HĐ mới nhất khi vào tab Hợp đồng (nút "Tạo văn bản từ mẫu"). */
  const [openDocuments, setOpenDocuments] = useState(false);
  const tabButtonsRef = useRef<Record<string, HTMLButtonElement | null>>({});

  const currentIdRef = useRef(employeeId);
  currentIdRef.current = employeeId;

  const load = () => {
    const requested = employeeId;
    axios
      .get<IDetail>(`api/employees/${requested}`)
      .then(r => {
        // Bỏ phản hồi muộn của nhân viên trước khi đã chuyển sang người khác.
        if (currentIdRef.current !== requested) return;
        setEmployee(r.data);
        setError(null);
      })
      .catch(err => {
        if (currentIdRef.current !== requested) return;
        setError(err?.response?.status === 404 ? 'Không tìm thấy nhân viên' : errorText(err, 'Không tải được hồ sơ'));
      });
  };

  // Đổi nhân viên trên cùng trang: bỏ dữ liệu và trạng thái của người trước (tab, hộp thoại).
  useEffect(() => {
    setEmployee(null);
    setError(null);
    setTab('info');
    setTerminateOpen(false);
    setConfirmAction(null);
    setNotice(null);
    load();
  }, [employeeId]);

  const tabs: [TabId, string][] = [
    ['info', 'Thông tin'],
    ['contracts', 'Hợp đồng & lương'],
    ['documents', 'Hồ sơ scan'],
    ['assignments', 'Lịch sử công tác'],
  ];
  if (canWrite) {
    tabs.push(['account', 'Tài khoản đăng nhập']);
    tabs.push(['history', 'Lịch sử thay đổi']);
  }

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
    tabButtonsRef.current[tabs[next][0]]?.focus();
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
  const badge = scheduledBadge(employee);
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
            {badge && (
              <span className={badge.className} title={`Ngày làm việc cuối: ${isoToVn(employee.scheduledTermination)}`}>
                {badge.text}
              </span>
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
            {canWrite && (
              <span>
                Tài khoản: <strong>{employee.userLogin ?? 'Chưa có'}</strong>
              </span>
            )}
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
            <Button
              type="button"
              className="emp-btn-ghost"
              onClick={() => {
                setOpenDocuments(true);
                setTab('contracts');
              }}
            >
              Tạo văn bản từ mẫu
            </Button>
            {working && !employee.scheduledTermination && (
              <Button type="button" className="emp-btn-accent" onClick={() => setTerminateOpen(true)}>
                Cho nghỉ việc
              </Button>
            )}
            {working && employee.scheduledTermination && (
              <Button type="button" className="emp-btn-accent" onClick={() => setConfirmAction('cancel')}>
                Hủy lịch nghỉ
              </Button>
            )}
            {!working && employee.terminationUndoable && (
              <Button type="button" className="emp-btn-accent" onClick={() => setConfirmAction('undo')}>
                Hoàn tác nghỉ việc
              </Button>
            )}
          </div>
        )}
      </section>

      {notice && (
        <div role="status" className="alert alert-warning">
          {notice}
        </div>
      )}

      <div role="tablist" aria-label="Mục hồ sơ" className="emp-tabs">
        {tabs.map(([tid, label], i) => (
          <button
            key={tid}
            ref={el => {
              tabButtonsRef.current[tid] = el;
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
            openLatestDocuments={openDocuments}
            onDocumentsOpened={() => setOpenDocuments(false)}
          />
        )}
        {tab === 'contracts' && <PayrollSection employeeId={employeeId} visible={canSeePayroll} />}
        {tab === 'documents' && <DocumentsTab employeeId={employeeId} />}
        {tab === 'assignments' && <AssignmentsTab employeeId={employeeId} onChanged={load} />}
        {tab === 'account' && canWrite && <AccountTab employeeId={employeeId} onChanged={load} terminated={!working} />}
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

      {confirmAction === 'cancel' && (
        <ConfirmDialog
          id="cancel-termination"
          title="Hủy lịch nghỉ việc"
          confirmLabel="Xác nhận hủy lịch"
          failText="Không hủy được lịch nghỉ, vui lòng thử lại"
          onConfirm={() => axios.delete(`api/employees/${employeeId}/termination`)}
          onClose={() => setConfirmAction(null)}
          onDone={load}
        >
          <p className="mb-0">
            Hủy lịch nghỉ việc (ngày làm việc cuối {isoToVn(employee.scheduledTermination)}) của {employee.fullName}? Hồ sơ và tài khoản
            đăng nhập giữ nguyên như hiện tại.
          </p>
        </ConfirmDialog>
      )}

      {confirmAction === 'undo' && (
        <ConfirmDialog
          id="undo-termination"
          title="Hoàn tác nghỉ việc"
          confirmLabel="Xác nhận hoàn tác"
          failText="Không hoàn tác được, vui lòng thử lại"
          onConfirm={async () => {
            const r = await axios.post<IDetail>(`api/employees/${employeeId}/termination/undo`);
            setNotice(r?.data?.terminationNotice ?? null);
          }}
          onClose={() => setConfirmAction(null)}
          onDone={load}
        >
          <p className="mb-0">
            Hoàn tác việc cho nghỉ của {employee.fullName}? Trạng thái, ngày kết thúc vị trí làm việc và hợp đồng trở lại như trước khi cho
            nghỉ, tài khoản đăng nhập được mở lại (tài khoản Quản trị chỉ Quản trị mở lại được). Các sửa đổi khác sau đó được giữ nguyên.
            Giai đoạn “quay về” đã xóa khi cho nghỉ (nếu có) không được khôi phục.
          </p>
        </ConfirmDialog>
      )}
    </div>
  );
};

export default EmployeeDetail;
