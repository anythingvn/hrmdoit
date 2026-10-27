import './calc-draft.scss';

import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Modal } from 'react-bootstrap';
import { Link, useNavigate, useParams } from 'react-router';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { StatusChip } from 'app/shared/ui';

import { money, periodText } from '../payroll-api';
import {
  COUNT_FIELDS,
  ICalcColumn,
  ICalcDraft,
  ICalcLine,
  TOTAL_FIELDS,
  acknowledgeDraft,
  cancelDraft,
  confirmDraft,
  getCalc,
  parseCell,
  recalcDraft,
  removeCalcLine,
  restoreCalcLine,
  updateCalcLine,
} from './calc-api';

/** Số ngày / số người: bỏ số 0 thừa ("21", "20,5"); tiền: 1.234.567. */
const cellText = (field: string, v?: number | null) => {
  if (v == null) return '—';
  return COUNT_FIELDS.has(field) ? String(v).replace('.', ',') : money(v);
};

interface ICellProps {
  draft: ICalcDraft;
  line: ICalcLine;
  col: ICalcColumn;
  disabled: boolean;
  onSave: (line: ICalcLine, col: ICalcColumn, text: string) => void;
}

/** Một ô: cột nhập tay luôn là ô nhập; cột tự tính hiện số xám, bấm "sửa" mới thành ô nhập (ghi đè); cột tổng chỉ đọc. */
const Cell = ({ line, col, disabled, onSave }: Omit<ICellProps, 'draft'>) => {
  const [editing, setEditing] = useState(false);
  const { field } = col;
  const label = `${col.header} của ${line.fullName}`;
  const value = line.amounts[field];

  if (col.input) {
    const own = line.manual[field];
    return (
      <td className="calc-cell-input">
        <input
          type="text"
          inputMode="decimal"
          aria-label={label}
          className="form-control form-control-sm"
          defaultValue={own ?? ''}
          key={`${line.id}-${own ?? ''}`}
          disabled={disabled}
          onBlur={e => onSave(line, col, e.target.value)}
        />
      </td>
    );
  }
  const overridden = line.overrides[field] != null;
  const readOnly = TOTAL_FIELDS.has(field) || disabled;
  return (
    <td className={`calc-cell-auto${overridden ? ' calc-cell-overridden' : ''}`}>
      {editing ? (
        <input
          type="text"
          inputMode="decimal"
          autoFocus
          aria-label={label}
          className="form-control form-control-sm"
          defaultValue={value ?? ''}
          onBlur={e => {
            setEditing(false);
            if (e.target.value !== String(value ?? '')) onSave(line, col, e.target.value);
          }}
        />
      ) : readOnly ? (
        cellText(field, value)
      ) : (
        <button
          type="button"
          className="calc-cell-edit"
          aria-label={`Sửa ${col.header} của ${line.fullName}`}
          onClick={() => setEditing(true)}
        >
          {cellText(field, value)}
        </button>
      )}
      {overridden && !disabled && (
        <button
          type="button"
          className="btn btn-link p-0 ms-1"
          aria-label={`↺ Trả ${col.header} của ${line.fullName} về số tự tính`}
          title="Trả về số tự tính"
          onClick={() => onSave(line, col, '')}
        >
          ↺
        </button>
      )}
    </td>
  );
};

/** Bản tính lương nháp của một kỳ: sửa ô, tính lại từ hồ sơ, xác nhận thành phiên bản của kỳ. */
export const CalcDraft = () => {
  const { id = '' } = useParams<'id'>();
  const navigate = useNavigate();
  const [draft, setDraft] = useState<ICalcDraft | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [askCancel, setAskCancel] = useState(false);

  useEffect(() => {
    getCalc(id)
      .then(setDraft)
      .catch(err => setError(errorText(err, 'Không tải được bản tính lương')));
  }, [id]);

  /** Lấy lại tổng, trạng thái xác nhận từ máy chủ; dòng giữ như đang hiện. */
  const refreshTotals = async () => {
    try {
      const fresh = await getCalc(id);
      setDraft(prev => (prev ? { ...fresh, lines: prev.lines } : fresh));
    } catch {
      // Tổng cũ vẫn hiện; lần sửa tiếp theo sẽ tải lại.
    }
  };

  const save = async (line: ICalcLine, col: ICalcColumn, text: string) => {
    if (!draft) return;
    setError(null);
    const n = parseCell(text, col.field);
    if (Number.isNaN(n)) {
      setError(
        COUNT_FIELDS.has(col.field)
          ? `${col.header}: Nhập một số (ví dụ 20,5).`
          : `${col.header}: Nhập một số đồng nguyên (ví dụ 1500000 hoặc 1.500.000).`,
      );
      return;
    }
    const current = col.input ? line.manual[col.field] : line.overrides[col.field];
    if ((current ?? null) === n) return;
    const body = col.input ? { manual: { [col.field]: n } } : { overrides: { [col.field]: n } };
    try {
      const updated = await updateCalcLine(draft.id, line.id, body);
      setDraft(d => (d ? { ...d, lines: d.lines.map(l => (l.id === updated.id ? updated : l)) } : d));
      await refreshTotals();
    } catch (err) {
      setError(errorText(err));
    }
  };

  const remove = async (line: ICalcLine) => {
    if (!draft) return;
    setError(null);
    try {
      await removeCalcLine(draft.id, line.id);
      setDraft(d => (d ? { ...d, lines: d.lines.filter(l => l.id !== line.id) } : d));
      await refreshTotals();
    } catch (err) {
      setError(errorText(err));
    }
  };

  const run = async (action: () => Promise<ICalcDraft>) => {
    setError(null);
    setBusy(true);
    try {
      setDraft(await action());
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const confirm = async () => {
    if (!draft) return;
    setError(null);
    setBusy(true);
    try {
      const done = await confirmDraft(draft.id);
      navigate(`/bang-luong/${done.period}`);
    } catch (err) {
      setError(errorText(err, 'Không xác nhận được bản tính lương'));
      // Máy chủ có thể vừa tính lại và lưu số mới (thông số, mức luật, bảng công đã đổi): hiện số mới để xem lại.
      try {
        setDraft(await getCalc(id));
      } catch {
        // Giữ bảng đang hiện.
      }
    } finally {
      setBusy(false);
    }
  };

  if (!draft) return error ? <Alert variant="danger">{error}</Alert> : <p>Đang tải...</p>;

  const open = draft.status === 'DANG_LAM';
  const notices = draft.notices ?? [];
  const excluded = draft.excluded ?? [];
  const hasWarning = notices.length > 0 || draft.lines.some(l => l.warnings.length > 0);
  const errorLines = draft.lines.filter(l => l.errors.length > 0).length;

  return (
    <div className="doit-card calc-draft">
      <div className="d-flex flex-wrap gap-2 align-items-center mb-2">
        <h2 className="h4 mb-0">Tính lương kỳ {periodText(draft.period)}</h2>
        <StatusChip tone={open ? 'wait' : 'ok'}>{open ? 'Bản nháp' : draft.status === 'DA_XAC_NHAN' ? 'Đã xác nhận' : 'Đã hủy'}</StatusChip>
        <span className="text-muted">
          {draft.lines.length} người{errorLines > 0 ? ` · ${errorLines} dòng có lỗi` : ''}
        </span>
      </div>
      <p className="text-muted small">
        Ô nền xám do hệ thống tính (bấm số để sửa tay); ô trắng là khoản nhập tay. Ô sửa tay tô vàng, bấm ↺ để trả về số tự tính.
      </p>
      {error && <Alert variant="danger">{error}</Alert>}
      {notices.length > 0 && (
        <Alert variant="warning">
          {notices.map(n => (
            <div key={n}>{n}</div>
          ))}
        </Alert>
      )}
      <div className="calc-scroll">
        <table className="calc-table">
          <thead>
            <tr>
              <th scope="col" className="calc-sticky-1">
                Mã
              </th>
              <th scope="col" className="calc-sticky-2">
                Họ tên
              </th>
              {draft.columns.map(c => (
                <th key={c.field} scope="col" className="text-end">
                  {c.header}
                  {c.subHeader && <div className="small fw-normal text-muted">{c.subHeader}</div>}
                </th>
              ))}
              <th scope="col">Lỗi / cảnh báo</th>
              {open && <th scope="col" />}
            </tr>
          </thead>
          <tbody>
            {draft.lines.map(l => (
              <tr key={l.id}>
                <td className="calc-sticky-1">{l.code}</td>
                <td className="calc-sticky-2">
                  {l.fullName}
                  {l.position && <div className="small text-muted">{l.position}</div>}
                </td>
                {draft.columns.map(c => (
                  <Cell key={c.field} line={l} col={c} disabled={!open} onSave={save} />
                ))}
                <td style={{ whiteSpace: 'normal', minWidth: 220 }}>
                  {l.errors.map((m, k) => (
                    <div key={`e${k}`} className="text-danger small">
                      {m}
                    </div>
                  ))}
                  {l.warnings.map((m, k) => (
                    <div key={`w${k}`} className="text-warning-emphasis small">
                      {m}
                    </div>
                  ))}
                  {l.stale && <div className="small text-muted">Hồ sơ đã đổi, hãy tính lại</div>}
                </td>
                {open && (
                  <td>
                    <Button
                      variant="link"
                      className="text-danger p-0"
                      style={{ minHeight: 44 }}
                      aria-label={`Bỏ dòng ${l.fullName}`}
                      onClick={() => remove(l)}
                    >
                      Bỏ dòng
                    </Button>
                  </td>
                )}
              </tr>
            ))}
            <tr className="calc-total-row" aria-label="Tổng">
              <td className="calc-sticky-1">Tổng</td>
              <td className="calc-sticky-2" />
              {draft.columns.map(c => (
                <td key={c.field} className="text-end">
                  {!COUNT_FIELDS.has(c.field) && draft.totals[c.field] != null ? money(draft.totals[c.field]) : ''}
                </td>
              ))}
              <td />
              {open && <td />}
            </tr>
          </tbody>
        </table>
      </div>
      {open && excluded.length > 0 && (
        <div className="mt-3" data-testid="calc-excluded">
          <div className="small text-muted">Đã bỏ khỏi bản tính:</div>
          <ul className="list-unstyled mb-0">
            {excluded.map(x => (
              <li key={x.lineId} className="d-flex align-items-center gap-2">
                <span>
                  {x.code} – {x.fullName}
                </span>
                <Button
                  variant="link"
                  className="p-0"
                  style={{ minHeight: 44 }}
                  disabled={busy}
                  aria-label={`Khôi phục ${x.fullName}`}
                  onClick={() => run(() => restoreCalcLine(draft.id, x.lineId))}
                >
                  Khôi phục
                </Button>
              </li>
            ))}
          </ul>
        </div>
      )}
      {open && hasWarning && (
        <Form.Check
          id="calc-ack"
          className="mt-3"
          label="Đã xem và xác nhận các cảnh báo"
          checked={draft.warningsAcknowledged}
          disabled={draft.warningsAcknowledged || busy}
          onChange={() => run(() => acknowledgeDraft(draft.id))}
        />
      )}
      <div className="d-flex flex-wrap gap-2 mt-3 align-items-center">
        {open && (
          <>
            <Button variant="outline-secondary" style={{ minHeight: 44 }} disabled={busy} onClick={() => run(() => recalcDraft(draft.id))}>
              Tính lại từ hồ sơ
            </Button>
            <Button style={{ minHeight: 44 }} disabled={busy || !draft.canConfirm} onClick={confirm}>
              Xác nhận
            </Button>
            <Button variant="outline-danger" style={{ minHeight: 44 }} disabled={busy} onClick={() => setAskCancel(true)}>
              Hủy bản tính
            </Button>
          </>
        )}
        <Link to={`/bang-luong/${draft.period}`} className="ms-auto">
          Về chi tiết kỳ
        </Link>
      </div>
      <Modal show={askCancel} onHide={() => setAskCancel(false)}>
        <Modal.Header closeButton>
          <Modal.Title>Hủy bản tính lương</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          Hủy bản tính kỳ {periodText(draft.period)}? Các ô đã nhập, đã sửa của bản này không dùng tiếp; bấm &quot;Tính lương&quot; ở kỳ để
          tính bản mới.
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={() => setAskCancel(false)}>
            Không
          </Button>
          <Button
            variant="danger"
            onClick={() => {
              setAskCancel(false);
              void run(() => cancelDraft(draft.id));
            }}
          >
            Hủy bản tính này
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
};

export default CalcDraft;
