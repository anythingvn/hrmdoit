import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import dayjs from 'dayjs';
import { Alert, Button, Form, Modal } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { BackupStatus, backupWarnings } from 'app/shared/layout/backup-banner/backup-banner';
import { SectionCard, StatusChip } from 'app/shared/ui';

import { createAgeKeyPair, IAgeKeyPair, keyFileName, keyFileText } from './age-key';
import { backupApi, IBackupSettings, IServiceAccountInfo, MAX_KEY_BYTES, parseSharedDriveId } from './backup-api';

const LINKS = {
  driveApi: 'https://console.cloud.google.com/apis/library/drive.googleapis.com',
  serviceAccounts: 'https://console.cloud.google.com/iam-admin/serviceaccounts',
  sharedDrives: 'https://drive.google.com/drive/shared-drives',
};

const SOURCE_TEXT = {
  WEB: 'Cấu hình trên web',
  ENV: 'Cấu hình bằng file .env trên máy chủ',
  NONE: 'Chưa cấu hình',
};

// Ngày theo giờ máy (không phải UTC) để tên file khóa không lệch ngày ở Việt Nam.
const today = () => dayjs().format('YYYY-MM-DD');

const shortKey = (key: string | null) => (key && key.length > 24 ? `${key.slice(0, 12)}…${key.slice(-8)}` : (key ?? ''));

const formatTime = (iso: string | null) => (iso ? new Date(iso).toLocaleString('vi-VN') : '');

const ExtLink = ({ href, children }: { href: string; children: React.ReactNode }) => (
  <a href={href} target="_blank" rel="noreferrer">
    {children}
  </a>
);

const StepTitle = ({ n, children }: { n: number; children: React.ReactNode }) => (
  <>
    <span className="badge text-bg-primary rounded-pill me-2">{n}</span>
    {children}
  </>
);

const OffsiteChip = ({ status }: { status: BackupStatus | null }) => {
  if (!status) return <StatusChip tone="muted">Chưa có thông tin</StatusChip>;
  if (!status.offsiteConfigured) return <StatusChip tone="muted">Chưa cấu hình</StatusChip>;
  if (status.offsiteOk === false)
    return (
      <>
        <StatusChip tone="danger">Lỗi</StatusChip> {status.offsiteReason ?? ''}
      </>
    );
  return <StatusChip tone="ok">Đạt</StatusChip>;
};

const saveBlob = (text: string, fileName: string) => {
  const href = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
  const a = document.createElement('a');
  a.href = href;
  a.download = fileName;
  // Gắn vào DOM và hoãn thu hồi URL: một số Safari/Firefox hủy tải nếu thu hồi ngay.
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
};

/** Tab "Sao lưu" (Quản trị): cấu hình sao lưu ra Google Drive; khóa mã hóa age tạo ngay trên trình duyệt, khóa bí mật không gửi lên máy chủ. */
export const BackupTab = () => {
  const [settings, setSettings] = useState<IBackupSettings | null>(null);
  const [status, setStatus] = useState<BackupStatus | null>(null);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [keyJson, setKeyJson] = useState<string | null>(null);
  const [keyInfo, setKeyInfo] = useState<IServiceAccountInfo | null>(null);
  const [keyError, setKeyError] = useState<string | null>(null);
  const [driveInput, setDriveInput] = useState('');
  const [testResult, setTestResult] = useState<string | null>(null);
  const [pair, setPair] = useState<IAgeKeyPair | null>(null);
  const [pairDate, setPairDate] = useState(today());
  const [confirmed, setConfirmed] = useState(false);
  const [confirmOff, setConfirmOff] = useState(false);
  const [copied, setCopied] = useState(false);
  const keyChoiceRef = useRef(0);

  const loadStatus = () =>
    axios
      .get<BackupStatus>('api/admin/backup-status')
      .then(r => setStatus(r.data))
      .catch(() => setStatus(null));

  useEffect(() => {
    backupApi
      .get()
      .then(setSettings)
      .catch(err => setError(errorText(err, 'Không tải được cấu hình sao lưu')));
    void loadStatus();
  }, []);

  const hasWeb = settings?.source === 'WEB';
  const showForm = !!settings && (settings.source !== 'WEB' || editing);
  const storedKey = hasWeb && !!settings.serviceAccountEmail;
  const storedAge = hasWeb ? settings.ageRecipient : null;

  const driveId = parseSharedDriveId(driveInput);
  const driveError = driveInput.trim() && !driveId ? 'Link Shared Drive không hợp lệ' : null;
  const keyReady = !!keyJson || storedKey;
  const ageRecipient = pair?.publicKey ?? storedAge;
  const ageReady = pair ? confirmed : !!storedAge;
  const canSave = showForm && keyReady && !!driveId && !!ageRecipient && ageReady && !busy;

  const startEdit = () => {
    setEditing(true);
    setDriveInput(settings?.sharedDriveId ?? '');
    setNotice(null);
    setError(null);
    setTestResult(null);
  };

  const resetForm = () => {
    keyChoiceRef.current += 1;
    setKeyJson(null);
    setKeyInfo(null);
    setKeyError(null);
    setPair(null);
    setConfirmed(false);
    setTestResult(null);
    setEditing(false);
  };

  const chooseKey = async (file: File | null) => {
    const token = ++keyChoiceRef.current;
    setKeyJson(null);
    setKeyInfo(null);
    setKeyError(null);
    setTestResult(null);
    if (!file) return;
    if (file.size > MAX_KEY_BYTES) {
      setKeyError('File quá lớn, khóa service account chỉ khoảng 2 KB.');
      return;
    }
    try {
      const text = await file.text();
      const info = await backupApi.checkKey(text);
      if (token !== keyChoiceRef.current) return;
      setKeyJson(text);
      setKeyInfo(info);
    } catch (err) {
      if (token === keyChoiceRef.current) setKeyError(errorText(err, 'Không đọc được file khóa'));
    }
  };

  const copyEmail = async (email: string) => {
    try {
      await navigator.clipboard?.writeText(email);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  const makeKey = async () => {
    setError(null);
    try {
      const created = await createAgeKeyPair();
      const date = today();
      setPair(created);
      setPairDate(date);
      setConfirmed(false);
      saveBlob(keyFileText(created, date), keyFileName(date));
    } catch {
      setError('Trình duyệt không tạo được khóa mã hóa. Thử lại bằng trình duyệt khác.');
    }
  };

  const testConnection = async (json: string | null, drive: string | null) => {
    if (!drive) return;
    setError(null);
    setNotice(null);
    setTestResult(null);
    setBusy(true);
    try {
      const d = await backupApi.test(json, drive);
      setTestResult(`Kết nối được Shared Drive "${d.name}".`);
    } catch (err) {
      setError(errorText(err, 'Không kiểm tra được kết nối'));
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (!canSave || !driveId || !ageRecipient) return;
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      const saved = await backupApi.save({ serviceAccountJson: keyJson, sharedDrive: driveId, ageRecipient, keySaved: true });
      setSettings(saved);
      resetForm();
      setNotice('Đã lưu cấu hình sao lưu Google Drive.');
      void loadStatus();
    } catch (err) {
      setError(errorText(err, 'Không lưu được cấu hình sao lưu'));
    } finally {
      setBusy(false);
    }
  };

  const disable = async () => {
    setConfirmOff(false);
    setError(null);
    setBusy(true);
    try {
      await backupApi.disable();
      setSettings(await backupApi.get());
      resetForm();
      setDriveInput('');
      setNotice('Đã tắt sao lưu Google Drive.');
      void loadStatus();
    } catch (err) {
      setError(errorText(err, 'Không tắt được sao lưu Google Drive'));
    } finally {
      setBusy(false);
    }
  };

  const warnings = backupWarnings(status);

  return (
    <section className="doit-page" aria-label="Sao lưu">
      {notice && (
        <Alert variant="success" className="mb-0" onClose={() => setNotice(null)} dismissible>
          {notice}
        </Alert>
      )}
      {error && (
        <Alert variant="danger" role="alert" className="mb-0">
          {error}
        </Alert>
      )}
      {!settings && !error && <p className="mb-0">Đang tải...</p>}

      {settings && (
        <SectionCard title="Tình trạng">
          <dl className="mb-0">
            <dt>Nguồn cấu hình</dt>
            <dd>
              <StatusChip tone={settings.source === 'NONE' ? 'warn' : 'ok'}>{SOURCE_TEXT[settings.source]}</StatusChip>
            </dd>
            <dt>Lần sao lưu gần nhất</dt>
            <dd>{status?.lastBackupAt ? formatTime(status.lastBackupAt) : 'Chưa có'}</dd>
            <dt>Đẩy ra ngoài máy chủ</dt>
            <dd className="mb-0">
              <OffsiteChip status={status} />
            </dd>
          </dl>
          {warnings.length > 0 && (
            <ul className="mt-3 mb-0 text-warning-emphasis">
              {warnings.map(w => (
                <li key={w}>{w}</li>
              ))}
            </ul>
          )}
        </SectionCard>
      )}

      {settings && hasWeb && !editing && (
        <SectionCard title="Google Drive">
          <dl>
            <dt>Service account</dt>
            <dd>{settings.serviceAccountEmail}</dd>
            <dt>Shared Drive</dt>
            <dd>{settings.sharedDriveName}</dd>
            <dt>Khóa mã hóa (công khai)</dt>
            <dd>
              <code>{shortKey(settings.ageRecipient)}</code>
            </dd>
            <dt>Cấu hình bởi</dt>
            <dd>
              {settings.updatedBy}
              {settings.updatedAt ? `, ${formatTime(settings.updatedAt)}` : ''}
            </dd>
          </dl>
          {testResult && (
            <Alert variant="success" className="mb-3">
              {testResult}
            </Alert>
          )}
          <div className="d-flex flex-wrap gap-2">
            <Button style={{ minHeight: 44 }} disabled={busy} onClick={() => void testConnection(null, settings.sharedDriveId)}>
              Kiểm tra kết nối
            </Button>
            <Button style={{ minHeight: 44 }} variant="outline-secondary" disabled={busy} onClick={startEdit}>
              Sửa
            </Button>
            <Button style={{ minHeight: 44 }} variant="outline-danger" disabled={busy} onClick={() => setConfirmOff(true)}>
              Tắt sao lưu Google Drive
            </Button>
          </div>
        </SectionCard>
      )}

      {showForm && (
        <>
          <SectionCard title={<StepTitle n={1}>Khóa Google (service account)</StepTitle>}>
            <ol className="text-muted">
              <li>
                Bật <ExtLink href={LINKS.driveApi}>Google Drive API</ExtLink> cho project.
              </li>
              <li>
                Vào <ExtLink href={LINKS.serviceAccounts}>IAM &amp; Admin → Service Accounts</ExtLink>, tạo service account, mở tab Keys,
                chọn Add key → JSON.
              </li>
              <li>Chọn file .json vừa tải về ở dưới.</li>
            </ol>
            <Form.Group controlId="backup-key-file">
              <Form.Label>File khóa JSON</Form.Label>
              <Form.Control
                type="file"
                accept="application/json,.json"
                style={{ minHeight: 44 }}
                onChange={e => void chooseKey((e.target as HTMLInputElement).files?.[0] ?? null)}
              />
              {storedKey && !keyInfo && <Form.Text muted>Đang dùng khóa đã lưu. Chỉ chọn file mới nếu muốn đổi khóa.</Form.Text>}
            </Form.Group>
            {keyError && (
              <Alert variant="danger" role="alert" className="mt-2 mb-0">
                {keyError}
              </Alert>
            )}
            {keyInfo && (
              <div className="d-flex flex-wrap gap-2 align-items-center mt-2">
                <span>Email service account:</span>
                <strong>{keyInfo.serviceAccountEmail}</strong>
                <Button
                  size="sm"
                  variant="outline-secondary"
                  style={{ minHeight: 44 }}
                  onClick={() => void copyEmail(keyInfo.serviceAccountEmail)}
                >
                  {copied ? 'Đã sao chép' : 'Sao chép'}
                </Button>
              </div>
            )}
          </SectionCard>

          <SectionCard title={<StepTitle n={2}>Shared Drive</StepTitle>}>
            <ol className="text-muted">
              <li>
                Tạo một <ExtLink href={LINKS.sharedDrives}>Shared Drive</ExtLink> (ví dụ "HRM backup").
              </li>
              <li>
                Thêm email service account ở bước 1 làm thành viên với vai trò <strong>Người quản lý nội dung</strong>.
              </li>
              <li>Dán link Shared Drive (hoặc ID) vào ô dưới.</li>
            </ol>
            <Form.Group controlId="backup-drive">
              <Form.Label>Link Shared Drive</Form.Label>
              <Form.Control
                value={driveInput}
                style={{ minHeight: 44 }}
                placeholder="https://drive.google.com/drive/folders/..."
                isInvalid={!!driveError}
                onChange={e => {
                  setDriveInput(e.target.value);
                  setTestResult(null);
                }}
              />
              <Form.Control.Feedback type="invalid">{driveError}</Form.Control.Feedback>
            </Form.Group>
          </SectionCard>

          <SectionCard title={<StepTitle n={3}>Khóa mã hóa</StepTitle>}>
            <p className="text-muted">
              Bản sao lưu được mã hóa trước khi lên Google Drive. Khóa được tạo ngay trên trình duyệt này; khóa bí mật không bao giờ gửi lên
              máy chủ.
            </p>
            {!pair && storedAge && (
              <p>
                Đang dùng khóa công khai <code>{shortKey(storedAge)}</code>.
              </p>
            )}
            {pair && storedAge && (
              <Alert variant="warning">Các bản sao lưu cũ vẫn cần khóa bí mật cũ để giải mã, hãy giữ cả file khóa cũ.</Alert>
            )}
            <Button style={{ minHeight: 44 }} variant={pair || storedAge ? 'outline-secondary' : 'primary'} onClick={() => void makeKey()}>
              Tạo khóa mã hóa
            </Button>
            {pair && (
              <div className="mt-3">
                <p className="mb-1">Khóa công khai:</p>
                <code className="d-block mb-2" style={{ overflowWrap: 'anywhere' }}>
                  {pair.publicKey}
                </code>
                <Button
                  variant="outline-secondary"
                  style={{ minHeight: 44 }}
                  onClick={() => saveBlob(keyFileText(pair, pairDate), keyFileName(pairDate))}
                >
                  Tải lại file khóa
                </Button>
                <Form.Check
                  id="backup-key-confirmed"
                  className="mt-3"
                  type="checkbox"
                  checked={confirmed}
                  onChange={e => setConfirmed(e.target.checked)}
                  label="Tôi đã cất file khóa bí mật ở nơi an toàn. Mất file này thì không khôi phục được bản sao lưu."
                />
              </div>
            )}
          </SectionCard>

          {testResult && (
            <Alert variant="success" className="mb-0">
              {testResult}
            </Alert>
          )}
          <div className="d-flex flex-wrap gap-2">
            <Button
              style={{ minHeight: 44 }}
              variant="outline-secondary"
              disabled={busy || !keyReady || !driveId}
              onClick={() => void testConnection(keyJson, driveId)}
            >
              Kiểm tra kết nối
            </Button>
            <Button style={{ minHeight: 44 }} disabled={!canSave} onClick={() => void save()}>
              Lưu
            </Button>
            {editing && (
              <Button style={{ minHeight: 44 }} variant="link" disabled={busy} onClick={resetForm}>
                Hủy
              </Button>
            )}
          </div>
        </>
      )}

      <Modal show={confirmOff} onHide={() => setConfirmOff(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title as="h2" className="fs-5">
            Tắt sao lưu Google Drive?
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          Máy chủ xóa khóa Google và cấu hình đã lưu; bản sao lưu mới sẽ không còn đẩy lên Google Drive. Các bản đã có trên Drive không bị
          xóa.
        </Modal.Body>
        <Modal.Footer>
          <Button style={{ minHeight: 44 }} variant="outline-secondary" onClick={() => setConfirmOff(false)}>
            Không tắt
          </Button>
          <Button style={{ minHeight: 44 }} variant="danger" onClick={() => void disable()}>
            Tắt sao lưu
          </Button>
        </Modal.Footer>
      </Modal>
    </section>
  );
};

export default BackupTab;
