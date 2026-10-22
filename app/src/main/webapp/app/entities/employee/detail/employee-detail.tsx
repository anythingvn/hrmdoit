import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useParams, useSearchParams } from 'react-router';
import { Button } from 'react-bootstrap';

import { CONTRACT_TYPE_LABEL, STATUS_LABEL } from 'app/shared/model/employee.model';

import { IArchiveResult, archiveApi } from '../archive/archive-api';
import { instantToVn, isoToVn, scheduledBadge } from '../vn-date';
import ArchiveDialog from './archive-dialog';
import AccountTab from './account-tab';
import ConfirmDialog from './confirm-dialog';
import AssignmentsTab from './assignments-tab';
import ContractsTab from './contracts-tab';
import PayrollSection from './payroll-section';
import { EmployeeReadOnlyContext, ICurrentSalary, errorText, initials, useRoles } from './detail-api';
import DocumentsTab from './documents-tab';
import HistoryTab from './history-tab';
import InfoTab, { IEmployeeDetail } from './info-tab';
import LeaveTab, { leaveTabs, useEmployeeLeave } from './leave-tab';
import RehireDialog from './rehire-dialog';
import TerminateDialog from './terminate-dialog';

import './employee-detail.scss';

type TabId = 'info' | 'contracts' | 'documents' | 'assignments' | 'leave' | 'account' | 'history';

/** Tab mở được qua địa chỉ: các tab mọi vai trò xem hồ sơ đều có. */
const LINKABLE_TABS: TabId[] = ['info', 'contracts', 'documents', 'assignments'];

const tabFromQuery = (value: string | null): TabId => LINKABLE_TABS.find(t => t === value) ?? 'info';

interface IDetail extends IEmployeeDetail {
  currentSalary?: ICurrentSalary | null;
  /** Ghi chú trả về sau khi hoàn tác (vd không mở lại tài khoản đã bỏ gắn). */
  terminationNotice?: string | null;
  /** Có giá trị khi hồ sơ đã lưu trữ (chỉ xem). */
  archivedAt?: string | null;
  archivedBy?: string | null;
  /** Đưa vào lưu trữ được (Đã nghỉ, hoặc hồ sơ tạo nhầm); server chỉ tính cho Quản trị/HR. */
  archivable?: boolean | null;
}

/** Danh sách người và phòng ban còn nhận hồ sơ vừa lưu trữ làm quản lý / trưởng phòng. */
const ArchiveFollowUp = ({ result }: { result: IArchiveResult }) =>
  result.managedEmployees.length + result.headedDepartments.length > 0 ? (
    <div role="status" className="alert alert-warning">
      <p className="mb-2">Hãy đổi quản lý/trưởng phòng cho các hồ sơ sau:</p>
      <ul className="mb-0">
        {result.managedEmployees.map(m => (
          <li key={`e${m.id}`}>
            <Link to={`/nhan-vien/${m.id}`}>{[m.code, m.name].filter(Boolean).join(' – ')}</Link>
          </li>
        ))}
        {result.headedDepartments.map(d => (
          <li key={`d${d.id}`}>Trưởng phòng: {d.name}</li>
        ))}
      </ul>
    </div>
  ) : null;

/** Các tab theo quyền; hồ sơ đã lưu trữ chỉ xem nên không có tab Tài khoản (toàn thao tác sửa). */
const detailTabs = (leave: [TabId, string][], canWrite: boolean, archived: boolean): [TabId, string][] => {
  const tabs: [TabId, string][] = [
    ['info', 'Thông tin'],
    ['contracts', 'Hợp đồng & lương'],
    ['documents', 'Hồ sơ scan'],
    ['assignments', 'Lịch sử công tác'],
    ...leave,
  ];
  if (canWrite && !archived) tabs.push(['account', 'Tài khoản đăng nhập']);
  if (canWrite) tabs.push(['history', 'Lịch sử thay đổi']);
  return tabs;
};

type ArchiveAction = 'archive' | 'rehire' | 'restore';

/** Hộp thoại đưa vào lưu trữ, nhận làm lại, khôi phục. Lưu trữ xong thì báo danh sách người/phòng cần đổi quản lý. */
const ArchiveActionDialogs = ({
  action,
  employee,
  employeeId,
  onClose,
  onArchived,
  onChanged,
}: {
  action: ArchiveAction | null;
  employee: IDetail;
  employeeId: number;
  onClose: () => void;
  onArchived: (result: IArchiveResult | null) => void;
  onChanged: () => void;
}) => {
  if (action === 'archive') {
    return (
      <ArchiveDialog
        employeeName={employee.fullName}
        onConfirm={note => archiveApi.archive(employeeId, note)}
        onClose={onClose}
        onDone={result => {
          onArchived(result);
          onChanged();
        }}
      />
    );
  }
  if (action === 'rehire') {
    return <RehireDialog employee={{ ...employee, id: employeeId }} onClose={onClose} onDone={onChanged} />;
  }
  if (action === 'restore') {
    return (
      <ConfirmDialog
        id="restore-employee"
        title="Khôi phục hồ sơ"
        confirmLabel="Xác nhận khôi phục"
        failText="Không khôi phục được, vui lòng thử lại"
        onConfirm={() => archiveApi.restore(employeeId)}
        onClose={onClose}
        onDone={() => {
          onArchived(null);
          onChanged();
        }}
      >
        <p className="mb-0">
          Đưa hồ sơ của {employee.fullName} về danh sách nhân viên? Trạng thái và dữ liệu giữ nguyên; tài khoản đăng nhập vẫn khóa.
        </p>
      </ConfirmDialog>
    );
  }
  return null;
};

type HeaderAction = 'documents' | 'terminate' | 'cancel' | 'undo' | 'rehire' | 'archive';

/** Nút thao tác ở thẻ đầu hồ sơ (Quản trị/HR, hồ sơ chưa lưu trữ). */
const HeaderActions = ({
  employeeId,
  employee,
  onAction,
}: {
  employeeId: number;
  employee: IDetail;
  onAction: (action: HeaderAction) => void;
}) => {
  const working = employee.status !== 'DA_NGHI';
  return (
    <div className="d-flex flex-wrap gap-2">
      <Link to={`/nhan-vien/${employeeId}/sua`} className="btn emp-btn-ghost">
        Sửa hồ sơ
      </Link>
      <Button type="button" className="emp-btn-ghost" onClick={() => onAction('documents')}>
        Tạo văn bản từ mẫu
      </Button>
      {working && !employee.scheduledTermination && (
        <Button type="button" className="emp-btn-accent" onClick={() => onAction('terminate')}>
          Cho nghỉ việc
        </Button>
      )}
      {working && employee.scheduledTermination && (
        <Button type="button" className="emp-btn-accent" onClick={() => onAction('cancel')}>
          Hủy lịch nghỉ
        </Button>
      )}
      {!working && employee.terminationUndoable && (
        <Button type="button" className="emp-btn-accent" onClick={() => onAction('undo')}>
          Hoàn tác nghỉ việc
        </Button>
      )}
      {!working && (
        <Button type="button" className="emp-btn-accent" onClick={() => onAction('rehire')}>
          Nhận làm lại
        </Button>
      )}
      {employee.archivable && (
        <Button type="button" className="emp-btn-ghost" onClick={() => onAction('archive')}>
          Đưa vào lưu trữ
        </Button>
      )}
    </div>
  );
};

/** Băng "Hồ sơ đã lưu trữ (ngày, người)"; nút Khôi phục khi có quyền. */
const ArchivedBanner = ({ employee, onRestore }: { employee: IDetail; onRestore?: () => void }) => (
  <div role="status" className="alert alert-secondary d-flex flex-wrap align-items-center justify-content-between gap-2 mb-0">
    <span>Hồ sơ đã lưu trữ ({[instantToVn(employee.archivedAt), employee.archivedBy].filter(Boolean).join(', ')})</span>
    {onRestore && (
      <Button type="button" variant="primary" onClick={onRestore}>
        Khôi phục
      </Button>
    )}
  </div>
);

/** Trang hồ sơ nhân viên: thẻ đầu + các tab. */
export const EmployeeDetail = () => {
  const { id } = useParams<'id'>();
  const employeeId = Number(id);
  const { canWrite, canSeePayroll } = useRoles();

  const [employee, setEmployee] = useState<IDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  // ?tab=contracts mở thẳng tab Hợp đồng & lương (vd từ nút "Thêm điều chỉnh lương" ở Tổng quan).
  const [searchParams] = useSearchParams();
  const initialTab = tabFromQuery(searchParams.get('tab'));
  const [tab, setTab] = useState<TabId>(initialTab);
  const [terminateOpen, setTerminateOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState<'cancel' | 'undo' | null>(null);
  const [archiveAction, setArchiveAction] = useState<ArchiveAction | null>(null);
  const [archiveResult, setArchiveResult] = useState<IArchiveResult | null>(null);
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
    setTab(initialTab);
    setTerminateOpen(false);
    setConfirmAction(null);
    setArchiveAction(null);
    setArchiveResult(null);
    setNotice(null);
    load();
  }, [employeeId]);

  const leave = useEmployeeLeave(employee, employeeId, canWrite);

  const archived = !!employee?.archivedAt;
  const tabs = detailTabs(leaveTabs<TabId>(leave, 'leave'), canWrite, archived);

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

  const onHeaderAction = (action: HeaderAction) => {
    if (action === 'documents') {
      setOpenDocuments(true);
      setTab('contracts');
    } else if (action === 'terminate') setTerminateOpen(true);
    else if (action === 'rehire' || action === 'archive') setArchiveAction(action);
    else setConfirmAction(action);
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
        {canWrite && !archived && <HeaderActions employeeId={employeeId} employee={employee} onAction={onHeaderAction} />}
      </section>

      {archived && <ArchivedBanner employee={employee} onRestore={canWrite ? () => setArchiveAction('restore') : undefined} />}

      {archiveResult && <ArchiveFollowUp result={archiveResult} />}

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

      <EmployeeReadOnlyContext value={archived}>
        <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} tabIndex={0}>
          {tab === 'info' && <InfoTab key={employee.id} employee={employee} />}
          {tab === 'contracts' && (
            <ContractsTab
              employeeId={employeeId}
              dependants={employee.dependants ?? 0}
              insured={!employee.insuranceExemption}
              currentSalary={employee.currentSalary}
              onChanged={load}
              openLatestDocuments={openDocuments}
              onDocumentsOpened={() => setOpenDocuments(false)}
            />
          )}
          {tab === 'contracts' && <PayrollSection employeeId={employeeId} visible={canSeePayroll} />}
          {tab === 'documents' && <DocumentsTab employeeId={employeeId} />}
          {tab === 'assignments' && <AssignmentsTab employeeId={employeeId} onChanged={load} />}
          <LeaveTab data={leave} active={tab === 'leave'} />
          {tab === 'account' && canWrite && !archived && <AccountTab employeeId={employeeId} onChanged={load} terminated={!working} />}
          {tab === 'history' && canWrite && <HistoryTab employeeId={employeeId} />}
        </div>
      </EmployeeReadOnlyContext>

      {terminateOpen && (
        <TerminateDialog
          employeeId={employeeId}
          employeeName={employee.fullName}
          isOpen
          toggle={() => setTerminateOpen(false)}
          onDone={load}
        />
      )}

      <ArchiveActionDialogs
        action={archiveAction}
        employee={employee}
        employeeId={employeeId}
        onClose={() => setArchiveAction(null)}
        onArchived={result => {
          setArchiveResult(result);
          // Hồ sơ vừa lưu trữ không còn tab Tài khoản.
          if (result) setTab(t => (t === 'account' ? 'info' : t));
        }}
        onChanged={load}
      />

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
