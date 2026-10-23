import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { IWorkSite } from 'app/shared/model/work-site.model';

import { NOT_SECURE_TEXT, geoErrorText, mapsUrl, parseCoord, roundCoord, settingsApi, siteLocationError } from './settings-api';

const TYPE_LABEL: Record<string, string> = { VAN_PHONG: 'Văn phòng', KHACH_HANG: 'Khách hàng' };

const GEO_OPTIONS: PositionOptions = { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 };

interface IForm {
  latitude: string;
  longitude: string;
  radiusM: string;
  officeIps: string;
}

const toForm = (s: IWorkSite): IForm => ({
  latitude: s.latitude === null || s.latitude === undefined ? '' : String(s.latitude),
  longitude: s.longitude === null || s.longitude === undefined ? '' : String(s.longitude),
  radiusM: String(s.radiusM ?? 200),
  officeIps: s.officeIps ?? '',
});

const hasCoords = (s: IWorkSite) => s.latitude !== null && s.latitude !== undefined && s.longitude !== null && s.longitude !== undefined;

const ipCount = (ips?: string | null) => (ips ?? '').split(/\r?\n/).filter(l => l.trim()).length;

/** Form sửa toạ độ, bán kính, IP văn phòng của một nơi làm việc. */
const SiteEditor = ({ site, onCancel, onSaved }: { site: IWorkSite; onCancel: () => void; onSaved: (name: string) => void }) => {
  const [form, setForm] = useState<IForm>(() => toForm(site));
  const [error, setError] = useState<string | null>(null);
  const [geoError, setGeoError] = useState<string | null>(null);
  const [accuracy, setAccuracy] = useState<number | null>(null);
  const [locating, setLocating] = useState(false);
  const [busy, setBusy] = useState(false);

  const set = (key: keyof IForm, value: string) => setForm(f => ({ ...f, [key]: value }));

  const locate = () => {
    setGeoError(null);
    setAccuracy(null);
    if (window.isSecureContext === false) {
      setGeoError(NOT_SECURE_TEXT);
      return;
    }
    if (!navigator.geolocation) {
      setGeoError('Trình duyệt này không hỗ trợ lấy vị trí. Nhập toạ độ tay.');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      pos => {
        setLocating(false);
        setForm(f => ({
          ...f,
          latitude: String(roundCoord(pos.coords.latitude)),
          longitude: String(roundCoord(pos.coords.longitude)),
        }));
        setAccuracy(Math.round(pos.coords.accuracy));
      },
      err => {
        setLocating(false);
        setGeoError(geoErrorText(err.code));
      },
      GEO_OPTIONS,
    );
  };

  const save = async () => {
    const problem = siteLocationError(form.latitude, form.longitude, form.radiusM);
    if (problem) {
      setError(problem);
      return;
    }
    const latitude = parseCoord(form.latitude);
    const longitude = parseCoord(form.longitude);
    const radiusM = Number(form.radiusM.trim());
    // Gửi chuỗi rỗng (không phải null) để xoá danh sách IP qua PATCH.
    const officeIps = form.officeIps.trim();
    setError(null);
    setBusy(true);
    try {
      if (latitude === null && hasCoords(site)) {
        // PATCH bỏ qua giá trị null nên xoá toạ độ phải gửi cả bản ghi.
        await settingsApi.putSite({ ...site, latitude: null, longitude: null, radiusM, officeIps: officeIps || null });
      } else {
        await settingsApi.patchSite({ id: site.id, latitude, longitude, radiusM, officeIps });
      }
      onSaved(site.name ?? '');
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const lat = parseCoord(form.latitude);
  const lng = parseCoord(form.longitude);
  const mapLink = lat !== null && lng !== null && !Number.isNaN(lat) && !Number.isNaN(lng) ? mapsUrl(lat, lng) : null;
  const title = `Địa điểm chấm công: ${site.name ?? ''}`;

  return (
    <form
      className="settings-site-editor d-flex flex-column gap-3"
      aria-label={title}
      onSubmit={e => {
        e.preventDefault();
        void save();
      }}
    >
      <h3 className="h5 mb-0">{title}</h3>
      {error && (
        <Alert variant="danger" role="alert" className="mb-0">
          {error}
        </Alert>
      )}
      <div className="settings-grid">
        <Form.Group controlId={`site-${site.id}-lat`}>
          <Form.Label>Vĩ độ</Form.Label>
          <Form.Control
            inputMode="decimal"
            value={form.latitude}
            onChange={e => set('latitude', e.target.value)}
            placeholder="vd 21.028511"
          />
        </Form.Group>
        <Form.Group controlId={`site-${site.id}-lng`}>
          <Form.Label>Kinh độ</Form.Label>
          <Form.Control
            inputMode="decimal"
            value={form.longitude}
            onChange={e => set('longitude', e.target.value)}
            placeholder="vd 105.804817"
          />
        </Form.Group>
        <Form.Group controlId={`site-${site.id}-radius`}>
          <Form.Label>Bán kính cho phép (m)</Form.Label>
          <Form.Control type="number" min={20} max={5000} value={form.radiusM} onChange={e => set('radiusM', e.target.value)} />
          <Form.Text muted>Từ 20 đến 5000 m, mặc định 200 m.</Form.Text>
        </Form.Group>
      </div>
      <div className="d-flex flex-wrap gap-3 align-items-center">
        <Button variant="outline-primary" disabled={locating} onClick={locate}>
          {locating ? 'Đang lấy vị trí...' : 'Lấy vị trí hiện tại'}
        </Button>
        {mapLink && (
          <a href={mapLink} target="_blank" rel="noopener noreferrer">
            Xem trên bản đồ
          </a>
        )}
        <span aria-live="polite">
          {accuracy !== null && (
            <span className={accuracy > Number(form.radiusM) ? 'text-danger' : 'text-muted'}>
              Sai số khoảng {accuracy} m
              {accuracy > Number(form.radiusM) ? ' (lớn hơn bán kính, nên lấy lại ở đúng văn phòng hoặc nhập tay)' : ''}
            </span>
          )}
        </span>
      </div>
      {geoError && (
        <Alert variant="warning" role="alert" className="mb-0">
          {geoError}
        </Alert>
      )}
      <Form.Group controlId={`site-${site.id}-ips`}>
        <Form.Label>IP public của mạng văn phòng (không bắt buộc)</Form.Label>
        <Form.Control
          as="textarea"
          rows={3}
          value={form.officeIps}
          onChange={e => set('officeIps', e.target.value)}
          placeholder={'Mỗi dòng một IP hoặc dải CIDR, vd\n113.190.1.10\n10.0.0.0/8'}
        />
      </Form.Group>
      <div className="d-flex flex-wrap gap-2">
        <Button type="submit" disabled={busy}>
          Lưu địa điểm
        </Button>
        <Button variant="outline-secondary" onClick={onCancel}>
          Huỷ
        </Button>
      </div>
    </form>
  );
};

/**
 * Tab "Địa điểm chấm công" (Quản trị, HR): nơi làm việc loại văn phòng và khách hàng, mỗi nơi có toạ độ, bán kính, IP văn phòng.
 * Nơi ngừng dùng vẫn hiện (mờ) và sửa được.
 */
export const LocationsTab = () => {
  const [sites, setSites] = useState<IWorkSite[] | null>(null);
  const [editing, setEditing] = useState<IWorkSite | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = () =>
    settingsApi
      .workSites()
      .then(all => setSites(all.filter(s => s.type === 'VAN_PHONG' || s.type === 'KHACH_HANG')))
      .catch(err => setError(errorText(err, 'Không tải được danh sách nơi làm việc')));

  useEffect(() => {
    void load();
  }, []);

  return (
    <section className="d-flex flex-column gap-3" aria-label="Địa điểm chấm công">
      <p className="text-muted mb-0">
        Toạ độ, bán kính và IP văn phòng dùng cho nhân viên tự chấm công. Nút &quot;Lấy vị trí hiện tại&quot; dùng vị trí của thiết bị đang
        mở trang: hãy bấm khi đang đứng ở văn phòng.
      </p>
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
      {sites === null && !error && <p className="mb-0">Đang tải...</p>}
      {sites && sites.length === 0 && (
        <p className="mb-0">Chưa có nơi làm việc loại văn phòng hoặc khách hàng (thêm ở Danh mục → Nơi làm việc).</p>
      )}
      {sites && sites.length > 0 && (
        <Table responsive className="align-middle">
          <thead>
            <tr>
              <th scope="col">Nơi làm việc</th>
              <th scope="col">Loại</th>
              <th scope="col">Toạ độ</th>
              <th scope="col" className="text-end">
                Bán kính
              </th>
              <th scope="col" className="text-end">
                IP văn phòng
              </th>
              <th scope="col" />
            </tr>
          </thead>
          <tbody>
            {sites.map(s => (
              <tr key={s.id} className={s.active ? undefined : 'text-muted settings-inactive'}>
                <td>
                  {s.name}
                  {!s.active && <div className="small">Ngừng dùng</div>}
                </td>
                <td>{TYPE_LABEL[s.type ?? ''] ?? s.type}</td>
                <td>
                  {hasCoords(s) ? (
                    <a href={mapsUrl(s.latitude!, s.longitude!)} target="_blank" rel="noopener noreferrer">
                      {s.latitude}, {s.longitude}
                    </a>
                  ) : (
                    'Chưa có'
                  )}
                </td>
                <td className="text-end">{s.radiusM ?? 200} m</td>
                <td className="text-end">{ipCount(s.officeIps) || '—'}</td>
                <td className="text-end">
                  <Button
                    variant="outline-primary"
                    title={`Sửa ${s.name ?? ''}`}
                    onClick={() => {
                      setNotice(null);
                      setEditing(s);
                    }}
                  >
                    Sửa
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {editing && (
        <SiteEditor
          key={editing.id}
          site={editing}
          onCancel={() => setEditing(null)}
          onSaved={name => {
            setEditing(null);
            setNotice(`Đã lưu địa điểm chấm công ${name}.`);
            void load();
          }}
        />
      )}
    </section>
  );
};

export default LocationsTab;
