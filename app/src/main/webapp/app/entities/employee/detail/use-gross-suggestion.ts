import { useEffect, useRef, useState } from 'react';
import axios from 'axios';

import { SalaryBasis } from './detail-api';

/** Phản hồi của GET api/salary-tools/gross-suggestion. */
interface IGrossSuggestion {
  gross: number | null;
  estimated?: boolean;
  /** yyyy-MM: tháng hiệu lực của bộ quy tắc đã dùng. */
  rulesFrom?: string;
  reason?: string;
}

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
/** @param insured false = không thuộc diện đóng BHXH bắt buộc: Gross gợi ý không cộng bảo hiểm. */
export const useGrossSuggestion = (initial: number | null, dependants: number, insured = true) => {
  const [gross, setGross] = useState<number | null>(initial);
  const [trigger, setTrigger] = useState<ITrigger | null>(null);
  /** Lý do server không gợi ý được (kỳ chưa có biểu thuế) → nhắc nhập Gross tay. */
  const [noRulesReason, setNoRulesReason] = useState<string | null>(null);
  /** Tháng hiệu lực (yyyy-MM) của bộ quy tắc mới nhất khi gợi ý chỉ là ước tính cho kỳ chưa có luật. */
  const [estimatedFrom, setEstimatedFrom] = useState<string | null>(null);
  const seqRef = useRef(0);
  const userTypedRef = useRef(false);

  useEffect(() => {
    if (!trigger || trigger.basis !== 'NET' || !trigger.amount) return undefined;
    const mine = trigger.n;
    const month = trigger.dateIso ? trigger.dateIso.slice(0, 7) : thisMonth();
    const t = setTimeout(() => {
      axios
        .get<IGrossSuggestion>(
          `api/salary-tools/gross-suggestion?net=${trigger.amount}&dependants=${dependants}&month=${month}${insured ? '' : '&insured=false'}`,
        )
        .then(r => {
          if (seqRef.current !== mine) return;
          const suggested = r.data?.gross ?? null;
          setNoRulesReason(suggested == null ? (r.data?.reason ?? 'Chưa có biểu thuế cho kỳ này') : null);
          setEstimatedFrom(suggested != null && r.data?.estimated ? (r.data.rulesFrom ?? month) : null);
          if (!userTypedRef.current) setGross(suggested);
        })
        .catch(() => undefined);
    }, 300);
    return () => clearTimeout(t);
  }, [trigger]);

  /** Gọi khi mức lương / cách tính / ngày đổi. */
  const inputsChanged = (amount: number | null, basis: SalaryBasis, dateIso: string | null) => {
    seqRef.current += 1;
    userTypedRef.current = false;
    setGross(null);
    setNoRulesReason(null);
    setEstimatedFrom(null);
    setTrigger({ n: seqRef.current, amount, basis, dateIso });
  };

  /** Người dùng tự gõ Gross. */
  const typeGross = (value: number | null) => {
    userTypedRef.current = true;
    setGross(value);
  };

  /** Gợi ý dưới ô Gross: lý do khi chưa có biểu thuế, ghi chú ước tính cho kỳ chưa có luật, ngược lại câu mặc định. */
  let hint = 'Tự điền theo gợi ý quy đổi Net → Gross, có thể sửa';
  if (noRulesReason) {
    hint = `${noRulesReason} — nhập Gross thủ công.`;
  } else if (estimatedFrom) {
    const [y, m] = estimatedFrom.split('-');
    hint = `Ước tính theo quy định mới nhất (tháng ${m}/${y})`;
  }

  return { gross, typeGross, inputsChanged, noRulesReason, estimatedFrom, hint };
};
