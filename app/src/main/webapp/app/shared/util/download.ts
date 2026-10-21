import axios from 'axios';

/** Dung lượng tối đa mỗi file tải lên (khớp spring.servlet.multipart.max-file-size). */
export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

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
