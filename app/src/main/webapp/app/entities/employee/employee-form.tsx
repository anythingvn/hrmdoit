import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useParams } from 'react-router';
import { Button } from 'react-bootstrap';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { IDepartment } from 'app/shared/model/department.model';
import { IEmployee, PERSON_TYPE_LABEL, PersonType } from 'app/shared/model/employee.model';

import { createEntity, getEntity, reset, updateEntity } from './employee.reducer';
import MoneyInput from './money-input';
import { isoToVn, vnToIso } from './vn-date';

const REQUIRED = 'Bắt buộc nhập';
const BAD_DATE = 'Ngày không hợp lệ, nhập dd/MM/yyyy';

interface IFormValues {
  personType: PersonType;
  code: string;
  fullName: string;
  gender: string;
  birthDate: string;
  email: string;
  phone: string;
  residence: string;
  education: string;
  idNumber: string;
  idIssueDate: string;
  idIssuePlace: string;
  taxCode: string;
  bankAccount: string;
  bankName: string;
  socialInsuranceNo: string;
  socialInsuranceStart: string;
  socialInsuranceSalary: number | null;
  departmentId: string;
  position: string;
  managerId: string;
  joinDate: string;
  dependants: string;
  note: string;
}

const emptyValues: IFormValues = {
  personType: 'NHAN_VIEN',
  code: '',
  fullName: '',
  gender: '',
  birthDate: '',
  email: '',
  phone: '',
  residence: '',
  education: '',
  idNumber: '',
  idIssueDate: '',
  idIssuePlace: '',
  taxCode: '',
  bankAccount: '',
  bankName: '',
  socialInsuranceNo: '',
  socialInsuranceStart: '',
  socialInsuranceSalary: null,
  departmentId: '',
  position: '',
  managerId: '',
  joinDate: '',
  dependants: '',
  note: '',
};

const DATE_FIELDS: (keyof IFormValues)[] = ['birthDate', 'idIssueDate', 'socialInsuranceStart', 'joinDate'];

const fromEntity = (e: IEmployee): IFormValues => ({
  ...emptyValues,
  personType: e.personType ?? 'NHAN_VIEN',
  code: e.code ?? '',
  fullName: e.fullName ?? '',
  gender: e.gender ?? '',
  birthDate: isoToVn(e.birthDate),
  email: e.email ?? '',
  phone: e.phone ?? '',
  residence: e.residence ?? '',
  education: e.education ?? '',
  socialInsuranceStart: isoToVn(e.socialInsuranceStart),
  socialInsuranceSalary: e.socialInsuranceSalary ?? null,
  departmentId: e.department?.id != null ? String(e.department.id) : '',
  position: e.position ?? '',
  managerId: e.manager?.id != null ? String(e.manager.id) : '',
  joinDate: isoToVn(e.joinDate),
  dependants: e.dependants != null ? String(e.dependants) : '',
  note: e.note ?? '',
});

export const validate = (v: IFormValues): Partial<Record<keyof IFormValues, string>> => {
  const errors: Partial<Record<keyof IFormValues, string>> = {};
  if (!v.fullName.trim()) errors.fullName = REQUIRED;
  if (v.personType === 'NHAN_VIEN' && !v.code.trim()) errors.code = REQUIRED;
  if (v.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email.trim())) errors.email = 'Email không hợp lệ';
  DATE_FIELDS.forEach(f => {
    if (vnToIso(v[f] as string) === undefined) errors[f] = BAD_DATE;
  });
  if (v.dependants.trim() && !/^\d+$/.test(v.dependants.trim())) errors.dependants = 'Phải là số nguyên không âm';
  return errors;
};

const orNull = (s: string): string | null => (s.trim() ? s.trim() : null);

/** Chỉ gửi trường nhạy cảm khi có nhập; bỏ trống = server giữ giá trị đang lưu. */
export const toEntity = (v: IFormValues, id?: number): IEmployee => {
  const e: IEmployee = {
    personType: v.personType,
    fullName: v.fullName.trim(),
    gender: (v.gender || null) as IEmployee['gender'],
    birthDate: vnToIso(v.birthDate) ?? null,
    email: orNull(v.email),
    phone: orNull(v.phone),
    residence: orNull(v.residence),
    education: orNull(v.education),
    socialInsuranceStart: vnToIso(v.socialInsuranceStart) ?? null,
    socialInsuranceSalary: v.socialInsuranceSalary,
    department: v.departmentId ? { id: Number(v.departmentId) } : null,
    position: orNull(v.position),
    manager: v.managerId ? { id: Number(v.managerId) } : null,
    joinDate: vnToIso(v.joinDate) ?? null,
    dependants: v.dependants.trim() ? Number(v.dependants) : null,
    note: orNull(v.note),
  };
  if (id !== undefined) e.id = id;
  if (v.personType === 'NHAN_VIEN') e.code = v.code.trim();
  if (v.idNumber.trim()) e.idNumber = v.idNumber.trim();
  const idIssueDate = vnToIso(v.idIssueDate);
  if (idIssueDate) e.idIssueDate = idIssueDate;
  if (v.idIssuePlace.trim()) e.idIssuePlace = v.idIssuePlace.trim();
  if (v.taxCode.trim()) e.taxCode = v.taxCode.trim();
  if (v.bankAccount.trim()) e.bankAccount = v.bankAccount.trim();
  if (v.bankName.trim()) e.bankName = v.bankName.trim();
  if (v.socialInsuranceNo.trim()) e.socialInsuranceNo = v.socialInsuranceNo.trim();
  return e;
};

const serverMessage = (err: any): string => {
  const msg: string | undefined = err?.response?.data?.message;
  if (msg === 'error.codeexists') return 'Mã nhân viên đã tồn tại';
  if (msg === 'error.codenull') return 'Phải nhập mã nhân viên';
  if (msg === 'error.useterminate') return 'Không đổi tình trạng khi sửa hồ sơ; hãy dùng chức năng Cho nghỉ việc';
  return err?.response?.data?.detail ?? err?.response?.data?.title ?? 'Không lưu được hồ sơ';
};

export const EmployeeForm = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const entity = useAppSelector(state => state.employee.entity);
  const loading = useAppSelector(state => state.employee.loading);
  const updating = useAppSelector(state => state.employee.updating);

  const [values, setValues] = useState<IFormValues>(emptyValues);
  const [errors, setErrors] = useState<Partial<Record<keyof IFormValues, string>>>({});
  const [moneyValid, setMoneyValid] = useState(true);
  const [serverError, setServerError] = useState<string | null>(null);
  const [departments, setDepartments] = useState<IDepartment[]>([]);
  const [managerQuery, setManagerQuery] = useState('');
  const [managerOptions, setManagerOptions] = useState<{ id: number; label: string }[]>([]);

  useEffect(() => {
    dispatch(reset());
    if (!isNew) {
      dispatch(getEntity(id));
    }
    axios
      .get<IDepartment[]>('api/departments')
      .then(r => setDepartments(r.data ?? []))
      .catch(() => undefined);
  }, [id]);

  useEffect(() => {
    if (!isNew && entity?.id !== undefined && String(entity.id) === id) {
      setValues(fromEntity(entity));
      if (entity.manager?.id != null) {
        setManagerOptions([{ id: entity.manager.id, label: `${entity.manager.code ?? ''} ${entity.manager.fullName ?? ''}`.trim() }]);
      }
    }
  }, [entity, id]);

  // Tìm quản lý trực tiếp theo tên/mã trong danh sách nhân viên.
  useEffect(() => {
    const q = managerQuery.trim();
    if (!q) return undefined;
    const t = setTimeout(() => {
      axios
        .get<IEmployee[]>(`api/employees?q.contains=${encodeURIComponent(q)}&size=10`)
        .then(r => {
          const found = (r.data ?? []).filter((m): m is IEmployee & { id: number } => m.id !== undefined && String(m.id) !== id);
          setManagerOptions(prev => {
            const keep = prev.filter(o => String(o.id) === values.managerId);
            const fresh = found
              .filter(m => !keep.some(k => k.id === m.id))
              .map(m => ({ id: m.id, label: `${m.code ?? ''} ${m.fullName}`.trim() }));
            return [...keep, ...fresh];
          });
        })
        .catch(() => undefined);
    }, 300);
    return () => clearTimeout(t);
  }, [managerQuery]);

  const set =
    <K extends keyof IFormValues>(key: K) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
      const value = e.target.value as IFormValues[K];
      setValues(v => ({ ...v, [key]: value }));
    };

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    const errs = validate(values);
    if (!moneyValid) errs.socialInsuranceSalary = 'Số tiền không hợp lệ';
    setErrors(errs);
    if (Object.keys(errs).length > 0) {
      return;
    }
    const payload = toEntity(values, isNew ? undefined : Number(id));
    const action = isNew ? createEntity(payload) : updateEntity(payload);
    const result: any = await dispatch(action);
    if (result.error) {
      setServerError(serverMessage(result.error));
    } else {
      navigate(isNew ? `/nhan-vien/${result.payload?.data?.id ?? ''}` : `/nhan-vien/${id}`);
    }
  };

  const field = (
    key: keyof IFormValues,
    label: string,
    opts: { required?: boolean; hint?: string; type?: string; placeholder?: string; autoComplete?: string } = {},
  ) => {
    const inputId = `employee-${key}`;
    const errId = `${inputId}-error`;
    const hintId = `${inputId}-hint`;
    const describedBy = [errors[key] ? errId : null, opts.hint ? hintId : null].filter(Boolean).join(' ') || undefined;
    return (
      <div className="col-12 col-md-6">
        <label htmlFor={inputId} className="form-label">
          {label}
          {opts.required ? ' *' : ''}
        </label>
        <input
          id={inputId}
          type={opts.type ?? 'text'}
          className={`form-control ${errors[key] ? 'is-invalid' : ''}`}
          value={values[key] as string}
          onChange={set(key)}
          placeholder={opts.placeholder}
          aria-required={opts.required || undefined}
          aria-invalid={errors[key] ? true : undefined}
          aria-describedby={describedBy}
          autoComplete={opts.autoComplete ?? 'off'}
        />
        {opts.hint && (
          <div id={hintId} className="form-text">
            {opts.hint}
          </div>
        )}
        {errors[key] && (
          <div id={errId} className="invalid-feedback d-block">
            {errors[key]}
          </div>
        )}
      </div>
    );
  };

  const dateField = (key: keyof IFormValues, label: string, hint?: string) => field(key, label, { placeholder: 'dd/MM/yyyy', hint });
  const sensitiveHint = isNew ? undefined : 'Để trống nếu không đổi';

  if (!isNew && loading) {
    return <p>Đang tải...</p>;
  }

  return (
    <form onSubmit={e => void handleSubmit(e)} noValidate aria-labelledby="employee-form-heading">
      <h1 id="employee-form-heading" className="h3 mb-3">
        {isNew ? 'Thêm nhân viên' : 'Sửa hồ sơ nhân viên'}
      </h1>
      {serverError && (
        <div role="alert" className="alert alert-danger">
          {serverError}
        </div>
      )}

      <fieldset className="card p-3 mb-3">
        <legend className="h5">Định danh</legend>
        <div className="row g-3">
          <div className="col-12 col-md-6">
            <label htmlFor="employee-personType" className="form-label">
              Loại hồ sơ
            </label>
            <select
              id="employee-personType"
              className="form-select"
              value={values.personType}
              onChange={set('personType')}
              disabled={!isNew}
            >
              {Object.entries(PERSON_TYPE_LABEL).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </div>
          {values.personType === 'NHAN_VIEN' ? (
            field('code', 'Mã nhân viên', { required: true, hint: 'Ví dụ NV0142; hệ thống tự viết hoa' })
          ) : (
            <div className="col-12 col-md-6 d-flex align-items-end">
              <p className="mb-2">Mã HDDV sẽ được tự sinh</p>
            </div>
          )}
          {field('fullName', 'Họ và tên', { required: true, autoComplete: 'name' })}
          <div className="col-12 col-md-6">
            <label htmlFor="employee-gender" className="form-label">
              Giới tính
            </label>
            <select id="employee-gender" className="form-select" value={values.gender} onChange={set('gender')}>
              <option value="">Chưa chọn</option>
              <option value="NAM">Nam</option>
              <option value="NU">Nữ</option>
            </select>
          </div>
          {dateField('birthDate', 'Ngày sinh')}
        </div>
      </fieldset>

      <fieldset className="card p-3 mb-3">
        <legend className="h5">Liên hệ</legend>
        <div className="row g-3">
          {field('email', 'Email', { type: 'email', autoComplete: 'email' })}
          {field('phone', 'Số điện thoại', { type: 'tel', autoComplete: 'tel' })}
          {field('residence', 'Địa chỉ / nơi cư trú')}
        </div>
      </fieldset>

      <fieldset className="card p-3 mb-3">
        <legend className="h5">Giấy tờ</legend>
        <div className="row g-3">
          {field('idNumber', 'Số CCCD', { hint: sensitiveHint })}
          {dateField('idIssueDate', 'Ngày cấp', sensitiveHint)}
          {field('idIssuePlace', 'Nơi cấp', { hint: sensitiveHint })}
          {field('taxCode', 'Mã số thuế', { hint: sensitiveHint })}
          {field('education', 'Trình độ')}
        </div>
      </fieldset>

      <fieldset className="card p-3 mb-3">
        <legend className="h5">Ngân hàng</legend>
        <div className="row g-3">
          {field('bankAccount', 'Số tài khoản', { hint: sensitiveHint })}
          {field('bankName', 'Ngân hàng', { hint: sensitiveHint })}
        </div>
      </fieldset>

      <fieldset className="card p-3 mb-3">
        <legend className="h5">BHXH</legend>
        <div className="row g-3">
          {field('socialInsuranceNo', 'Số sổ BHXH', { hint: sensitiveHint })}
          {dateField('socialInsuranceStart', 'Tháng bắt đầu đóng BHXH')}
          <div className="col-12 col-md-6">
            <label htmlFor="employee-socialInsuranceSalary" className="form-label">
              Mức lương đóng BHXH
            </label>
            <MoneyInput
              id="employee-socialInsuranceSalary"
              value={values.socialInsuranceSalary}
              onChange={n => setValues(v => ({ ...v, socialInsuranceSalary: n }))}
              onValidityChange={setMoneyValid}
            />
          </div>
        </div>
      </fieldset>

      <fieldset className="card p-3 mb-3">
        <legend className="h5">Công việc</legend>
        <div className="row g-3">
          <div className="col-12 col-md-6">
            <label htmlFor="employee-departmentId" className="form-label">
              Phòng ban
            </label>
            <select id="employee-departmentId" className="form-select" value={values.departmentId} onChange={set('departmentId')}>
              <option value="">Chưa chọn</option>
              {departments.map(d => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
          </div>
          {field('position', 'Chức danh')}
          <div className="col-12 col-md-6">
            <label htmlFor="employee-manager-search" className="form-label">
              Tìm quản lý trực tiếp
            </label>
            <input
              id="employee-manager-search"
              type="search"
              className="form-control"
              value={managerQuery}
              onChange={e => setManagerQuery(e.target.value)}
              placeholder="Gõ tên hoặc mã"
            />
          </div>
          <div className="col-12 col-md-6">
            <label htmlFor="employee-managerId" className="form-label">
              Quản lý trực tiếp
            </label>
            <select id="employee-managerId" className="form-select" value={values.managerId} onChange={set('managerId')}>
              <option value="">Không có</option>
              {managerOptions.map(o => (
                <option key={o.id} value={o.id}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          {dateField('joinDate', 'Ngày vào công ty')}
          {field('dependants', 'Số người phụ thuộc', { type: 'number' })}
        </div>
      </fieldset>

      <fieldset className="card p-3 mb-3">
        <legend className="h5">Ghi chú</legend>
        <label htmlFor="employee-note" className="form-label">
          Ghi chú
        </label>
        <textarea id="employee-note" className="form-control" rows={3} value={values.note} onChange={set('note')} />
      </fieldset>

      <div className="d-flex gap-2">
        <Link to={isNew ? '/nhan-vien' : `/nhan-vien/${id}`} className="btn btn-secondary">
          Hủy
        </Link>
        <Button variant="primary" type="submit" disabled={updating}>
          Lưu
        </Button>
      </div>
    </form>
  );
};

export default EmployeeForm;
