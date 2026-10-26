import type React from 'react';

/** Có nội dung để vẽ (bỏ qua undefined, null, false, chuỗi rỗng) — tránh lỡ in "0"/"false" ra giao diện. */
export const hasContent = (node: React.ReactNode): boolean => node !== undefined && node !== null && node !== false && node !== '';
