import { useEffect, useSyncExternalStore } from 'react';
import axios from 'axios';

/** Thông tin công khai của công ty ({@code GET /api/public/branding}): menu, màn đăng nhập, favicon, tiêu đề tab. */
export interface IBranding {
  companyName: string;
  shortName: string;
  tagline: string;
  /** Phiên bản logo (epoch millis lần đổi logo); dùng làm tham số {@code ?v=} để trình duyệt tải lại ảnh khi đổi. */
  logoVersion: number;
  /** Đã nhận dữ liệu từ máy chủ (trước đó là giá trị mặc định, logo là file tĩnh). */
  loaded: boolean;
}

export const DEFAULT_SHORT_NAME = 'DOIT HRM';
export const DEFAULT_TAGLINE = 'Do it if you can';

const DEFAULT_BRANDING: IBranding = {
  companyName: '',
  shortName: DEFAULT_SHORT_NAME,
  tagline: DEFAULT_TAGLINE,
  logoVersion: 0,
  loaded: false,
};

export const logoUrl = (logoVersion: number) => `/api/public/branding/logo?v=${logoVersion}`;

// Kho dùng chung trong cả trang: tải một lần mỗi lần mở trang, mọi chỗ dùng cùng cập nhật khi làm mới.
let state: IBranding = DEFAULT_BRANDING;
let started = false;
let seq = 0;
const listeners = new Set<() => void>();

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

const getSnapshot = () => state;

const text = (v: unknown) => (typeof v === 'string' ? v.trim() : '');

/** Tải lại branding từ máy chủ (sau khi lưu tên hay đổi logo). Lỗi mạng thì giữ giá trị đang có. */
export const refreshBranding = async (): Promise<void> => {
  started = true;
  const mine = ++seq;
  try {
    const res = await axios.get<Partial<IBranding>>('api/public/branding');
    const d = res?.data;
    // Lần tải cũ về sau lần mới (vd làm mới ngay sau khi đổi logo) thì bỏ, không ghi đè dữ liệu mới.
    if (!d || mine !== seq) return;
    state = {
      companyName: text(d.companyName),
      shortName: text(d.shortName) || DEFAULT_SHORT_NAME,
      tagline: text(d.tagline),
      logoVersion: Number(d.logoVersion) || 0,
      loaded: true,
    };
    listeners.forEach(l => l());
  } catch {
    // Giữ mặc định: màn đăng nhập và menu vẫn dùng được khi chưa lấy được branding.
  }
};

/** Branding công ty, tải một lần mỗi lần mở trang; {@code refresh()} tải lại và cập nhật mọi chỗ đang hiển thị. */
export const useBranding = () => {
  const branding = useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
  useEffect(() => {
    if (!started) void refreshBranding();
  }, []);
  return { ...branding, refresh: refreshBranding };
};

/** Tiêu đề tab = tên viết tắt, favicon = logo hiện tại. Gọi từ gốc ứng dụng khi branding đã tải. */
export const applyBrandingToDocument = (b: Pick<IBranding, 'shortName' | 'logoVersion'>) => {
  document.title = b.shortName;
  const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
  if (icon) icon.setAttribute('href', logoUrl(b.logoVersion));
};

/** Chỉ cho test: xoá kho để mỗi test bắt đầu như mới mở trang. */
export const resetBrandingForTests = () => {
  state = DEFAULT_BRANDING;
  started = false;
  seq = 0;
};
