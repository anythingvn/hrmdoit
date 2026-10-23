import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Button, Modal } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';

import { IPunchRequest, IPunchResult, ITodayView, PUNCH_STATUS, attendanceApi } from './attendance-api';
import ExplanationForm from './explanation-form';
import { getPosition } from './geolocation';

const WEEKDAY = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
const BIG = { minHeight: 64, fontSize: 18, fontWeight: 600 };

const nowHm = () => {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

const weekday = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return WEEKDAY[new Date(y, m - 1, d).getDay()];
};

const distanceText = (m: number) => (m >= 1000 ? `${(m / 1000).toFixed(1).replace('.', ',')} km` : `${m} m`);

const methodText = (p: IPunchResult) => {
  if (p.method === 'IP') return 'IP văn phòng';
  if (p.method === 'TU_XA') return 'từ xa';
  if (p.method === 'GPS') return p.distanceM === null || p.distanceM === undefined ? 'GPS' : `GPS cách ${distanceText(p.distanceM)}`;
  return '';
};

/** Màn "Chấm công" của nhân viên: nút Vào / Ra, nơi phải chấm hôm nay, kết quả và các lần chấm trong ngày. */
export const SelfPunch = () => {
  const [today, setToday] = useState<ITodayView | null>(null);
  const [result, setResult] = useState<IPunchResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [explainOpen, setExplainOpen] = useState(false);
  const [clock, setClock] = useState(nowHm);
  const [locating, setLocating] = useState(false);
  const [sent, setSent] = useState<string | null>(null);

  const load = useCallback(
    () =>
      attendanceApi
        .today()
        .then(t => {
          setToday(t);
          setNoticeOpen(prev => prev || t.consentRequired);
        })
        .catch(err => setError(errorText(err, 'Không tải được màn chấm công'))),
    [],
  );

  useEffect(() => {
    void load();
    const timer = setInterval(() => setClock(nowHm()), 15000);
    return () => clearInterval(timer);
  }, [load]);

  const consent = async () => {
    setError(null);
    setBusy(true);
    try {
      await attendanceApi.consent();
      setToday(t => (t ? { ...t, consentRequired: false } : t));
      setNoticeOpen(false);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const punch = async (kind: 'VAO' | 'RA') => {
    if (today?.consentRequired) {
      setNoticeOpen(true);
      return;
    }
    setError(null);
    setSent(null);
    setBusy(true);
    try {
      setLocating(true);
      const pos = await getPosition();
      setLocating(false);
      const req: IPunchRequest = pos ? { kind, ...pos } : { kind };
      setResult(await attendanceApi.punch(req));
      await load();
    } catch (err) {
      setLocating(false);
      setResult(null);
      setError(errorText(err, 'Không chấm công được, vui lòng thử lại'));
    } finally {
      setBusy(false);
    }
  };

  const st = result ? PUNCH_STATUS[result.status] : null;

  return (
    <div style={{ maxWidth: 420 }} className="mx-auto">
      <h2 className="visually-hidden">Chấm công</h2>
      <div className="text-center" style={{ fontSize: 40, fontWeight: 700 }} aria-label="Giờ hiện tại">
        {clock}
      </div>
      {today && (
        <div className="text-center text-muted mb-3">
          {weekday(today.day)}, {isoToVn(today.day)}
        </div>
      )}
      {error && (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      )}
      {today && (
        <div className="border rounded-3 p-2 mb-3" style={{ background: '#f6f5ef' }}>
          <strong className="d-block">Hôm nay chấm tại: {today.siteName ?? 'chưa xác định'}</strong>
          {today.start && today.end && (
            <span className="text-muted small">
              Giờ làm {today.start}–{today.end}
            </span>
          )}
        </div>
      )}
      <div className="d-flex gap-2 mb-3">
        <Button className="flex-fill" style={BIG} disabled={busy || !today} onClick={() => void punch('VAO')}>
          Vào
        </Button>
        <Button className="flex-fill" variant="dark" style={BIG} disabled={busy || !today} onClick={() => void punch('RA')}>
          Ra
        </Button>
      </div>
      {locating && (
        <div className="text-muted text-center mb-2" role="status">
          Đang lấy vị trí…
        </div>
      )}
      {sent && (
        <Alert variant="success" role="status">
          {sent}
        </Alert>
      )}
      {result && st && (
        <Alert variant={st.alert} data-testid="punch-result">
          {result.message}{' '}
          {result.status === 'TU_CHOI' && (
            <Button variant="link" className="p-0 align-baseline" onClick={() => setExplainOpen(true)}>
              Gửi giải trình
            </Button>
          )}
        </Alert>
      )}
      {today && today.punches.length > 0 && (
        <ul className="list-unstyled border-top pt-2" aria-label="Các lần chấm hôm nay">
          {today.punches.map(p => (
            <li key={p.id} className="d-flex justify-content-between align-items-center py-2 border-bottom">
              <span>
                {p.kind === 'VAO' ? 'Vào' : 'Ra'} {p.time}
                {methodText(p) && ` · ${methodText(p)}`}
              </span>
              <Badge bg={PUNCH_STATUS[p.status]?.bg ?? 'secondary'} text={p.status === 'CHO_DUYET' ? 'dark' : undefined}>
                {PUNCH_STATUS[p.status]?.label ?? p.status}
              </Badge>
            </li>
          ))}
        </ul>
      )}

      <Modal show={noticeOpen && !!today?.consentRequired} onHide={() => setNoticeOpen(false)} centered aria-labelledby="consent-title">
        <Modal.Header closeButton closeLabel="Đóng hộp thoại">
          <Modal.Title as="h3" className="h5" id="consent-title">
            Thông báo thu thập vị trí
          </Modal.Title>
        </Modal.Header>
        <Modal.Body style={{ whiteSpace: 'pre-line' }}>{today?.noticeText}</Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" style={{ minHeight: 44 }} onClick={() => setNoticeOpen(false)}>
            Để sau
          </Button>
          <Button style={{ minHeight: 44 }} disabled={busy} onClick={() => void consent()}>
            Tôi đồng ý
          </Button>
        </Modal.Footer>
      </Modal>

      {explainOpen && today && (
        <ExplanationForm
          day={today.day}
          onClose={() => setExplainOpen(false)}
          onDone={() => {
            setExplainOpen(false);
            setResult(null);
            setSent('Đã gửi giải trình');
          }}
        />
      )}
    </div>
  );
};

export default SelfPunch;
