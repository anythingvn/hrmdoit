import React, { useEffect, useState } from 'react';
import axios from 'axios';

/** Mật khẩu mặc định đang cấu hình ở server (application.account.default-password); chỉ Quản trị và Nhân sự đọc được. */
export const DEFAULT_PASSWORD_URL = 'api/admin/account-settings/default-password';

/** Tải mật khẩu mặc định từ server (không viết cứng ở giao diện); null khi chưa tải được hoặc không có quyền. */
export const useDefaultPassword = (): string | null => {
  const [value, setValue] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const r = await axios.get<{ defaultPassword?: string }>(DEFAULT_PASSWORD_URL, { headers: { 'Cache-Control': 'no-store' } });
        if (alive) setValue(r?.data?.defaultPassword ?? null);
      } catch {
        if (alive) setValue(null);
      }
    };
    void load();
    return () => {
      alive = false;
    };
  }, []);
  return value;
};

/** Giá trị mật khẩu mặc định (in đậm), hoặc ghi chú khi không tải được. */
export const DefaultPasswordText = () => {
  const value = useDefaultPassword();
  return value ? <strong>{value}</strong> : <span>(xem cấu hình máy chủ)</span>;
};

export default DefaultPasswordText;
