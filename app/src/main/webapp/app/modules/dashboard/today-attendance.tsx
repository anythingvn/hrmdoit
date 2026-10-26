import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { AutoKind, IDayBoard, attendanceApi } from 'app/modules/attendance/attendance-api';
import { SectionCard } from 'app/shared/ui';

/** Các loại nghỉ đã duyệt (có phép) tự điền lên bảng ngày. */
const LEAVE_KINDS: AutoKind[] = ['PHEP_NAM', 'NGHI_BU', 'VIEC_RIENG', 'OM', 'KHONG_LUONG'];

export interface ITodaySummary {
  total: number;
  checkedIn: number;
  late: number;
  onLeave: number;
  onTrip: number;
}

/** Đếm từ bảng gõ giờ của ngày: đã có giờ vào, vào muộn, nghỉ có phép (cả nửa ngày), công tác. */
export const summarize = (board: IDayBoard): ITodaySummary => {
  const rows = board.groups.flatMap(g => g.rows);
  return {
    total: rows.length,
    checkedIn: rows.filter(r => !!r.in).length,
    late: rows.filter(r => !!r.in && r.lateMinutes > 0).length,
    onLeave: rows.filter(r => !!r.autoKind && LEAVE_KINDS.includes(r.autoKind)).length,
    onTrip: rows.filter(r => r.autoKind === 'CONG_TAC').length,
  };
};

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * "Chấm công hôm nay" (thẻ tối): tính từ bảng gõ giờ ngày hôm nay ({@code GET api/attendance/day}), trong phạm vi người đang đăng nhập
 * được gõ giờ. Chỉ hiện với người có quyền gõ giờ.
 */
export const TodayAttendance = () => {
  const [sum, setSum] = useState<ITodaySummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    attendanceApi
      .day(todayIso())
      .then(b => setSum(summarize(b)))
      .catch(err => setError(errorText(err, 'Không tải được chấm công hôm nay')));
  }, []);

  const onTime = sum && sum.checkedIn > 0 ? Math.round(((sum.checkedIn - sum.late) / sum.checkedIn) * 100) : null;

  return (
    <SectionCard dark title="Chấm công hôm nay" titleId="today-att-title" actions={<Link to="/cham-cong">Chi tiết</Link>}>
      {error && <p className="mb-0">{error}</p>}
      {!sum && !error && <p className="mb-0">Đang tải...</p>}
      {sum && (
        <>
          <dl className="today-att-grid">
            <div>
              <dt>Đã check-in</dt>
              <dd>{sum.checkedIn}</dd>
            </div>
            <div>
              <dt>Đi muộn</dt>
              <dd>{sum.late}</dd>
            </div>
            <div>
              <dt>Nghỉ có phép</dt>
              <dd>{sum.onLeave}</dd>
            </div>
            <div>
              <dt>Đi công tác</dt>
              <dd>{sum.onTrip}</dd>
            </div>
          </dl>
          {onTime !== null && (
            <div className="today-att-rate">
              <div className="d-flex justify-content-between">
                <span>Tỷ lệ đúng giờ</span>
                <strong>{onTime}%</strong>
              </div>
              <div
                className="today-att-bar"
                role="progressbar"
                aria-label="Tỷ lệ đúng giờ"
                aria-valuenow={onTime}
                aria-valuemin={0}
                aria-valuemax={100}
              >
                <span style={{ width: `${onTime}%` }} />
              </div>
            </div>
          )}
          <span className="today-att-note">Trên {sum.total} người trong bảng chấm công của bạn hôm nay.</span>
        </>
      )}
    </SectionCard>
  );
};

export default TodayAttendance;
