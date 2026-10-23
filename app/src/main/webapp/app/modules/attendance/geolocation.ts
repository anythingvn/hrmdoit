export interface IPosition {
  latitude: number;
  longitude: number;
  accuracy: number;
}

/**
 * Lấy vị trí một lần (không theo dõi nền). Bị từ chối, lỗi, hết giờ hoặc trình duyệt không hỗ trợ thì trả {@code null}: lần chấm vẫn gửi,
 * không kèm tọa độ. {@code timeout} của trình duyệt (10 giây) không tính lúc người dùng còn đang trả lời hộp xin quyền; bộ đếm ngoài
 * {@code safetyMs} (30 giây) chỉ để không treo mãi khi trình duyệt không bao giờ trả lời.
 */
export const getPosition = (safetyMs = 30000): Promise<IPosition | null> =>
  new Promise(resolve => {
    const geo = typeof navigator === 'undefined' ? undefined : navigator.geolocation;
    if (!geo) {
      resolve(null);
      return;
    }
    const timer = setTimeout(() => resolve(null), safetyMs);
    const done = (p: IPosition | null) => {
      clearTimeout(timer);
      resolve(p);
    };
    try {
      geo.getCurrentPosition(
        pos => done({ latitude: pos.coords.latitude, longitude: pos.coords.longitude, accuracy: pos.coords.accuracy }),
        () => done(null),
        { timeout: 10000, maximumAge: 0, enableHighAccuracy: true },
      );
    } catch {
      done(null);
    }
  });
