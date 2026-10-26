import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button } from 'react-bootstrap';

import { CONTRACT_TYPE_LABEL } from 'app/shared/model/employee.model';
import { ChipTone, SectionCard, StatusChip } from 'app/shared/ui';

import { isoToVn } from '../vn-date';
import { contractStatus, range } from './contracts-tab';
import type { IDecision } from './decisions-section';
import { IContract, ISegment, LOCATION_TYPE_LABEL, errorText, useRoles } from './detail-api';

const TONE: Record<'ok' | 'warn' | 'old', ChipTone> = { ok: 'ok', warn: 'warn', old: 'muted' };

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** HĐ đã ký mới nhất sắp hết hạn trong 30 ngày (chip cảnh báo ở thẻ đầu hồ sơ), không có thì null. */
export const expiringContract = (contracts: IContract[]): IContract | null => {
  const latest = contracts.filter(c => c.status === 'DA_KY').sort((a, b) => (b.startDate ?? '').localeCompare(a.startDate ?? ''))[0];
  return latest && contractStatus(latest).text === 'Sắp hết hạn' ? latest : null;
};

/** Hiệu lực của quyết định cử nhân sự theo ngày: chưa đến, đang hiệu lực, đã kết thúc. */
const decisionStatus = (d: IDecision, today: string): { text: string; tone: ChipTone } => {
  if (d.fromDate > today) return { text: 'Chưa hiệu lực', tone: 'wait' };
  if (d.toDate && d.toDate < today) return { text: 'Đã kết thúc', tone: 'muted' };
  return { text: 'Hiệu lực', tone: 'ok' };
};

interface IDocRow {
  key: string;
  name: string;
  no?: string | null;
  period: string;
  sortDate: string;
  status: { text: string; tone: ChipTone };
}

/**
 * "Hợp đồng & quyết định" trên tab Thông tin (canvas): bảng gọn Văn bản / Hiệu lực / Trạng thái. Hợp đồng do trang hồ sơ tải sẵn; quyết định
 * cử nhân sự chỉ Quản trị, HR xem được (như tab Lịch sử công tác). Sửa, tạo văn bản vẫn ở tab Hợp đồng & lương.
 */
export const ContractsOverview = ({
  employeeId,
  contracts,
  error,
  onOpenContracts,
}: {
  employeeId: number;
  contracts: IContract[] | null;
  error?: string | null;
  onOpenContracts: () => void;
}) => {
  const { canManage } = useRoles();
  const [decisions, setDecisions] = useState<IDecision[]>([]);

  useEffect(() => {
    if (!canManage) return;
    axios
      .get<{ decision: IDecision }[]>(`api/employees/${employeeId}/assignment-decisions`)
      .then(r => setDecisions((r.data ?? []).map(x => x.decision)))
      .catch(() => setDecisions([]));
  }, [employeeId, canManage]);

  const today = todayIso();
  const rows: IDocRow[] = [
    ...(contracts ?? []).map(c => {
      const st = contractStatus(c);
      return {
        key: `c-${c.id}`,
        name: c.type ? CONTRACT_TYPE_LABEL[c.type] : 'Hợp đồng',
        no: c.contractNo,
        period: range(c.startDate, c.endDate),
        sortDate: c.startDate ?? '',
        status: { text: st.text, tone: TONE[st.tone] },
      };
    }),
    ...decisions.map(d => ({
      key: `d-${d.id}`,
      name: d.type === 'CONG_TAC' ? 'QĐ cử đi công tác' : 'QĐ cử làm tại khách hàng',
      no: d.decisionNo,
      period: range(d.fromDate, d.toDate),
      sortDate: d.fromDate,
      status: decisionStatus(d, today),
    })),
  ].sort((a, b) => b.sortDate.localeCompare(a.sortDate));

  return (
    <SectionCard
      title="Hợp đồng & quyết định"
      titleId="h-overview-contracts"
      actions={
        <Button type="button" variant="link" onClick={onOpenContracts}>
          Xem chi tiết
        </Button>
      }
    >
      {error && (
        <div role="alert" className="alert alert-danger mb-0">
          {error}
        </div>
      )}
      {contracts && rows.length === 0 && <p className="mb-0">Chưa có hợp đồng</p>}
      {rows.length > 0 && (
        <div className="table-responsive">
          <table className="table doit-table">
            <thead>
              <tr>
                <th scope="col">Văn bản</th>
                <th scope="col">Hiệu lực</th>
                <th scope="col">Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.key}>
                  <td>
                    <span className="fw-semibold">{r.name}</span>
                    {r.no && <span className="doit-cell-sub">Số {r.no}</span>}
                  </td>
                  <td className="text-nowrap">{r.period}</td>
                  <td>
                    <StatusChip tone={r.status.tone}>{r.status.text}</StatusChip>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </SectionCard>
  );
};

/** "Lịch sử công tác" trên tab Thông tin: dòng thời gian chỉ đọc, chấm cam ở giai đoạn hiện tại; thêm, sửa ở tab Lịch sử công tác. */
export const TimelineOverview = ({ employeeId, onAdd }: { employeeId: number; onAdd?: () => void }) => {
  const [segments, setSegments] = useState<ISegment[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    axios
      .get<ISegment[]>(`api/employees/${employeeId}/assignments/timeline`)
      .then(r => setSegments(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được lịch sử công tác')));
  }, [employeeId]);

  const today = todayIso();
  const ordered = [...(segments ?? [])].sort((a, b) => b.from.localeCompare(a.from));

  return (
    <SectionCard
      title="Lịch sử công tác"
      titleId="h-overview-timeline"
      actions={
        onAdd && (
          <Button type="button" variant="link" onClick={onAdd}>
            + Thêm điều động
          </Button>
        )
      }
    >
      {error && (
        <div role="alert" className="alert alert-danger mb-0">
          {error}
        </div>
      )}
      {segments?.length === 0 && <p className="mb-0">Chưa có lịch sử công tác</p>}
      {ordered.length > 0 && (
        <ol className="doit-timeline">
          {ordered.map(s => {
            const current = s.from <= today && (!s.to || s.to >= today);
            const place = s.unit || s.label || (s.type ? LOCATION_TYPE_LABEL[s.type] : '');
            return (
              <li key={`${s.id ?? 'gap'}-${s.from}-${s.to ?? ''}-${place}`}>
                <span className={`doit-dot ${current ? 'doit-dot-current' : ''}`} aria-hidden="true" />
                <div>
                  <span className="doit-timeline-period">
                    {isoToVn(s.from)} – {s.to ? isoToVn(s.to) : 'nay'}
                    {current && <span className="visually-hidden"> (hiện tại)</span>}
                  </span>
                  <span className="doit-timeline-place">{place}</span>
                  {s.type && <span className="doit-timeline-note">{LOCATION_TYPE_LABEL[s.type]}</span>}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </SectionCard>
  );
};
