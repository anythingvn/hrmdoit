import { useCallback, useEffect, useState } from 'react';

import { errorText } from 'app/entities/employee/detail/detail-api';

import { IDayView, IExplanation, IMonthBoard, MonthStatus, attendanceApi, isRedCell } from './attendance-api';

/**
 * Dữ liệu tháng của chính mình, dùng chung cho "Bảng công của tôi" và khung Lịch sử ở màn chấm công: bảng tháng
 * ({@code GET /api/attendance/my}) và các giải trình đã gửi trong tháng. Đổi {@code refresh} để tải lại.
 */
export const useMyMonth = (month: string, refresh = 0) => {
  const [board, setBoard] = useState<IMonthBoard | null>(null);
  const [explanations, setExplanations] = useState<IExplanation[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(() => {
    setError(null);
    attendanceApi
      .my(month)
      .then(setBoard)
      .catch(err => {
        setBoard(null);
        setError(errorText(err, 'Không tải được bảng công'));
      });
    attendanceApi
      .myExplanations(month)
      .then(list => setExplanations(Array.isArray(list) ? list : []))
      .catch(() => setExplanations([]));
  }, [month, refresh]);

  useEffect(reload, [reload]);

  return { board, explanations, error, reload };
};

/** Các ngày (yyyy-MM-dd) đang có giải trình chờ duyệt. */
export const waitingDays = (explanations: IExplanation[]) => new Set(explanations.filter(e => e.status === 'DANG_CHO').map(e => e.workDay));

/** Ngày đang chờ duyệt: có lần tự chấm chờ duyệt hoặc giải trình đang chờ. */
export const isPendingDay = (v: IDayView, waiting: Set<string>) => (v.pendingCount ?? 0) > 0 || waiting.has(v.day);

/**
 * Có nút Giải trình cho ngày {@code v}: tháng đang mở, ngày đã tới, chưa có giải trình đang chờ, và ngày đó thiếu giờ / chưa nhập hoặc
 * có lần chấm bị từ chối hay đang chờ duyệt. Tháng đã chốt, hoặc ngày đủ công mà không có lần chấm nào bị từ chối / chờ duyệt: không có.
 */
export const canExplainDay = (status: MonthStatus | undefined, v: IDayView, waiting: Set<string>, today: string) =>
  status === 'DANG_MO' &&
  v.day <= today &&
  !waiting.has(v.day) &&
  (isRedCell(v) || (v.punches ?? []).some(p => p.status === 'TU_CHOI' || p.status === 'CHO_DUYET'));
