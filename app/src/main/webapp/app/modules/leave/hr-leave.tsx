import './leave.scss';

import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router';
import { Alert, Button, Form, Modal, Table } from 'react-bootstrap';

import { downloadFile } from 'app/shared/util/download';
import { PageHeader } from 'app/shared/ui';

import { IDepartmentOption, ILeaveAdjustment, ILeaveBalance, ILedgerEntry, IOpeningCheck, leaveApi, leaveErrorText } from './leave-api';
import { num } from './leave-labels';
import { LedgerTable } from './leave-shared';

type Tab = 'balances' | 'opening';

const TABS: [Tab, string][] = [
  ['balances', 'Số dư'],
  ['opening', 'Số dư đầu kỳ'],
];

/** Bỏ dấu, chữ thường: tìm "chi" ra "Lê Chi", "binh" ra "Trần Bình". */
const fold = (s?: string | null) => (s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();

/** "1,5" / "-2" / "+0.5" → số; không hợp lệ → NaN. */
const parseAmount = (raw: string): number => {
  const t = raw.trim().replace(',', '.');
  return /^[+-]?\d+(\.\d{1,2})?$/.test(t) ? Number(t) : NaN;
};

// ------------------------------------------------------------------ điều chỉnh

const AdjustDialog = ({
  target,
  onClose,
  onDone,
}: {
  target: ILeaveBalance | null;
  onClose: () => void;
  onDone: (msg: string) => void;
}) => {
  const [pool, setPool] = useState<ILeaveAdjustment['pool']>('PHEP_NAM');
  const [bucket, setBucket] = useState<ILeaveAdjustment['bucket']>('NAM_NAY');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setPool('PHEP_NAM');
    setBucket('NAM_NAY');
    setAmount('');
    setNote('');
    setError(null);
  }, [target?.employeeId]);

  const submit = async () => {
    if (!target) return;
    const value = parseAmount(amount);
    if (Number.isNaN(value) || value === 0) {
      setError('Nhập số khác 0, tối đa 2 chữ số thập phân (vd 1,5 hoặc -2)');
      return;
    }
    if (!note.trim()) {
      setError('Phải ghi lý do điều chỉnh');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await leaveApi.adjust({ employeeId: target.employeeId, pool, bucket, amount: value, note: note.trim() });
      const unit = pool === 'PHEP_NAM' ? 'ngày phép năm' : 'giờ nghỉ bù';
      onDone(`Đã ghi điều chỉnh ${value > 0 ? '+' : ''}${num(value)} ${unit} cho ${target.fullName}.`);
    } catch (err) {
      setError(leaveErrorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal show={!!target} onHide={onClose} centered className="leave-modal" aria-labelledby="adjust-title">
      <Modal.Header closeButton closeLabel="Đóng hộp thoại">
        <Modal.Title as="h3" className="h5" id="adjust-title">
          Điều chỉnh số dư: {target?.fullName}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body className="d-flex flex-column gap-3">
        {error && (
          <Alert variant="danger" role="alert" className="mb-0">
            {error}
          </Alert>
        )}
        <p className="text-muted mb-0">
          Ghi thành một dòng mới trong sổ phép (lý do &quot;HR điều chỉnh&quot;); dòng cũ không sửa. Số dương là cộng, số âm là trừ.
        </p>
        <Form.Group controlId="adj-pool">
          <Form.Label>Quỹ</Form.Label>
          <Form.Select value={pool} onChange={e => setPool(e.target.value as ILeaveAdjustment['pool'])}>
            <option value="PHEP_NAM">Phép năm (ngày)</option>
            <option value="NGHI_BU">Nghỉ bù (giờ)</option>
          </Form.Select>
        </Form.Group>
        <Form.Group controlId="adj-bucket">
          <Form.Label>Ngăn</Form.Label>
          <Form.Select value={bucket} onChange={e => setBucket(e.target.value as ILeaveAdjustment['bucket'])}>
            <option value="NAM_NAY">Năm nay</option>
            <option value="NAM_TRUOC">Năm trước (hết hạn theo cài đặt)</option>
          </Form.Select>
        </Form.Group>
        <Form.Group controlId="adj-amount">
          <Form.Label>Số điều chỉnh</Form.Label>
          <Form.Control inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} placeholder="vd 1,5 hoặc -2" />
        </Form.Group>
        <Form.Group controlId="adj-note">
          <Form.Label>Lý do</Form.Label>
          <Form.Control as="textarea" rows={3} maxLength={500} value={note} onChange={e => setNote(e.target.value)} />
        </Form.Group>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="outline-secondary" onClick={onClose}>
          Đóng
        </Button>
        <Button disabled={busy} onClick={() => void submit()}>
          Ghi điều chỉnh
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

// ------------------------------------------------------------------ số dư cả công ty

const BalancesTab = ({ onNotice }: { onNotice: (msg: string) => void }) => {
  const [rows, setRows] = useState<ILeaveBalance[] | null>(null);
  const [departments, setDepartments] = useState<IDepartmentOption[]>([]);
  const [departmentId, setDepartmentId] = useState<number | undefined>(undefined);
  const [query, setQuery] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [adjusting, setAdjusting] = useState<ILeaveBalance | null>(null);
  const [ledgerOf, setLedgerOf] = useState<{ who: ILeaveBalance; rows: ILedgerEntry[] | null } | null>(null);

  const load = () =>
    leaveApi
      .companyBalances(departmentId)
      .then(setRows)
      .catch(err => setError(leaveErrorText(err, 'ngày', 'Không tải được số dư')));

  useEffect(() => {
    axios
      .get<IDepartmentOption[]>('api/departments')
      .then(r => setDepartments(r.data ?? []))
      .catch(() => setDepartments([]));
  }, []);

  useEffect(() => {
    load();
  }, [departmentId]);

  const shown = useMemo(() => {
    const q = fold(query.trim());
    return (rows ?? []).filter(r => !q || fold(r.fullName).includes(q) || fold(r.code).includes(q));
  }, [rows, query]);

  const openLedger = (who: ILeaveBalance) => {
    setLedgerOf({ who, rows: null });
    leaveApi
      .ledger(who.employeeId)
      .then(l => setLedgerOf(s => (s && s.who.employeeId === who.employeeId ? { who, rows: l } : s)))
      .catch(err => {
        setLedgerOf(null);
        setError(leaveErrorText(err));
      });
  };

  return (
    <section className="leave-section" aria-label="Số dư cả công ty">
      {error && (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      )}
      <div className="leave-table-tools">
        <Form.Group controlId="hr-search">
          <Form.Label>Tìm nhân viên</Form.Label>
          <Form.Control value={query} onChange={e => setQuery(e.target.value)} placeholder="Tên hoặc mã" />
        </Form.Group>
        <Form.Group controlId="hr-department">
          <Form.Label>Phòng ban</Form.Label>
          <Form.Select value={departmentId ?? ''} onChange={e => setDepartmentId(e.target.value ? Number(e.target.value) : undefined)}>
            <option value="">Tất cả phòng ban</option>
            {departments.map(d => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
      </div>
      {rows === null && !error && <p>Đang tải...</p>}
      {rows && shown.length === 0 && <p className="mb-0">Không có nhân viên phù hợp.</p>}
      {shown.length > 0 && (
        <Table responsive className="align-middle">
          <thead>
            <tr>
              <th scope="col">Nhân viên</th>
              <th scope="col">Phòng ban</th>
              <th scope="col" className="text-end">
                Phép năm còn lại
              </th>
              <th scope="col" className="text-end">
                Trong đó năm trước
              </th>
              <th scope="col" className="text-end">
                Được xin
              </th>
              <th scope="col" className="text-end">
                Nghỉ bù (giờ)
              </th>
              <th scope="col" />
            </tr>
          </thead>
          <tbody>
            {shown.map(r => (
              <tr key={r.employeeId} data-testid={`balance-${r.employeeId}`}>
                <td>
                  <Link to={`/nhan-vien/${r.employeeId}`}>{r.fullName}</Link>
                  {r.code && <div className="small text-muted">{r.code}</div>}
                </td>
                <td>{r.departmentName ?? '—'}</td>
                <td className={`text-end${Number(r.annual.total) < 0 ? ' leave-negative' : ''}`}>{num(r.annual.total)}</td>
                <td className="text-end">{num(r.annual.previousYear)}</td>
                <td className="text-end">{num(r.annual.requestable)}</td>
                <td className="text-end">{num(r.compensatory.total)}</td>
                <td className="text-end">
                  <div className="d-flex flex-wrap gap-2 justify-content-end">
                    <Button variant="outline-secondary" onClick={() => openLedger(r)}>
                      Sổ phép
                    </Button>
                    <Button variant="outline-primary" onClick={() => setAdjusting(r)}>
                      Điều chỉnh
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      <AdjustDialog
        target={adjusting}
        onClose={() => setAdjusting(null)}
        onDone={msg => {
          setAdjusting(null);
          onNotice(msg);
          load();
        }}
      />
      <Modal show={!!ledgerOf} onHide={() => setLedgerOf(null)} centered size="lg" className="leave-modal" aria-labelledby="ledger-title">
        <Modal.Header closeButton closeLabel="Đóng hộp thoại">
          <Modal.Title as="h3" className="h5" id="ledger-title">
            Sổ phép: {ledgerOf?.who.fullName}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>{ledgerOf?.rows ? <LedgerTable ledger={ledgerOf.rows} /> : <p className="mb-0">Đang tải...</p>}</Modal.Body>
      </Modal>
    </section>
  );
};

// ------------------------------------------------------------------ số dư đầu kỳ

const OpeningTab = ({ onSaved }: { onSaved: (msg: string) => void }) => {
  const [file, setFile] = useState<File | null>(null);
  const [check, setCheck] = useState<IOpeningCheck | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const runCheck = async () => {
    if (!file) {
      setError('Chọn file Excel trước');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      setCheck(await leaveApi.openingCheck(file));
    } catch (err) {
      setCheck(null);
      setError(leaveErrorText(err, 'ngày', 'Không đọc được file'));
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (!file || !check?.canSave) return;
    setError(null);
    setBusy(true);
    try {
      const r = await leaveApi.openingSave(file);
      setCheck(null);
      setFile(null);
      onSaved(`Đã lưu số dư đầu kỳ cho ${r.savedCount} người.`);
    } catch (err: any) {
      const again = err?.response?.data?.check as IOpeningCheck | undefined;
      if (again) setCheck(again);
      setError(leaveErrorText(err, 'ngày', 'Không lưu được số dư đầu kỳ'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="leave-section d-flex flex-column gap-3" aria-label="Nhập số dư đầu kỳ">
      <div>
        <p className="mb-2">
          Nhập số phép năm còn lại và giờ nghỉ bù còn lại của từng người tại ngày bắt đầu dùng (một lần, ghi vào sổ với lý do &quot;Số dư
          đầu kỳ&quot;). Lưu theo kiểu tất cả hoặc không: còn một dòng lỗi thì không lưu dòng nào.
        </p>
        <p className="text-muted mb-0">
          Lưu ý: khi bắt đầu dùng, hệ thống đã tự cộng phép của tháng hiện tại. Số đầu kỳ là số còn lại <strong>trước</strong> phần cộng
          tháng này, để không cộng hai lần.
        </p>
      </div>
      {error && (
        <Alert variant="danger" role="alert" className="mb-0">
          {error}
        </Alert>
      )}
      <div className="leave-table-tools">
        <Button
          variant="outline-secondary"
          onClick={() =>
            downloadFile(leaveApi.openingTemplateUrl, 'mau-so-du-dau-ky.xlsx').catch(err =>
              setError(leaveErrorText(err, 'ngày', 'Không tải được file mẫu')),
            )
          }
        >
          Tải file mẫu
        </Button>
        <Form.Group controlId="opening-file">
          <Form.Label>File Excel số dư đầu kỳ</Form.Label>
          <Form.Control
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={e => {
              setFile((e.target as HTMLInputElement).files?.[0] ?? null);
              setCheck(null);
            }}
          />
        </Form.Group>
        <Button variant="outline-primary" disabled={busy || !file} onClick={() => void runCheck()}>
          Kiểm tra file
        </Button>
        <Button disabled={busy || !check?.canSave} onClick={() => void save()}>
          Lưu số dư đầu kỳ
        </Button>
      </div>
      {check && (
        <div className="d-flex flex-column gap-2">
          <p className="mb-0" aria-live="polite">
            {check.rowCount} dòng, {check.errorCount} lỗi. {check.canSave ? 'Có thể lưu.' : 'Sửa các dòng lỗi trong file rồi kiểm tra lại.'}
          </p>
          {check.issues.length > 0 && (
            <Alert variant="warning" className="mb-0">
              <ul className="mb-0">
                {check.issues.map((i, k) => (
                  <li key={k}>
                    Dòng {i.excelRow}
                    {i.code ? ` (${i.code})` : ''}: {i.message}
                  </li>
                ))}
              </ul>
            </Alert>
          )}
          {check.rows.length > 0 && (
            <Table responsive size="sm">
              <thead>
                <tr>
                  <th scope="col">Dòng</th>
                  <th scope="col">Mã NV</th>
                  <th scope="col">Họ và tên</th>
                  <th scope="col" className="text-end">
                    Phép năm còn lại
                  </th>
                  <th scope="col" className="text-end">
                    Trong đó năm trước
                  </th>
                  <th scope="col" className="text-end">
                    Giờ nghỉ bù
                  </th>
                </tr>
              </thead>
              <tbody>
                {check.rows.map(r => (
                  <tr key={r.excelRow}>
                    <td>{r.excelRow}</td>
                    <td>{r.code}</td>
                    <td>{r.fullName ?? ''}</td>
                    <td className="text-end">{num(r.annualRemaining)}</td>
                    <td className="text-end">{num(r.annualPreviousYear)}</td>
                    <td className="text-end">{num(r.compensatoryHours)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </div>
      )}
    </section>
  );
};

// ------------------------------------------------------------------ trang

/**
 * "Quản lý nghỉ phép" (HR, Quản trị): số dư cả công ty và sổ từng người, điều chỉnh, nhập số dư đầu kỳ. Cài đặt nghỉ phép ở
 * Cài đặt → Nghỉ phép.
 */
export const HrLeave = () => {
  const [tab, setTab] = useState<Tab>('balances');
  const [notice, setNotice] = useState<string | null>(null);

  return (
    <div className="leave-page">
      <PageHeader title="Quản lý nghỉ phép" subtitle={<>Số dư phép năm và nghỉ bù của cả công ty, điều chỉnh tay và số dư đầu kỳ.</>} />
      {notice && (
        <Alert variant="success" onClose={() => setNotice(null)} dismissible>
          {notice}
        </Alert>
      )}
      <div className="leave-tabs" role="tablist" aria-label="Quản lý nghỉ phép">
        {TABS.map(([id, label]) => (
          <Button
            key={id}
            role="tab"
            id={`hr-tab-${id}`}
            aria-selected={tab === id}
            aria-controls={`hr-panel-${id}`}
            variant="link"
            onClick={() => setTab(id)}
          >
            {label}
          </Button>
        ))}
      </div>
      <div role="tabpanel" id={`hr-panel-${tab}`} aria-labelledby={`hr-tab-${tab}`}>
        {tab === 'balances' && <BalancesTab onNotice={setNotice} />}
        {tab === 'opening' && (
          <OpeningTab
            onSaved={msg => {
              setNotice(msg);
              setTab('balances');
            }}
          />
        )}
      </div>
    </div>
  );
};

export default HrLeave;
