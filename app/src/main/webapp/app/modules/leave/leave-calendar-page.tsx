import './leave.scss';

import React, { useEffect, useState } from 'react';
import { Alert, Form } from 'react-bootstrap';

import { ICalendar, IDepartmentOption, currentMonth, leaveApi, leaveErrorText } from './leave-api';
import { CALENDAR_COLOR, CALENDAR_TYPE_LABEL, CalendarType } from './leave-labels';
import EmployeeLeaveModal, { LeavePerson } from './employee-leave-modal';
import MonthCalendar from './month-calendar';

const TYPES: CalendarType[] = ['PHEP_NAM', 'VIEC_RIENG', 'OM', 'KHONG_LUONG', 'NGHI_BU', 'CONG_TAC', 'NGHI_VIEC'];

/**
 * "Lịch nghỉ": lịch tháng của những người mình được xem (BGĐ, HR, Quản trị: cả công ty; trưởng nhóm: nhóm mình; trưởng phòng: phòng
 * mình). Lọc theo phòng ban và loại; không hiện lý do nghỉ. Phạm vi xem do server quyết định.
 */
export const LeaveCalendarPage = () => {
  const [month, setMonth] = useState(currentMonth());
  const [departmentId, setDepartmentId] = useState<number | undefined>(undefined);
  const [type, setType] = useState<CalendarType | ''>('');
  const [departments, setDepartments] = useState<IDepartmentOption[]>([]);
  const [calendar, setCalendar] = useState<ICalendar | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [person, setPerson] = useState<LeavePerson | null>(null);

  useEffect(() => {
    leaveApi
      .calendarDepartments()
      .then(setDepartments)
      .catch(() => setDepartments([]));
  }, []);

  useEffect(() => {
    let live = true;
    const params: { departmentId?: number; type?: string } = {};
    if (departmentId) params.departmentId = departmentId;
    if (type) params.type = type;
    setError(null);
    leaveApi
      .calendar(month, params)
      .then(c => live && setCalendar(c))
      .catch(err => live && setError(leaveErrorText(err, 'ngày', 'Không tải được lịch nghỉ')));
    return () => {
      live = false;
    };
  }, [month, departmentId, type]);

  const people = new Set((calendar?.items ?? []).map(it => it.employeeId)).size;

  return (
    <div className="leave-page">
      <div>
        <h1>Lịch nghỉ</h1>
        <p className="text-muted mb-0">Ai nghỉ ngày nào trong tháng. Đơn chờ duyệt hiện mờ; lịch không hiện lý do nghỉ.</p>
      </div>
      {error && (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      )}
      <section className="leave-section" aria-label="Lịch nghỉ tháng">
        <div className="leave-filters">
          <Form.Group controlId="cal-department">
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
          <Form.Group controlId="cal-type">
            <Form.Label>Loại</Form.Label>
            <Form.Select value={type} onChange={e => setType(e.target.value as CalendarType | '')}>
              <option value="">Tất cả loại</option>
              {TYPES.map(t => (
                <option key={t} value={t}>
                  {CALENDAR_TYPE_LABEL[t]}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
          <p className="text-muted mb-2" aria-live="polite">
            {calendar ? `${people} người có lịch trong tháng` : 'Đang tải...'}
          </p>
        </div>
        <MonthCalendar
          month={month}
          days={calendar?.days ?? []}
          items={calendar?.items ?? []}
          onMonthChange={setMonth}
          onPersonClick={(employeeId, name) => setPerson({ employeeId, name })}
        />
        <p className="text-muted small mt-2 mb-0">
          Bấm ngày có &quot;+N người&quot; để xem danh sách; bấm tên để xem số dư, đơn và sổ phép.
        </p>
        <ul className="leave-legend" aria-label="Chú thích màu">
          {TYPES.map(t => (
            <li key={t}>
              <span
                className="leave-legend-swatch"
                style={{ background: CALENDAR_COLOR[t].bg, borderColor: CALENDAR_COLOR[t].border ?? CALENDAR_COLOR[t].bg }}
                aria-hidden="true"
              />
              {CALENDAR_TYPE_LABEL[t]}
            </li>
          ))}
          <li>
            <span className="leave-legend-swatch is-pending" aria-hidden="true" />
            Chờ duyệt (mờ)
          </li>
        </ul>
      </section>
      <EmployeeLeaveModal person={person} onClose={() => setPerson(null)} />
    </div>
  );
};

export default LeaveCalendarPage;
