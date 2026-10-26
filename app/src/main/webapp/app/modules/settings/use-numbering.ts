import { useEffect, useState } from 'react';

import { INumbering, INumberingValues, settingsApi } from './settings-api';

/** Giá trị mặc định cài sẵn, dùng khi chưa tải được cấu hình (máy chủ vẫn là nơi quyết định mã thật). */
export const FALLBACK_NUMBERING = {
  documentPrefix: 'DOIT',
  staffCodePrefix: 'DOIT',
  internCodePrefix: 'TT',
  serviceCodePrefix: 'HDDV',
};

let cache: INumbering | null = null;
let inflight: Promise<void> | null = null;
const listeners = new Set<(v: INumbering | null) => void>();

/** Ghi cấu hình mới vào bộ nhớ chung (sau khi lưu) để mọi câu gợi ý đổi theo ngay. */
export const setNumberingCache = (value: INumbering | null) => {
  cache = value;
  listeners.forEach(l => l(value));
};

export const resetNumberingCache = () => {
  inflight = null;
  setNumberingCache(null);
};

const load = () => {
  inflight ??= Promise.resolve()
    .then(() => settingsApi.numbering())
    .then(v => {
      if (v && typeof v === 'object' && !Array.isArray(v)) setNumberingCache(v);
    })
    .catch(() => undefined)
    .finally(() => {
      inflight = null;
    });
  return inflight;
};

/** Cấu hình mã và số văn bản hiệu lực (tải một lần, dùng chung); null khi chưa tải được hoặc không có quyền. */
export const useNumbering = (): INumbering | null => {
  const [value, setValue] = useState<INumbering | null>(cache);
  useEffect(() => {
    listeners.add(setValue);
    if (!cache) void load();
    else setValue(cache);
    return () => {
      listeners.delete(setValue);
    };
  }, []);
  return value;
};

/** Tiền tố hiệu lực kèm giá trị cài sẵn khi chưa tải được. */
export const useEffectivePrefixes = (): Pick<
  INumberingValues,
  'documentPrefix' | 'staffCodePrefix' | 'internCodePrefix' | 'serviceCodePrefix'
> => {
  const n = useNumbering();
  return {
    documentPrefix: n?.documentPrefix || FALLBACK_NUMBERING.documentPrefix,
    staffCodePrefix: n?.staffCodePrefix || FALLBACK_NUMBERING.staffCodePrefix,
    internCodePrefix: n?.internCodePrefix || FALLBACK_NUMBERING.internCodePrefix,
    serviceCodePrefix: n?.serviceCodePrefix || FALLBACK_NUMBERING.serviceCodePrefix,
  };
};
