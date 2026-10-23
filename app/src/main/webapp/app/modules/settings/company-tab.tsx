import React, { useEffect, useState } from 'react';
import { Alert, Button, Form } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { logoUrl, useBranding } from 'app/shared/branding/branding';

import { COMPANY_LIMITS, CompanySettingInput, ICompanySetting, logoFileError, settingsApi } from './settings-api';

type FieldKey = keyof CompanySettingInput;

const FIELDS: { key: FieldKey; label: string; hint?: string }[] = [
  { key: 'companyName', label: 'Tên công ty', hint: 'Dùng trên phiếu lương, hợp đồng, quyết định, báo cáo.' },
  { key: 'shortName', label: 'Tên viết tắt', hint: 'Hiện ở menu và tiêu đề tab trình duyệt. Để trống thì dùng "DOIT HRM".' },
  { key: 'tagline', label: 'Khẩu hiệu', hint: 'Dòng nhỏ dưới tên ở menu và màn đăng nhập.' },
  { key: 'representativeName', label: 'Người đại diện ký' },
  { key: 'representativeTitle', label: 'Chức danh người đại diện' },
];

const EMPTY: Record<FieldKey, string> = { companyName: '', shortName: '', tagline: '', representativeName: '', representativeTitle: '' };

const toForm = (s: ICompanySetting): Record<FieldKey, string> => ({
  companyName: s.companyName ?? '',
  shortName: s.shortName ?? '',
  tagline: s.tagline ?? '',
  representativeName: s.representativeName ?? '',
  representativeTitle: s.representativeTitle ?? '',
});

const canPreview = () => typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function';

/** Tab "Công ty" (Quản trị): tên, tên viết tắt, khẩu hiệu, người đại diện; logo PNG/JPG/SVG tối đa 1 MB, chỉ hiện trên web. */
export const CompanyTab = () => {
  const branding = useBranding();
  const [setting, setSetting] = useState<ICompanySetting | null>(null);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [logoError, setLogoError] = useState<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [inputKey, setInputKey] = useState(0);

  useEffect(() => {
    settingsApi
      .company()
      .then(s => {
        setSetting(s);
        setForm(toForm(s));
      })
      .catch(err => setError(errorText(err, 'Không tải được cài đặt công ty')));
  }, []);

  useEffect(
    () => () => {
      if (preview) URL.revokeObjectURL(preview);
    },
    [preview],
  );

  const save = async () => {
    const dto = Object.fromEntries(FIELDS.map(f => [f.key, form[f.key].trim()])) as CompanySettingInput;
    if (!dto.companyName) {
      setError('Cần nhập tên công ty');
      return;
    }
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      const s = await settingsApi.saveCompany(dto);
      setSetting(s);
      setForm(toForm(s));
      setNotice('Đã lưu thông tin công ty.');
      void branding.refresh();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const chooseLogo = (file: File | null) => {
    setNotice(null);
    setPreview(null);
    setLogoFile(null);
    if (!file) {
      setLogoError(null);
      return;
    }
    const problem = logoFileError(file);
    setLogoError(problem);
    if (problem) return;
    setLogoFile(file);
    if (canPreview()) setPreview(URL.createObjectURL(file));
  };

  const clearChosen = () => {
    setLogoFile(null);
    setPreview(null);
    setInputKey(k => k + 1);
  };

  const logoAction = async (action: () => Promise<ICompanySetting>, done: string) => {
    setLogoError(null);
    setNotice(null);
    setBusy(true);
    try {
      const s = await action();
      setSetting(s);
      clearChosen();
      setNotice(done);
      // Menu, màn đăng nhập, favicon và tiêu đề tab đổi ngay, không cần tải lại trang.
      await branding.refresh();
    } catch (err) {
      setLogoError(errorText(err, 'Không cập nhật được logo'));
    } finally {
      setBusy(false);
    }
  };

  const currentVersion = branding.loaded ? branding.logoVersion : (setting?.logoVersion ?? 0);

  return (
    <section className="d-flex flex-column gap-4" aria-label="Thông tin công ty">
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
      {!setting && !error && <p className="mb-0">Đang tải...</p>}
      {setting && (
        <>
          <div>
            <h2 className="h5">Thông tin công ty</h2>
            <div className="settings-grid">
              {FIELDS.map(f => (
                <Form.Group key={f.key} controlId={`company-${f.key}`}>
                  <Form.Label>{f.label}</Form.Label>
                  <Form.Control
                    value={form[f.key]}
                    maxLength={COMPANY_LIMITS[f.key]}
                    required={f.key === 'companyName'}
                    onChange={e => setForm(s => ({ ...s, [f.key]: e.target.value }))}
                  />
                  {f.hint && <Form.Text muted>{f.hint}</Form.Text>}
                </Form.Group>
              ))}
            </div>
            <Button className="mt-3" disabled={busy} onClick={() => void save()}>
              Lưu thông tin công ty
            </Button>
          </div>

          <div>
            <h2 className="h5">Logo</h2>
            <p className="text-muted">
              PNG, JPG hoặc SVG, tối đa 1 MB. Logo hiện ở menu, màn đăng nhập, biểu tượng và tiêu đề tab trình duyệt; không in lên giấy tờ.
            </p>
            <div className="d-flex flex-wrap gap-4 align-items-start">
              <figure className="settings-logo-box mb-0">
                <img src={logoUrl(currentVersion)} alt="Logo hiện tại" width="96" height="96" />
                <figcaption className="small text-muted">Đang dùng</figcaption>
              </figure>
              {preview && (
                <figure className="settings-logo-box mb-0">
                  <img src={preview} alt="Logo mới (xem trước)" width="96" height="96" />
                  <figcaption className="small text-muted">Xem trước</figcaption>
                </figure>
              )}
              <div className="d-flex flex-column gap-2">
                <Form.Group controlId="company-logo">
                  <Form.Label>Chọn logo</Form.Label>
                  <Form.Control
                    key={inputKey}
                    type="file"
                    accept="image/png,image/jpeg,image/svg+xml,.png,.jpg,.jpeg,.svg"
                    onChange={e => chooseLogo((e.target as HTMLInputElement).files?.[0] ?? null)}
                  />
                </Form.Group>
                {logoError && (
                  <Alert variant="danger" role="alert" className="mb-0">
                    {logoError}
                  </Alert>
                )}
                <div className="d-flex flex-wrap gap-2">
                  <Button
                    disabled={busy || !logoFile}
                    onClick={() => logoFile && void logoAction(() => settingsApi.uploadLogo(logoFile), 'Đã cập nhật logo.')}
                  >
                    Tải logo lên
                  </Button>
                  <Button
                    variant="outline-secondary"
                    disabled={busy}
                    onClick={() => void logoAction(settingsApi.resetLogo, 'Đã trở lại logo mặc định.')}
                  >
                    Khôi phục logo mặc định
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </section>
  );
};

export default CompanyTab;
