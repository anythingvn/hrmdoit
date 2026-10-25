import React from 'react';

import { isoToVn } from 'app/entities/employee/vn-date';
import { ChipTone, StatusChip as SharedChip } from 'app/shared/ui';

import { ILeaveRequest } from './leave-api';
import { PART_LABEL, statusTone } from './leave-labels';

const CHIP_TONE: Record<ReturnType<typeof statusTone>, ChipTone> = { ok: 'ok', wait: 'wait', no: 'warn', old: 'muted' };

/** Nhãn trạng thái đơn (màu theo canvas). */
export const StatusChip = ({ status, label }: { status: string; label: string }) => {
  const tone = statusTone(status);
  return (
    <SharedChip tone={CHIP_TONE[tone]} className={`leave-status tone-${tone}`}>
      {label}
    </SharedChip>
  );
};

/** "02/11/2026 – 03/11/2026 (chiều ngày đầu, sáng ngày cuối)". */
export const rangeText = (r: Pick<ILeaveRequest, 'fromDate' | 'toDate' | 'firstPart' | 'lastPart'>) => {
  if (r.fromDate === r.toDate) {
    const part = r.firstPart && r.firstPart !== 'CA_NGAY' ? ` (${PART_LABEL[r.firstPart].toLowerCase()})` : '';
    return `${isoToVn(r.fromDate)}${part}`;
  }
  const notes = [r.firstPart === 'CHIEU' ? 'chiều ngày đầu' : null, r.lastPart === 'SANG' ? 'sáng ngày cuối' : null].filter(Boolean);
  return `${isoToVn(r.fromDate)} – ${isoToVn(r.toDate)}${notes.length ? ` (${notes.join(', ')})` : ''}`;
};
