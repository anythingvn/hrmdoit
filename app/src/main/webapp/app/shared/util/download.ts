import axios from 'axios';

/** Tải file từ API (có token) về máy với tên cho trước. */
export const downloadFile = async (url: string, fileName: string, params?: Record<string, unknown>) => {
  const res = await axios.get<Blob>(url, { responseType: 'blob', params });
  const href = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(href);
};
