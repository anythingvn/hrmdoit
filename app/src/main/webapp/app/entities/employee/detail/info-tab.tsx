import React, { useState } from 'react';
import axios from 'axios';
import { Button } from 'react-bootstrap';

import { IEmployee, INSURANCE_EXEMPTION_LABEL } from 'app/shared/model/employee.model';

import { formatMoney } from '../money';
import { isoToVn } from '../vn-date';
import { TERMINATION_TYPE_LABEL, TerminationType, errorText, useRoles } from './detail-api';

type SensitiveKey = 'idNumber' | 'idIssueDate' | 'idIssuePlace' | 'taxCode' | 'bankAccount' | 'bankName' | 'socialInsuranceNo';
type Sensitive = Partial<Record<SensitiveKey, string | null>>;

/** Kế toán chỉ được xem các trường này (server cũng chỉ trả các trường này). */
const KE_TOAN_FIELDS: SensitiveKey[] = ['bankAccount', 'bankName', 'socialInsuranceNo'];
const MASK = '••••';

export interface IEmployeeDetail extends IEmployee {
  lastWorkingDay?: string | null;
  terminationType?: TerminationType | null;
  terminationReason?: string | null;
  terminationDate?: string | null;
}

type Item = { k: string; v?: React.ReactNode; sensitive?: SensitiveKey; date?: boolean };

const text = (v?: string | number | null) => (v == null || v === '' ? '—' : String(v));

/** {@code self}: hồ sơ của chính người đang đăng nhập ("Hồ sơ của tôi") — xem đủ dữ liệu nhạy cảm của mình. */
export const InfoTab = ({ employee, self = false }: { employee: IEmployeeDetail; self?: boolean }) => {
  const roles = useRoles();
  const canSeeSensitive = self || roles.canSeeSensitive;
  const sensitiveFull = self || roles.sensitiveFull;
  const [sensitive, setSensitive] = useState<Sensitive | null>(null);
  const [shown, setShown] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // Mỗi lần "Hiện" đều gọi /sensitive (server ghi nhật ký); "Ẩn" xóa dữ liệu khỏi bộ nhớ.
  const toggleSensitive = async () => {
    setError(null);
    if (shown) {
      setShown(false);
      setSensitive(null);
      return;
    }
    setLoading(true);
    try {
      const r = await axios.get<Sensitive>(`api/employees/${employee.id}/sensitive`);
      setSensitive(r.data ?? {});
      setShown(true);
    } catch (err) {
      setError(errorText(err, 'Không xem được dữ liệu nhạy cảm'));
    } finally {
      setLoading(false);
    }
  };

  const sensitiveValue = (key: SensitiveKey, date?: boolean) => {
    const allowed = sensitiveFull || KE_TOAN_FIELDS.includes(key);
    if (!shown || !sensitive || !allowed) return MASK;
    const v = sensitive[key];
    return date ? text(isoToVn(v)) : text(v);
  };

  const groups: { title: string; items: Item[] }[] = [
    {
      // Thứ tự theo canvas: giấy tờ hay tra cứu (CCCD, MST, sổ BHXH) nằm ngay trong khối thông tin cá nhân.
      title: 'Thông tin cá nhân',
      items: [
        { k: 'Ngày sinh', v: text(isoToVn(employee.birthDate)) },
        { k: 'Giới tính', v: employee.gender === 'NAM' ? 'Nam' : employee.gender === 'NU' ? 'Nữ' : '—' },
        { k: 'Số CCCD', sensitive: 'idNumber' },
        { k: 'Điện thoại', v: text(employee.phone) },
        { k: 'Email', v: text(employee.email) },
        { k: 'Mã số thuế', sensitive: 'taxCode' },
        { k: 'Số sổ BHXH', sensitive: 'socialInsuranceNo' },
        { k: 'Địa chỉ', v: text(employee.residence) },
        { k: 'Trình độ', v: text(employee.education) },
      ],
    },
    {
      title: 'Giấy tờ',
      items: [
        { k: 'Ngày cấp CCCD', sensitive: 'idIssueDate', date: true },
        { k: 'Nơi cấp CCCD', sensitive: 'idIssuePlace' },
      ],
    },
    {
      title: 'Ngân hàng',
      items: [
        { k: 'Số tài khoản', sensitive: 'bankAccount' },
        { k: 'Ngân hàng', sensitive: 'bankName' },
      ],
    },
    {
      title: 'BHXH',
      items: [
        { k: 'Bắt đầu đóng', v: text(isoToVn(employee.socialInsuranceStart)) },
        { k: 'Mức lương đóng', v: employee.socialInsuranceSalary != null ? formatMoney(employee.socialInsuranceSalary) : '—' },
        { k: 'Số người phụ thuộc', v: text(employee.dependants) },
        { k: 'Số năm đóng BHXH trước khi vào DO IT', v: text(employee.priorSocialInsuranceYears) },
        {
          k: 'Bảo hiểm bắt buộc',
          v: employee.insuranceExemption
            ? `Không thuộc diện – ${INSURANCE_EXEMPTION_LABEL[employee.insuranceExemption]}${
                employee.insuranceExemptionNote ? ` (${employee.insuranceExemptionNote})` : ''
              }`
            : 'Thuộc diện đóng BHXH, BHYT, BHTN',
        },
      ],
    },
    {
      title: 'Công việc',
      items: [
        { k: 'Phòng ban', v: text(employee.department?.name) },
        { k: 'Chức danh', v: text(employee.position) },
        { k: 'Quản lý trực tiếp', v: text(employee.manager?.fullName) },
        { k: 'Ngày vào', v: text(isoToVn(employee.joinDate)) },
        { k: 'Mức phép năm', v: employee.annualLeaveDays != null ? `${employee.annualLeaveDays} ngày` : '—' },
        { k: 'Ghi chú', v: text(employee.note) },
      ],
    },
  ];
  if (employee.status === 'DA_NGHI') {
    groups.push({
      title: 'Nghỉ việc',
      items: [
        { k: 'Ngày làm việc cuối', v: text(isoToVn(employee.lastWorkingDay)) },
        { k: 'Ngày nghỉ việc', v: text(isoToVn(employee.terminationDate)) },
        { k: 'Hình thức', v: employee.terminationType ? TERMINATION_TYPE_LABEL[employee.terminationType] : '—' },
        { k: 'Lý do', v: text(employee.terminationReason) },
      ],
    });
  }

  // Nút "Hiện" nằm ở đầu khối đầu tiên (canvas: mỗi khu vực một thẻ, thao tác bên phải tiêu đề).
  const sensitiveToggle = canSeeSensitive && (
    <Button type="button" variant="outline-secondary" size="sm" onClick={() => void toggleSensitive()} disabled={loading}>
      {shown ? 'Ẩn' : 'Hiện'}
    </Button>
  );

  return (
    <div className="d-flex flex-column gap-4">
      {groups.map((g, gi) => (
        <section key={g.title} className="doit-section" aria-labelledby={`info-group-${gi}`}>
          <div className="doit-section-head">
            <h2 id={`info-group-${gi}`}>{g.title}</h2>
            {gi === 0 && sensitiveToggle}
          </div>
          {gi === 0 && canSeeSensitive && (
            <p className="text-muted small mb-0">Dữ liệu nhạy cảm đang {shown ? 'hiện' : 'ẩn'}; mỗi lần xem đều được ghi nhật ký.</p>
          )}
          {gi === 0 && error && (
            <div role="alert" className="alert alert-danger mb-0">
              {error}
            </div>
          )}
          <dl className="doit-dl">
            {g.items.map(i => (
              <div key={i.k}>
                <dt>{i.k}</dt>
                <dd>{i.sensitive ? sensitiveValue(i.sensitive, i.date) : i.v}</dd>
              </div>
            ))}
          </dl>
        </section>
      ))}
    </div>
  );
};

export default InfoTab;
