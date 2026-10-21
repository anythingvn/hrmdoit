import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button } from 'react-bootstrap';

import { CONTRACT_TYPE_LABEL } from 'app/shared/model/employee.model';

import { formatMoney } from '../money';
import { isoToVn } from '../vn-date';
import AllowanceDialog from './allowance-dialog';
import ContractDialog from './contract-dialog';
import ContractDocumentsDialog from './contract-documents-dialog';
import {
  CONTRACT_STATUS_LABEL,
  IAllowance,
  IContract,
  ICurrentSalary,
  ISalaryChange,
  SalaryBasis,
  errorText,
  useRoles,
} from './detail-api';
import SalaryChangeDialog from './salary-change-dialog';

interface IContractsTabProps {
  employeeId: number;
  dependants: number;
  /** false = không thuộc diện đóng BHXH bắt buộc. */
  insured?: boolean;
  currentSalary?: ICurrentSalary | null;
  /** Gọi sau khi lưu để trang tải lại thẻ đầu (loại HĐ, lương hiện tại). */
  onChanged: () => void;
  /** Mở ngay hộp văn bản của HĐ có ngày bắt đầu mới nhất (nút "Tạo văn bản từ mẫu" trên thẻ đầu). */
  openLatestDocuments?: boolean;
  onDocumentsOpened?: () => void;
}

type Dialog =
  | { kind: 'contract'; item: IContract | null }
  | { kind: 'salary'; item: ISalaryChange | null }
  | { kind: 'allowance'; item: IAllowance | null }
  | { kind: 'documents'; item: IContract };

const SOON_DAYS = 30;

const range = (from?: string | null, to?: string | null) => (to ? `${isoToVn(from)} – ${isoToVn(to)}` : `từ ${isoToVn(from)}`);

const money = (amount?: number | null, basis?: SalaryBasis | null, gross?: number | null) => {
  if (amount == null) return '—';
  const base = `${formatMoney(amount)}${basis ? ` (${basis === 'NET' ? 'Net' : 'Gross'})` : ''}`;
  return basis === 'NET' && gross != null ? `${base} · Gross ${formatMoney(gross)}` : base;
};

/** Nhãn trạng thái; HĐ đã ký sắp hết hạn trong 30 ngày thì báo "Sắp hết hạn". */
const contractStatus = (c: IContract): { text: string; tone: 'ok' | 'warn' | 'old' } => {
  if (c.status === 'DA_KY') {
    if (c.endDate) {
      const days = (new Date(`${c.endDate}T00:00:00`).getTime() - Date.now()) / 86400000;
      if (days < -1) return { text: 'Đã hết hạn', tone: 'warn' };
      if (days <= SOON_DAYS) return { text: 'Sắp hết hạn', tone: 'warn' };
    }
    return { text: 'Hiệu lực', tone: 'ok' };
  }
  return { text: c.status ? CONTRACT_STATUS_LABEL[c.status] : '—', tone: c.status === 'NHAP' ? 'warn' : 'old' };
};

export const ContractsTab = ({
  employeeId,
  dependants,
  insured = true,
  currentSalary,
  onChanged,
  openLatestDocuments,
  onDocumentsOpened,
}: IContractsTabProps) => {
  const { canWrite } = useRoles();
  const [contracts, setContracts] = useState<IContract[]>([]);
  const [salaryChanges, setSalaryChanges] = useState<ISalaryChange[]>([]);
  const [allowances, setAllowances] = useState<IAllowance[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog | null>(null);

  const load = () => {
    setLoadError(null);
    Promise.all([
      axios.get<IContract[]>(`api/employees/${employeeId}/contracts`),
      axios.get<ISalaryChange[]>(`api/employees/${employeeId}/salary-changes`),
      axios.get<IAllowance[]>(`api/employees/${employeeId}/allowances`),
    ])
      .then(([c, s, a]) => {
        setContracts(c.data ?? []);
        setSalaryChanges(s.data ?? []);
        setAllowances(a.data ?? []);
      })
      .catch(err => setLoadError(errorText(err, 'Không tải được hợp đồng và lương')));
  };

  useEffect(load, [employeeId]);

  useEffect(() => {
    if (!openLatestDocuments || contracts.length === 0) return;
    const latest = [...contracts].sort((a, b) => (b.startDate ?? '').localeCompare(a.startDate ?? ''))[0];
    setDialog({ kind: 'documents', item: latest });
    onDocumentsOpened?.();
  }, [openLatestDocuments, contracts]);

  const saved = () => {
    load();
    onChanged();
  };
  const close = () => setDialog(null);

  const editButton = (label: string, onClick: () => void) =>
    canWrite && (
      <Button type="button" variant="link" className="doit-row-action" onClick={onClick} aria-label={label}>
        Sửa
      </Button>
    );

  return (
    <div className="d-flex flex-column gap-4">
      {loadError && (
        <div role="alert" className="alert alert-danger">
          {loadError}
        </div>
      )}

      <section className="doit-section" aria-labelledby="h-contracts">
        <div className="doit-section-head">
          <h2 id="h-contracts">Hợp đồng</h2>
          {canWrite && (
            <Button type="button" variant="primary" onClick={() => setDialog({ kind: 'contract', item: null })}>
              + Thêm hợp đồng
            </Button>
          )}
        </div>
        <p className="mb-0">
          Lương hiện tại:{' '}
          <strong>{currentSalary ? money(currentSalary.amount, currentSalary.basis, currentSalary.grossAmount) : 'Chưa có'}</strong>
          {currentSalary?.effectiveDate && <span className="text-muted"> · từ {isoToVn(currentSalary.effectiveDate)}</span>}
        </p>
        <div className="table-responsive">
          <table className="table doit-table">
            <thead>
              <tr>
                <th scope="col">Loại / số</th>
                <th scope="col">Hiệu lực</th>
                <th scope="col">Lương</th>
                <th scope="col">Trạng thái</th>
                {canWrite && <th scope="col">Thao tác</th>}
              </tr>
            </thead>
            <tbody>
              {contracts.length === 0 && (
                <tr>
                  <td colSpan={canWrite ? 5 : 4}>Chưa có hợp đồng</td>
                </tr>
              )}
              {contracts.map(c => {
                const st = contractStatus(c);
                const name = c.type ? CONTRACT_TYPE_LABEL[c.type] : 'Hợp đồng';
                return (
                  <tr key={c.id}>
                    <td>
                      <span className="fw-semibold">{name}</span>
                      {c.contractNo && <div className="text-muted">Số {c.contractNo}</div>}
                    </td>
                    <td className="text-nowrap">{range(c.startDate, c.endDate)}</td>
                    <td>{money(c.salary, c.salaryBasis, c.grossAmount)}</td>
                    <td>
                      <span className={`doit-badge doit-badge-${st.tone}`}>{st.text}</span>
                    </td>
                    {canWrite && (
                      <td className="text-nowrap">
                        {editButton(`Sửa ${name} ${c.contractNo ?? ''}`.trim(), () => setDialog({ kind: 'contract', item: c }))}
                        <Button
                          type="button"
                          variant="link"
                          className="doit-row-action"
                          onClick={() => setDialog({ kind: 'documents', item: c })}
                          aria-label={`Văn bản ${name} ${c.contractNo ?? ''}`.trim()}
                        >
                          Văn bản
                        </Button>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="doit-section" aria-labelledby="h-salary">
        <div className="doit-section-head">
          <h2 id="h-salary">Điều chỉnh lương</h2>
          {canWrite && (
            <Button type="button" variant="outline-primary" onClick={() => setDialog({ kind: 'salary', item: null })}>
              + Điều chỉnh lương
            </Button>
          )}
        </div>
        <div className="table-responsive">
          <table className="table doit-table">
            <thead>
              <tr>
                <th scope="col">Ngày hiệu lực</th>
                <th scope="col">Mức lương</th>
                <th scope="col">Ghi chú</th>
                {canWrite && <th scope="col">Thao tác</th>}
              </tr>
            </thead>
            <tbody>
              {salaryChanges.length === 0 && (
                <tr>
                  <td colSpan={canWrite ? 4 : 3}>Chưa có điều chỉnh lương</td>
                </tr>
              )}
              {salaryChanges.map(s => (
                <tr key={s.id}>
                  <td>{isoToVn(s.effectiveDate)}</td>
                  <td>{money(s.amount, s.basis, s.grossAmount)}</td>
                  <td>{s.note}</td>
                  {canWrite && (
                    <td>
                      {editButton(`Sửa điều chỉnh lương ngày ${isoToVn(s.effectiveDate)}`, () => setDialog({ kind: 'salary', item: s }))}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="doit-section" aria-labelledby="h-allowance">
        <div className="doit-section-head">
          <h2 id="h-allowance">Phụ cấp</h2>
          {canWrite && (
            <Button type="button" variant="outline-primary" onClick={() => setDialog({ kind: 'allowance', item: null })}>
              + Thêm phụ cấp
            </Button>
          )}
        </div>
        <div className="table-responsive">
          <table className="table doit-table">
            <thead>
              <tr>
                <th scope="col">Tên</th>
                <th scope="col">Mỗi tháng</th>
                <th scope="col">Thời gian</th>
                {canWrite && <th scope="col">Thao tác</th>}
              </tr>
            </thead>
            <tbody>
              {allowances.length === 0 && (
                <tr>
                  <td colSpan={canWrite ? 4 : 3}>Chưa có phụ cấp</td>
                </tr>
              )}
              {allowances.map(a => (
                <tr key={a.id}>
                  <td>{a.name}</td>
                  <td>{a.amountPerMonth != null ? formatMoney(a.amountPerMonth) : '—'}</td>
                  <td className="text-nowrap">{range(a.fromDate, a.toDate)}</td>
                  {canWrite && <td>{editButton(`Sửa phụ cấp ${a.name ?? ''}`, () => setDialog({ kind: 'allowance', item: a }))}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {dialog?.kind === 'contract' && (
        <ContractDialog
          employeeId={employeeId}
          dependants={dependants}
          insured={insured}
          contracts={contracts}
          contract={dialog.item}
          isOpen
          toggle={close}
          onSaved={saved}
        />
      )}
      {dialog?.kind === 'documents' && <ContractDocumentsDialog contract={dialog.item} onClose={close} onChanged={saved} />}
      {dialog?.kind === 'salary' && (
        <SalaryChangeDialog
          employeeId={employeeId}
          dependants={dependants}
          insured={insured}
          salaryChange={dialog.item}
          isOpen
          toggle={close}
          onSaved={saved}
        />
      )}
      {dialog?.kind === 'allowance' && (
        <AllowanceDialog employeeId={employeeId} allowance={dialog.item} isOpen toggle={close} onSaved={saved} />
      )}
    </div>
  );
};

export default ContractsTab;
