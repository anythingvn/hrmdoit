import axios from 'axios';

/** Dung lượng tối đa mỗi file tải lên (khớp spring.servlet.multipart.max-file-size). */
export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

/**
 * Tải file từ API (có token) về máy với tên cho trước. Lỗi nghiệp vụ (400 kèm JSON) được đọc lại từ blob vào `response.data` để
 * `errorText` hiện đúng câu lỗi.
 */
export const downloadFile = async (url: string, fileName: string, params?: Record<string, unknown>) => {
  let res;
  try {
    res = await axios.get<Blob>(url, { responseType: 'blob', params });
  } catch (err) {
    await readJsonError(err);
    throw err;
  }
  const href = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(href);
};

const readJsonError = async (err: any) => {
  const data = err?.response?.data;
  if (typeof Blob === 'undefined' || !(data instanceof Blob) || !data.type.includes('json')) return;
  try {
    err.response.data = JSON.parse(await data.text());
  } catch {
    // Không đọc được: giữ nguyên, người gọi hiện câu lỗi chung.
  }
};
