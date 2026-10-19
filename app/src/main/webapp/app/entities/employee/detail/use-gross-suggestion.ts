import { useEffect, useRef, useState } from 'react';
import axios from 'axios';

import { SalaryBasis } from './detail-api';

interface ITrigger {
  n: number;
  amount: number | null;
  basis: SalaryBasis;
  dateIso: string | null;
}

const thisMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
};

/**
 * Ô "Lương Gross (cho kế toán)". Khi người dùng đổi mức lương, cách tính hoặc ngày hiệu lực: xóa Gross và hỏi lại gợi ý
 * (chỉ khi lương NET). Gross người dùng tự gõ sau lần gợi ý gần nhất được giữ, gợi ý về muộn không ghi đè.
 */
export const useGrossSuggestion = (initial: number | null, dependants: number) => {
  const [gross, setGross] = useState<number | null>(initial);
  const [trigger, setTrigger] = useState<ITrigger | null>(null);
  const seq = useRef(0);
  const userTyped = useRef(false);

  useEffect(() => {
    if (!trigger || trigger.basis !== 'NET' || !trigger.amount) return undefined;
    const mine = trigger.n;
    const month = trigger.dateIso ? trigger.dateIso.slice(0, 7) : thisMonth();
    const t = setTimeout(() => {
      axios
        .get<{ gross: number | null }>(`api/salary-tools/gross-suggestion?net=${trigger.amount}&dependants=${dependants}&month=${month}`)
        .then(r => {
          if (seq.current === mine && !userTyped.current) setGross(r.data?.gross ?? null);
        })
        .catch(() => undefined);
    }, 300);
    return () => clearTimeout(t);
  }, [trigger]);

  /** Gọi khi mức lương / cách tính / ngày đổi. */
  const inputsChanged = (amount: number | null, basis: SalaryBasis, dateIso: string | null) => {
    seq.current += 1;
    userTyped.current = false;
    setGross(null);
    setTrigger({ n: seq.current, amount, basis, dateIso });
  };

  /** Người dùng tự gõ Gross. */
  const typeGross = (value: number | null) => {
    userTyped.current = true;
    setGross(value);
  };

  return { gross, typeGross, inputsChanged };
};
