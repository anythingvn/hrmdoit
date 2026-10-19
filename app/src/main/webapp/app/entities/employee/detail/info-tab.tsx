import React, { useState } from 'react';
import axios from 'axios';
import { Button } from 'reactstrap';

import { IEmployee } from 'app/shared/model/employee.model';

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
}

type Item = { k: string; v?: React.ReactNode; sensitive?: SensitiveKey; date?: boolean };

const text = (v?: string | number | null) => (v == null || v === '' ? '—' : String(v));

export const InfoTab = ({ employee }: { employee: IEmployeeDetail }) => {
  const { canSeeSensitive, sensitiveFull } = useRoles();
  const [sensitive, setSensitive] = useState<Sensitive | null>(null);
  const [shown, setShown] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const toggleSensitive = async () => {
    setError(null);
    if (shown) {
      setShown(false);
      return;
    }
    if (!sensitive) {
      setLoading(true);
      try {
        const r = await axios.get<Sensitive>(`api/employees/${employee.id}/sensitive`);
        setSensitive(r.data ?? {});
      } catch (err) {
        setError(errorText(err, 'Không xem được dữ liệu nhạy cảm'));
        return;
      } finally {
        setLoading(false);
      }
    }
    setShown(true);
  };

  const sensitiveValue = (key: SensitiveKey, date?: boolean) => {
    const allowed = sensitiveFull || KE_TOAN_FIELDS.includes(key);
    if (!shown || !sensitive || !allowed) return MASK;
    const v = sensitive[key];
    return date ? text(isoToVn(v)) : text(v);
  };

  const groups: Array<{ title: string; items: Item[] }> = [
    {
      title: 'Thông tin cá nhân',
      items: [
        { k: 'Ngày sinh', v: text(isoToVn(employee.birthDate)) },
        { k: 'Giới tính', v: employee.gender === 'NAM' ? 'Nam' : employee.gender === 'NU' ? 'Nữ' : '—' },
        { k: 'Điện thoại', v: text(employee.phone) },
        { k: 'Email', v: text(employee.email) },
        { k: 'Địa chỉ', v: text(employee.residence) },
        { k: 'Trình độ', v: text(employee.education) },
      ],
    },
    {
      title: 'Giấy tờ',
      items: [
        { k: 'Số CCCD', sensitive: 'idNumber' },
        { k: 'Ngày cấp', sensitive: 'idIssueDate', date: true },
        { k: 'Nơi cấp', sensitive: 'idIssuePlace' },
        { k: 'Mã số thuế', sensitive: 'taxCode' },
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
        { k: 'Số sổ BHXH', sensitive: 'socialInsuranceNo' },
        { k: 'Bắt đầu đóng', v: text(isoToVn(employee.socialInsuranceStart)) },
        { k: 'Mức lương đóng', v: employee.socialInsuranceSalary != null ? formatMoney(employee.socialInsuranceSalary) : '—' },
        { k: 'Số người phụ thuộc', v: text(employee.dependants) },
      ],
    },
    {
      title: 'Công việc',
      items: [
        { k: 'Phòng ban', v: text(employee.department?.name) },
        { k: 'Chức danh', v: text(employee.position) },
        { k: 'Quản lý trực tiếp', v: text(employee.manager?.fullName) },
        { k: 'Ngày vào', v: text(isoToVn(employee.joinDate)) },
        { k: 'Ghi chú', v: text(employee.note) },
      ],
    },
  ];
  if (employee.status === 'DA_NGHI') {
    groups.push({
      title: 'Nghỉ việc',
      items: [
        { k: 'Ngày làm việc cuối', v: text(isoToVn(employee.lastWorkingDay)) },
        { k: 'Hình thức', v: employee.terminationType ? TERMINATION_TYPE_LABEL[employee.terminationType] : '—' },
        { k: 'Lý do', v: text(employee.terminationReason) },
      ],
    });
  }

  return (
    <div className="d-flex flex-column gap-4">
      {canSeeSensitive && (
        <div className="d-flex flex-wrap align-items-center gap-2">
          <span className="text-muted">Dữ liệu nhạy cảm đang {shown ? 'hiện' : 'ẩn'}; mỗi lần xem đều được ghi nhật ký.</span>
          <Button type="button" color="secondary" outline onClick={() => void toggleSensitive()} disabled={loading}>
            {shown ? 'Ẩn' : 'Hiện'}
          </Button>
        </div>
      )}
      {error && (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      )}
      {groups.map((g, gi) => (
        <section key={g.title} className="doit-section" aria-labelledby={`info-group-${gi}`}>
          <h2 id={`info-group-${gi}`}>{g.title}</h2>
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
