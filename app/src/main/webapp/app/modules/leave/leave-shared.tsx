import React, { useState } from 'react';
import { Alert, Button, Modal, Table } from 'react-bootstrap';

import { isoToVn } from 'app/entities/employee/vn-date';

import { ILeaveBalance, ILedgerEntry, leaveErrorText } from './leave-api';
import { BUCKET_LABEL, LEDGER_REASON_LABEL, POOL_LABEL, num } from './leave-labels';

/** Một thẻ số dư (nhãn, số lớn, đơn vị, ghi chú). */
export const BalanceCard = ({ label, value, unit, note }: { label: string; value: string; unit: string; note?: React.ReactNode }) => (
  <div className="leave-balance">
    <div className="lb-label">{label}</div>
    <div className="lb-value">
      {value}
      <span className="lb-unit">{unit}</span>
    </div>
    {note ? <div className="lb-note">{note}</div> : null}
  </div>
);

/** Các thẻ số dư: phép năm (kèm phần năm trước), nghỉ bù, ốm trong năm, làm thêm tháng này. */
export const BalanceCards = ({ balance }: { balance: ILeaveBalance }) => {
  const prev = Number(balance.annual.previousYear);
  return (
    <section className="leave-balances" aria-label="Số dư">
      <BalanceCard
        label="Phép năm còn lại"
        value={num(balance.annual.total)}
        unit="ngày"
        note={
          <>
            Được xin tối đa {num(balance.annual.requestable)} ngày (gồm 1 ngày ứng)
            {prev > 0 && balance.annual.previousYearExpiry && (
              <>
                <br />
                Trong đó {num(prev)} ngày năm trước, dùng đến {isoToVn(balance.annual.previousYearExpiry)}
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
          note="Mức BHXH theo số năm đóng; vượt mức chỉ cảnh báo"
        />
      )}
      {balance.overtime && (
        <BalanceCard
          label="Làm thêm tháng này"
          value={num(balance.overtime.monthHours)}
          unit={`/ ${balance.overtime.monthCap} giờ`}
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
