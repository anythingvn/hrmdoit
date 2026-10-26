import './self-punch.scss';

import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Col, Modal, Row } from 'react-bootstrap';

import { useAppSelector } from 'app/config/store';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';
import { ChipTone, StatusChip } from 'app/shared/ui';

import { IPunchRequest, IPunchResult, ITodayView, PUNCH_STATUS, attendanceApi, distanceText, weekdayVn as weekday } from './attendance-api';
import ExplanationForm from './explanation-form';
import { getPosition } from './geolocation';
import PunchHistory from './punch-history';

const BIG = { minHeight: 64, fontSize: 18, fontWeight: 600 };

/** Màu nhãn lần chấm theo bản phác: được tính xanh, chờ duyệt vàng, từ chối cam đỏ. */
const PUNCH_TONE: Record<string, ChipTone> = { CHAP_NHAN: 'ok', DA_DUYET: 'ok', CHO_DUYET: 'wait', TU_CHOI: 'warn' };

/** Ký hiệu đầu khung kết quả (bản phác): ✓ được tính, ⏳ chờ duyệt, ✕ bị từ chối. */
const RESULT_ICON: Record<string, string> = { CHAP_NHAN: '✓', DA_DUYET: '✓', CHO_DUYET: '⏳', TU_CHOI: '✕' };

const nowHm = () => {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

const methodText = (p: IPunchResult) => {
  if (p.method === 'IP') return 'IP văn phòng';
  if (p.method === 'TU_XA') return 'từ xa';
  if (p.method === 'GPS') return p.distanceM === null || p.distanceM === undefined ? 'GPS' : `GPS cách ${distanceText(p.distanceM)}`;
  return '';
};

/**
 * Màn "Chấm công" của nhân viên theo canvas điện thoại: đầu tối (lời chào, đồng hồ lớn, ngày · giờ làm), thẻ trắng nổi lên trên gồm nơi
 * chấm hôm nay, nút Vào / Ra lớn, kết quả và các lần chấm trong ngày; bên phải (≥ 992px, hẹp hơn thì nằm dưới) khung Lịch sử theo tháng.
 */
export const SelfPunch = () => {
  const account = useAppSelector(state => state.authentication.account);
  const displayName = [account?.firstName, account?.lastName].filter(Boolean).join(' ') || account?.login || '';
  const [today, setToday] = useState<ITodayView | null>(null);
  const [result, setResult] = useState<IPunchResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [explainOpen, setExplainOpen] = useState(false);
  const [clock, setClock] = useState(nowHm);
  const [locating, setLocating] = useState(false);
  const [sent, setSent] = useState<string | null>(null);
  const [refresh, setRefresh] = useState(0);

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
      setRefresh(n => n + 1);
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
    <Row className="g-4">
      <Col xs={12} lg={5}>
        <div className="self-punch">
          <header className="self-punch-head">
            <h1 className="visually-hidden">Chấm công</h1>
            {displayName && (
              <div className="self-punch-hello">
                <span>Xin chào</span>
                <strong>{displayName}</strong>
              </div>
            )}
            <div className="self-punch-clock" aria-label="Giờ hiện tại">
              {clock}
            </div>
            {today && (
              <div className="self-punch-date">
                {weekday(today.day)}, {isoToVn(today.day)}
              </div>
            )}
          </header>
          <div className="self-punch-card" data-testid="punch-card">
            {error && (
              <Alert variant="danger" role="alert" className="mb-0">
                {error}
              </Alert>
            )}
            {today && (
              <div className="self-punch-place">
                <strong>Hôm nay chấm tại: {today.siteName ?? 'chưa xác định'}</strong>
                {today.start && today.end && (
                  <span>
                    Giờ làm {today.start}–{today.end}
                  </span>
                )}
              </div>
            )}
            <div className="self-punch-buttons">
              <Button style={BIG} disabled={busy || !today} onClick={() => void punch('VAO')}>
                Vào
              </Button>
              <Button variant="dark" style={BIG} disabled={busy || !today} onClick={() => void punch('RA')}>
                Ra
              </Button>
            </div>
            {locating && (
              <div className="text-muted text-center" role="status">
                Đang lấy vị trí…
              </div>
            )}
            {sent && (
              <Alert variant="success" role="status" className="mb-0">
                {sent}
              </Alert>
            )}
            {result && st && (
              <div
                className={`self-punch-result self-punch-result-${PUNCH_TONE[result.status] ?? 'muted'}`}
                data-testid="punch-result"
                role="status"
              >
                <span aria-hidden="true">{RESULT_ICON[result.status]} </span>
                {result.message}{' '}
                {result.status === 'TU_CHOI' && (
                  <Button variant="link" className="p-0 align-baseline" onClick={() => setExplainOpen(true)}>
                    Gửi giải trình
                  </Button>
                )}
              </div>
            )}
            {today && today.punches.length > 0 && (
              <ul className="self-punch-list" aria-label="Các lần chấm hôm nay">
                {today.punches.map(p => (
                  <li key={p.id}>
                    <span>
                      {p.kind === 'VAO' ? 'Vào' : 'Ra'} {p.time}
                      {methodText(p) && ` · ${methodText(p)}`}
                    </span>
                    <StatusChip tone={PUNCH_TONE[p.status] ?? 'muted'}>{PUNCH_STATUS[p.status]?.label ?? p.status}</StatusChip>
                  </li>
                ))}
              </ul>
            )}
            <Modal
              show={noticeOpen && !!today?.consentRequired}
              onHide={() => setNoticeOpen(false)}
              centered
              aria-labelledby="consent-title"
            >
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
                  setRefresh(n => n + 1);
                }}
              />
            )}
          </div>
        </div>
      </Col>
      <Col xs={12} lg={7}>
        <PunchHistory refresh={refresh} />
      </Col>
    </Row>
  );
};

export default SelfPunch;
