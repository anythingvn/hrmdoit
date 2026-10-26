import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { instantToVn } from 'app/entities/employee/vn-date';
import { StatusChip } from 'app/shared/ui';
import { downloadFile } from 'app/shared/util/download';
import { ArchivedBadge } from 'app/shared/archived-badge';

interface IPayslipStatus {
  employeeId: number;
  employeeCode: string;
  fullName: string;
  email?: string | null;
  status?: 'PENDING' | 'SENT' | 'FAILED' | null;
  version?: number | null;
  sentAt?: string | null;
  error?: string | null;
  staleVersion: boolean;
  archived?: boolean;
}

const statusText = (r: IPayslipStatus) => {
  if (r.status === 'SENT') return `Đã gửi lúc ${instantToVn(r.sentAt, true)}`;
  if (r.status === 'FAILED') return `Lỗi: ${r.error ?? 'không rõ'}`;
  if (r.status === 'PENDING') return 'Đang gửi…';
  return 'Chưa gửi';
};

const fileName = (period: string, code: string) => `PhieuLuong_${period.slice(5, 7)}-${period.slice(0, 4)}_${code}.pdf`;

/**
 * Gửi phiếu lương của kỳ đã duyệt (Kế toán, Quản trị): mỗi người một email kèm PDF có mật khẩu (6 số cuối CCCD).
 * Bảng tình trạng tự tải lại khi còn người đang gửi.
 */
export const PayslipPanel = ({ period }: { period: string }) => {
  const [rows, setRows] = useState<IPayslipStatus[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = () =>
    axios
      .get<IPayslipStatus[]>(`api/payroll/periods/${period}/payslips`)
      .then(r => setRows(r.data ?? []))
      .catch(err => setError(errorText(err, 'Không tải được tình trạng gửi phiếu lương')));

  useEffect(() => {
    load();
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [period]);

  // Còn lượt đang gửi: tải lại sau 3 giây.
  useEffect(() => {
    if (rows?.some(r => r.status === 'PENDING')) {
      timerRef.current = setTimeout(load, 3000);
    }
  }, [rows]);

  const send = async (employeeIds: number[] | null) => {
    setError(null);
    setBusy(true);
    try {
      const r = await axios.post<IPayslipStatus[]>(`api/payroll/periods/${period}/payslips/send`, { employeeIds });
      setRows(r.data ?? []);
    } catch (err) {
      setError(errorText(err, 'Không gửi được phiếu lương'));
    } finally {
      setBusy(false);
    }
  };

  const download = async (r: IPayslipStatus) => {
    setError(null);
    try {
      await downloadFile(`api/payroll/periods/${period}/payslips/${r.employeeId}/pdf`, fileName(period, r.employeeCode));
    } catch (err) {
      setError(errorText(err, 'Không tải được phiếu lương'));
    }
  };

  const sentCount = rows?.filter(r => r.status === 'SENT' && !r.staleVersion).length ?? 0;

  return (
    <section className="mt-4" aria-labelledby="payslip-title">
      <div className="d-flex flex-wrap gap-2 align-items-center mb-2">
        <h3 className="h5 mb-0" id="payslip-title">
          Phiếu lương
        </h3>
        {rows && (
          <span className="text-muted">
            Đã gửi {sentCount}/{rows.length}
          </span>
        )}
        <Button className="ms-auto" disabled={busy} onClick={() => send(null)}>
          Gửi phiếu lương cho người chưa nhận
        </Button>
      </div>
      <p className="text-muted small">
        Mỗi người một email tới email trên hồ sơ, kèm PDF mở bằng 6 số cuối CCCD. Thân email không có số tiền. Người thiếu email hoặc CCCD
        hiện lỗi, người khác vẫn được gửi.
      </p>
      {error && <Alert variant="danger">{error}</Alert>}
      {rows && rows.length > 0 && (
        <Table size="sm" responsive>
          <thead>
            <tr>
              <th scope="col">Nhân viên</th>
              <th scope="col">Email</th>
              <th scope="col">Tình trạng</th>
              <th scope="col" aria-label="Thao tác" />
            </tr>
          </thead>
          <tbody>
            {rows.map(r => (
              <tr key={r.employeeId}>
                <td>
                  {r.fullName} <span className="text-muted">({r.employeeCode})</span>
                  <ArchivedBadge archived={r.archived} />
                </td>
                <td>{r.email ?? '—'}</td>
                <td>
                  <span className={r.status === 'FAILED' ? 'text-danger' : undefined}>{statusText(r)}</span>
                  {r.staleVersion && (
                    <StatusChip tone="wait" className="ms-2">
                      Theo phiên bản cũ (v{r.version})
                    </StatusChip>
                  )}
                </td>
                <td className="text-end text-nowrap">
                  <Button size="sm" variant="outline-secondary" className="me-1" onClick={() => download(r)}>
                    Tải
                  </Button>
                  <Button
                    size="sm"
                    variant="outline-primary"
                    disabled={busy || r.status === 'PENDING'}
                    onClick={() => send([r.employeeId])}
                  >
                    Gửi lại
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </section>
  );
};

export default PayslipPanel;
