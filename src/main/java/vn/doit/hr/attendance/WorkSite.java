package vn.doit.hr.attendance;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Một điểm chấm công: văn phòng, trụ sở khách hàng, chi nhánh...
 *
 * @param radiusMeters bán kính cho phép quanh tâm (văn phòng trong tòa nhà thường 50–150 m)
 * @param wifiBssids địa chỉ MAC của các điểm phát WiFi tại đây (dạng aa:bb:cc:dd:ee:ff)
 * @param publicIps IP public của mạng tại đây (server tự thấy, điện thoại không giả được)
 */
public record WorkSite(String id, String name, GeoPoint center, int radiusMeters, Set<String> wifiBssids, Set<String> publicIps) {

    /** Bán kính mặc định do DOIT chọn (GPS trong ngõ phố thường lệch vài chục đến hơn trăm mét). */
    public static final int DEFAULT_RADIUS_METERS = 200;

    /** Điểm chấm công với bán kính mặc định {@value #DEFAULT_RADIUS_METERS} m. */
    public static WorkSite withDefaultRadius(String id, String name, GeoPoint center, Set<String> wifiBssids, Set<String> publicIps) {
        return new WorkSite(id, name, center, DEFAULT_RADIUS_METERS, wifiBssids, publicIps);
    }

    public WorkSite {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("Bán kính phải > 0");
        }
        wifiBssids = wifiBssids == null ? Set.of()
                : wifiBssids.stream().map(WorkSite::normalizeBssid).collect(Collectors.toUnmodifiableSet());
        publicIps = publicIps == null ? Set.of() : Set.copyOf(publicIps);
    }

    public boolean hasWifi(String bssid) {
        return bssid != null && wifiBssids.contains(normalizeBssid(bssid));
    }

    public boolean hasIp(String ip) {
        return ip != null && publicIps.contains(ip.trim());
    }

    static String normalizeBssid(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace('-', ':');
    }
}
