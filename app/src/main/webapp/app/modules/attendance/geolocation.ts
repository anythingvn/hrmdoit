export interface IPosition {
  latitude: number;
  longitude: number;
  accuracy: number;
}

/**
 * Lấy vị trí một lần (không theo dõi nền). Bị từ chối, lỗi, hết {@code timeoutMs} (mặc định 10 giây) hoặc trình duyệt không hỗ trợ thì
 * trả {@code null}: lần chấm vẫn gửi, không kèm tọa độ.
 */
export const getPosition = (timeoutMs = 10000): Promise<IPosition | null> =>
  new Promise(resolve => {
    const geo = typeof navigator === 'undefined' ? undefined : navigator.geolocation;
    if (!geo) {
      resolve(null);
      return;
    }
    const timer = setTimeout(() => resolve(null), timeoutMs);
    const done = (p: IPosition | null) => {
      clearTimeout(timer);
      resolve(p);
    };
    try {
      geo.getCurrentPosition(
        pos => done({ latitude: pos.coords.latitude, longitude: pos.coords.longitude, accuracy: pos.coords.accuracy }),
        () => done(null),
        { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 0 },
      );
    } catch {
      done(null);
    }
  });
