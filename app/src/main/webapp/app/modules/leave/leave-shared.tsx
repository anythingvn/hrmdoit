import React, { useState } from 'react';
import { Alert, Button, Modal, Table } from 'react-bootstrap';

import { isoToVn } from 'app/entities/employee/vn-date';

import { ILeaveBalance, ILedgerEntry, leaveErrorText } from './leave-api';
import { BUCKET_LABEL, LEDGER_REASON_LABEL, POOL_LABEL, num } from './leave-labels';

/** Thanh tiến độ của thẻ số dư: {@code value}/{@code max} (cắt trong 0–100%), cam cho phép năm, navy cho các thẻ khác. */
const BalanceBar = ({ value, max, label, accent }: { value: number; max: number; label: string; accent?: boolean }) => {
  const pct = max > 0 ? Math.max(0, Math.min(100, Math.round((value / max) * 100))) : 0;
  return (
    <div className="lb-bar" role="progressbar" aria-label={label} aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
      <span className={accent ? 'lb-bar-accent' : undefined} style={{ width: `${pct}%` }} />
    </div>
  );
};

/** Một thẻ số dư (nhãn, số lớn, đơn vị, thanh tiến độ, ghi chú). */
export const BalanceCard = ({
  label,
  value,
  unit,
  note,
  bar,
}: {
  label: string;
  value: string;
  unit: string;
  note?: React.ReactNode;
  bar?: React.ReactNode;
}) => (
  <div className="leave-balance">
    <div className="lb-label">{label}</div>
    <div className="lb-value">
      {value}
      <span className="lb-unit">{unit}</span>
    </div>
    {bar}
    {note ? <div className="lb-note">{note}</div> : null}
  </div>
);

/** "Phép năm: 12 + 1 (thâm niên 5 năm) = 13 ngày"; null khi server chưa trả mức năm. */
export const annualRateText = (annual: ILeaveBalance['annual']): string | null => {
  if (annual.annualDays == null || annual.baseAnnualDays == null) return null;
  const extra = annual.seniorityExtraDays ?? 0;
  if (extra > 0) {
    return `Phép năm: ${annual.baseAnnualDays} + ${extra} (thâm niên ${annual.seniorityYears ?? 0} năm) = ${annual.annualDays} ngày`;
  }
  return `Phép năm: ${annual.annualDays} ngày`;
};

/**
 * Các thẻ số dư (canvas Nghỉ phép): phép năm (thanh = phần năm nay còn / mức năm), nghỉ bù, ốm trong năm (đã dùng / mức BHXH), nghỉ không
 * lương đã duyệt trong năm (chỉ khi trang có danh sách đơn), làm thêm tháng này (đã ghi / giới hạn).
 */
export const BalanceCards = ({ balance, unpaid }: { balance: ILeaveBalance; unpaid?: { days: number; year: number } | null }) => {
  const prev = Number(balance.annual.previousYear);
  const rate = annualRateText(balance.annual);
  const advance = balance.annual.advanceDays ?? 0;
  const annualDays = balance.annual.annualDays;
  return (
    <section className="leave-balances" aria-label="Số dư">
      <BalanceCard
        label="Phép năm còn lại"
        value={num(balance.annual.total)}
        unit="ngày"
        bar={
          annualDays ? (
            <BalanceBar value={Number(balance.annual.currentYear)} max={annualDays} label="Phép năm nay còn so với mức năm" accent />
          ) : undefined
        }
        note={
          <>
            {annualDays ? (
              <>
                Năm nay còn {num(balance.annual.currentYear)} / {annualDays} ngày
                <br />
              </>
            ) : null}
            Được xin tối đa {num(balance.annual.requestable)} ngày
            {advance ? ` (gồm ${advance} ngày ứng)` : ''}
            {prev > 0 && balance.annual.previousYearExpiry && (
              <>
                <br />
                Trong đó {num(prev)} ngày năm trước, dùng đến {isoToVn(balance.annual.previousYearExpiry)}
              </>
            )}
            {rate && (
              <>
                <br />
                <span>{rate}</span>
              </>
            )}
          </>
        }
      />
      <BalanceCard label="Quỹ nghỉ bù" value={num(balance.compensatory.total)} unit="giờ" note="8 giờ = 1 ngày nghỉ bù" />
      {balance.sick && (
        <BalanceCard
          label="Nghỉ ốm trong năm"
          value={num(balance.sick.usedDays)}
          unit={`/ ${balance.sick.limitDays} ngày`}
          bar={<BalanceBar value={Number(balance.sick.usedDays)} max={balance.sick.limitDays} label="Nghỉ ốm đã dùng so với mức BHXH" />}
          note="Mức BHXH theo số năm đóng; vượt mức chỉ cảnh báo"
        />
      )}
      {unpaid && (
        <BalanceCard
          label="Nghỉ không lương"
          value={num(unpaid.days)}
          unit="ngày đã duyệt"
          note={`Đơn nghỉ không lương đã duyệt, năm ${unpaid.year}`}
        />
      )}
      {balance.overtime && (
        <BalanceCard
          label="Làm thêm tháng này"
          value={num(balance.overtime.monthHours)}
          unit={`/ ${balance.overtime.monthCap} giờ`}
          bar={
            <BalanceBar
              value={Number(balance.overtime.monthHours)}
              max={balance.overtime.monthCap}
              label="Giờ làm thêm tháng này so với giới hạn"
            />
          }
          note="Giới hạn theo luật lao động"
        />
      )}
    </section>
  );
};

/** Sổ phép: mới nhất trước. */
export const LedgerTable = ({ ledger, empty = 'Chưa có dòng nào.' }: { ledger: ILedgerEntry[]; empty?: string }) =>
  ledger.length === 0 ? (
    <p className="mb-0">{empty}</p>
  ) : (
    <Table responsive size="sm">
      <thead>
        <tr>
          <th scope="col">Ngày</th>
          <th scope="col">Quỹ</th>
          <th scope="col">Ngăn</th>
          <th scope="col" className="text-end">
            Số
          </th>
          <th scope="col">Lý do</th>
          <th scope="col">Ghi chú</th>
        </tr>
      </thead>
      <tbody>
        {[...ledger].reverse().map(e => (
          <tr key={e.id}>
            <td>{isoToVn(e.effectiveDate)}</td>
            <td>{POOL_LABEL[e.pool] ?? e.pool}</td>
            <td>
              {BUCKET_LABEL[e.bucket] ?? e.bucket} {e.year}
            </td>
            <td className="text-end">{`${Number(e.amount) > 0 ? '+' : ''}${num(e.amount)}`}</td>
            <td>{LEDGER_REASON_LABEL[e.reason] ?? e.reason}</td>
            <td>{e.note ?? ''}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );

export interface ConfirmAsk {
  title: string;
  body: React.ReactNode;
  confirmLabel: string;
  run: () => Promise<unknown>;
  done: string;
  /** Nút xác nhận đỏ (thao tác bỏ / hủy). */
  danger?: boolean;
}

/**
 * Hộp hỏi lại trước một thao tác (rút đơn, xin hủy). Lỗi hiện trong hộp; thành công thì đóng và báo {@code onDone(câu thông báo)}.
 */
export const ConfirmModal = ({
  ask,
  onClose,
  onDone,
}: {
  ask: ConfirmAsk | null;
  onClose: () => void;
  onDone: (notice: string) => void;
}) => {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const close = () => {
    setError(null);
    onClose();
  };

  const confirm = async () => {
    if (!ask) return;
    setError(null);
    setBusy(true);
    try {
      await ask.run();
      onDone(ask.done);
      close();
    } catch (err) {
      setError(leaveErrorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal show={!!ask} onHide={close} centered className="leave-modal" aria-labelledby="leave-confirm-title">
      <Modal.Header closeButton closeLabel="Đóng hộp thoại">
        <Modal.Title as="h3" className="h5" id="leave-confirm-title">
          {ask?.title}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && (
          <Alert variant="danger" role="alert">
            {error}
          </Alert>
        )}
        {ask?.body}
      </Modal.Body>
      <Modal.Footer>
        <Button variant="outline-secondary" onClick={close}>
          Đóng
        </Button>
        <Button variant={ask?.danger ? 'danger' : 'primary'} disabled={busy} onClick={() => void confirm()}>
          {ask?.confirmLabel}
        </Button>
      </Modal.Footer>
    </Modal>
  );
};
