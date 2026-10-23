import { vi } from 'vitest';
import React from 'react';
import { render, screen, within } from '@testing-library/react';

import PunchList from './punch-list';
import { IPunchView, distanceText, punchReasonText, punchSourceText } from './attendance-api';

const base = (extra: Partial<IPunchView>): IPunchView => ({
  id: 1,
  kind: 'VAO',
  time: '08:10',
  source: 'APP',
  status: 'CHO_DUYET',
  ...extra,
});

describe('punchSourceText: người duyệt thấy khoảng cách, điểm chấm và IP', () => {
  it('cách <x m> · <điểm> và IP khi có', () => {
    expect(
      punchSourceText(base({ method: 'GPS', accuracyM: 140, distanceM: 210, siteName: 'Văn phòng HN', clientIp: '113.190.1.10' })),
    ).toBe('Tự chấm · GPS ±140 m · cách 210 m · Văn phòng HN · IP 113.190.1.10 · chờ duyệt');
  });

  it('từ 1000 m ghi km với dấu phẩy; không có điểm hoặc khoảng cách thì bỏ đoạn "cách"', () => {
    expect(distanceText(2400)).toBe('2,4 km');
    expect(distanceText(999)).toBe('999 m');
    expect(punchSourceText(base({ method: 'GPS', distanceM: 2400, siteName: 'ACME', status: 'TU_CHOI' }))).toBe(
      'Tự chấm · GPS · cách 2,4 km · ACME · bị từ chối',
    );
    expect(punchSourceText(base({ method: 'GPS', distanceM: 210 }))).toBe('Tự chấm · GPS · chờ duyệt');
  });

  it('chấm theo IP văn phòng ghi IP kèm địa chỉ khi được trả về; không bao giờ ghi tọa độ', () => {
    expect(punchSourceText(base({ method: 'IP', status: 'CHAP_NHAN', clientIp: '10.0.0.5', latitude: 21.02, longitude: 105.8 }))).toBe(
      'Tự chấm · IP 10.0.0.5',
    );
    expect(punchSourceText(base({ method: 'GPS', latitude: 21.02851, longitude: 105.8048 }))).not.toMatch(/21\.0|105\.8/);
  });
});

describe('punchReasonText', () => {
  it('lần bị lead từ chối hiện ghi chú của lead, không hiện lý do tự động', () => {
    expect(punchReasonText(base({ status: 'TU_CHOI', decisionNote: 'Không ở văn phòng', reviewReason: 'Sát ranh giới' }))).toBe(
      'Lý do: Không ở văn phòng',
    );
  });

  it('từ chối tự động (không ghi chú) giữ lý do hệ thống; chờ duyệt thì không có', () => {
    expect(punchReasonText(base({ status: 'TU_CHOI', reviewReason: 'Bạn đang cách VP 2,4 km' }))).toBe('Bạn đang cách VP 2,4 km');
    expect(punchReasonText(base({ status: 'CHO_DUYET', reviewReason: 'Sát ranh giới' }))).toBeNull();
  });

  it('lần đã duyệt có ghi chú cũng hiện', () => {
    expect(punchReasonText(base({ status: 'DA_DUYET', decisionNote: 'Đã xác nhận qua điện thoại' }))).toBe(
      'Lý do: Đã xác nhận qua điện thoại',
    );
  });
});

describe('PunchList', () => {
  it('dòng lần chấm: cách x m · điểm, IP và "Lý do" của người duyệt', () => {
    render(
      <PunchList
        canReview={false}
        approve={vi.fn()}
        reject={vi.fn()}
        punches={[
          base({
            id: 5,
            status: 'TU_CHOI',
            method: 'GPS',
            distanceM: 450,
            siteName: 'Văn phòng HN',
            clientIp: '1.2.3.4',
            reviewReason: 'Sát ranh giới',
            decisionNote: 'Đang ở quán cà phê',
          }),
          base({ id: 6, status: 'TU_CHOI', method: 'GPS', reviewReason: 'Bạn đang cách VP 2,4 km' }),
        ]}
      />,
    );
    const rejected = within(screen.getByTestId('punch-5'));
    expect(rejected.getByText(/cách 450 m · Văn phòng HN/)).toBeTruthy();
    expect(rejected.getByText(/IP 1\.2\.3\.4/)).toBeTruthy();
    expect(rejected.getByText('(Lý do: Đang ở quán cà phê)')).toBeTruthy();
    expect(rejected.queryByText(/Sát ranh giới/)).toBeNull();
    expect(within(screen.getByTestId('punch-6')).getByText('(Bạn đang cách VP 2,4 km)')).toBeTruthy();
  });
});
